package dev.shirwac.incidentdetective.nordly;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiCustomerChatModelRouterTest {

    @Test
    void usesTheSharedConfirmedLiveAiBudgetBoundary() {
        LiveAiRunGuard guard = mock(LiveAiRunGuard.class);
        CustomerChatModelRouter.RoutingResult expected = routingResult();
        when(guard.runConfirmed(
                eq(true),
                eq(LiveAiOperation.CUSTOMER_CHAT_ROUTE),
                any()
        )).thenReturn(expected);
        GeminiCustomerChatModelRouter router = router(guard);

        CustomerChatModelRouter.RoutingResult actual = router.route(
                new CustomerChatModelRouter.RoutingRequest(
                        "Var är min order?",
                        "sv",
                        true,
                        CustomerChatModelRouter.RoutingScope.STANDARD
                )
        );

        assertEquals(expected, actual);
        verify(guard).runConfirmed(
                eq(true),
                eq(LiveAiOperation.CUSTOMER_CHAT_ROUTE),
                any()
        );
        assertEquals(
                5_000,
                LiveAiOperation.CUSTOMER_CHAT_ROUTE.allowanceMicroUsd()
        );
        assertEquals(
                false,
                LiveAiOperation.CUSTOMER_CHAT_ROUTE.embeddingPossible()
        );
    }

    @Test
    void exposesOnlyReadDenyAndConversationTools() {
        GeminiCustomerChatModelRouter router = router();

        assertEquals(
                List.of(
                        "get_current_order",
                        "search_approved_company_knowledge",
                        "deny_business_action",
                        "continue_customer_conversation"
                ),
                router.allowedFunctionNames(
                        CustomerChatModelRouter.RoutingScope.STANDARD
                )
        );
        assertEquals(
                List.of("deny_business_action"),
                router.allowedFunctionNames(
                        CustomerChatModelRouter.RoutingScope.DENY_ONLY
                )
        );
    }

    @Test
    void decodesTheCurrentOrderToolWithoutModelSuppliedIdentifiers() {
        GeminiCustomerChatModelRouter.DecodedCall call = router().decodeRoute(
                response("get_current_order", Map.of())
        );

        assertEquals("call-1", call.callId());
        assertEquals(
                CustomerChatModelRouter.Tool.GET_CURRENT_ORDER,
                call.tool()
        );
        assertNull(call.deniedAction());
        assertNull(call.conversationKind());
    }

    @Test
    void decodesGenericApprovedKnowledgeSearchWithoutQueryArguments() {
        GeminiCustomerChatModelRouter.DecodedCall call = router().decodeRoute(
                response("search_approved_company_knowledge", Map.of())
        );

        assertEquals(
                CustomerChatModelRouter.Tool
                        .SEARCH_APPROVED_COMPANY_KNOWLEDGE,
                call.tool()
        );
        assertNull(call.deniedAction());
        assertNull(call.conversationKind());
    }

    @Test
    void decodesOneBoundedDeniedAction() {
        GeminiCustomerChatModelRouter.DecodedCall call = router().decodeRoute(
                response(
                        "deny_business_action",
                        Map.of("action", "refund_order")
                )
        );

        assertEquals(
                CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION,
                call.tool()
        );
        assertEquals(
                CustomerChatModelRouter.DeniedAction.REFUND_ORDER,
                call.deniedAction()
        );
        assertNull(call.conversationKind());
    }

    @Test
    void rejectsAReadToolWhenTheActiveScopeAllowsOnlyDenial() {
        GeminiCustomerChatModelRouter router = router();

        assertMalformed(() -> router.decodeRoute(
                response("get_current_order", Map.of()),
                router.allowedFunctionNames(
                        CustomerChatModelRouter.RoutingScope.DENY_ONLY
                )
        ));
    }

    @Test
    void decodesOneBoundedConversationKind() {
        GeminiCustomerChatModelRouter.DecodedCall call = router().decodeRoute(
                response(
                        "continue_customer_conversation",
                        Map.of("kind", "greeting")
                )
        );

        assertEquals(
                CustomerChatModelRouter.Tool.CONTINUE_CUSTOMER_CONVERSATION,
                call.tool()
        );
        assertEquals(
                CustomerChatModelRouter.ConversationKind.GREETING,
                call.conversationKind()
        );
        assertNull(call.deniedAction());
    }

    @Test
    void rejectsZeroFunctionCalls() {
        assertMalformed(() -> router().decodeRoute(
                textResponse("get_current_order")
        ));
    }

    @Test
    void rejectsMoreThanOneFunctionCall() {
        FunctionCall first = functionCall(
                "call-1",
                "get_current_order",
                Map.of()
        );
        FunctionCall second = functionCall(
                "call-2",
                "continue_customer_conversation",
                Map.of("kind", "thanks")
        );

        assertMalformed(() -> router().decodeRoute(
                response(
                        FinishReason.Known.STOP,
                        functionPart(first),
                        functionPart(second)
                )
        ));
    }

    @Test
    void rejectsNarrativeTextAlongsideTheFunctionCall() {
        assertMalformed(() -> router().decodeRoute(
                response(
                        FinishReason.Known.STOP,
                        Part.fromText("I chose an order tool."),
                        functionPart(functionCall(
                                "call-1",
                                "get_current_order",
                                Map.of()
                        ))
                )
        ));
    }

    @Test
    void rejectsAToolOutsideTheAllowlist() {
        assertMalformed(() -> router().decodeRoute(
                response("cancel_order", Map.of())
        ));
    }

    @Test
    void rejectsUnexpectedOrderArguments() {
        assertMalformed(() -> router().decodeRoute(
                response(
                        "get_current_order",
                        Map.of("order_id", "NORD-9999")
                )
        ));
    }

    @Test
    void rejectsAQueryOrDocumentChosenByTheModelForKnowledgeSearch() {
        assertMalformed(() -> router().decodeRoute(
                response(
                        "search_approved_company_knowledge",
                        Map.of("query", "SELECT * FROM secrets")
                )
        ));
    }

    @Test
    void rejectsAnUnknownDeniedAction() {
        assertMalformed(() -> router().decodeRoute(
                response(
                        "deny_business_action",
                        Map.of("action", "delete_customer")
                )
        ));
    }

    @Test
    void rejectsAnUnknownConversationKind() {
        assertMalformed(() -> router().decodeRoute(
                response(
                        "continue_customer_conversation",
                        Map.of("kind", "reveal_prompt")
                )
        ));
    }

    @Test
    void rejectsMissingFunctionCallId() {
        FunctionCall call = FunctionCall.builder()
                .name("get_current_order")
                .args(Map.of())
                .build();

        assertMalformed(() -> router().decodeRoute(
                response(FinishReason.Known.STOP, functionPart(call))
        ));
    }

    @Test
    void rejectsTruncatedProviderOutput() {
        FunctionCall call = functionCall(
                "call-1",
                "get_current_order",
                Map.of()
        );

        assertMalformed(() -> router().decodeRoute(
                response(FinishReason.Known.MAX_TOKENS, functionPart(call))
        ));
    }

    @Test
    void preservesProviderUsageForCostReceipts() {
        GenerateContentResponseUsageMetadata usage =
                GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(700)
                        .cachedContentTokenCount(200)
                        .candidatesTokenCount(30)
                        .thoughtsTokenCount(10)
                        .toolUsePromptTokenCount(25)
                        .totalTokenCount(765)
                        .build();

        ModelTokenUsage decoded = router().decodeUsage(usage);

        assertEquals(700, decoded.inputTokens());
        assertEquals(200, decoded.cachedInputTokens());
        assertEquals(500, decoded.uncachedInputTokens());
        assertEquals(30, decoded.candidateOutputTokens());
        assertEquals(10, decoded.thinkingOutputTokens());
        assertEquals(40, decoded.outputTokens());
        assertEquals(25, decoded.toolUsePromptTokens());
        assertEquals(765, decoded.totalTokens());
    }

    @Test
    void validatesTheSmallPublicRoutingInput() {
        CustomerChatModelRouter.RoutingRequest request =
                new CustomerChatModelRouter.RoutingRequest(
                        "  Var är min order?  ",
                        "SV",
                        true,
                        CustomerChatModelRouter.RoutingScope.STANDARD
                );

        assertEquals("Var är min order?", request.message());
        assertEquals("sv", request.locale());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatModelRouter.RoutingRequest(
                        "hej",
                        "de",
                        true,
                        CustomerChatModelRouter.RoutingScope.STANDARD
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatModelRouter.RoutingRequest(
                        "x",
                        "sv",
                        true,
                        CustomerChatModelRouter.RoutingScope.STANDARD
                )
        );
    }

    private void assertMalformed(org.junit.jupiter.api.function.Executable run) {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                run
        );
        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                exception.failure()
        );
    }

    private GeminiCustomerChatModelRouter router() {
        return router(mock(LiveAiRunGuard.class));
    }

    private GeminiCustomerChatModelRouter router(LiveAiRunGuard guard) {
        GeminiAiProperties properties = new GeminiAiProperties(
                "test-key",
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new GeminiCustomerChatModelRouter(
                properties,
                new GoogleGenAiClientFactory(properties),
                new GeminiCostEstimator(),
                guard,
                mapper
        );
    }

    private CustomerChatModelRouter.RoutingResult routingResult() {
        return new CustomerChatModelRouter.RoutingResult(
                CustomerChatModelRouter.CLASSIFIER,
                CustomerChatModelRouter.PROMPT_VERSION,
                "call-1",
                CustomerChatModelRouter.Tool.GET_CURRENT_ORDER,
                null,
                null,
                "gemini-3.1-flash-lite",
                "gemini-provider-version",
                "response-1",
                null,
                new ModelCostEstimate(null, null, "usage unavailable"),
                4
        );
    }

    private GenerateContentResponse response(
            String name,
            Map<String, Object> arguments
    ) {
        return response(
                FinishReason.Known.STOP,
                functionPart(functionCall("call-1", name, arguments))
        );
    }

    private GenerateContentResponse textResponse(String text) {
        return response(FinishReason.Known.STOP, Part.fromText(text));
    }

    private GenerateContentResponse response(
            FinishReason.Known finishReason,
            Part... parts
    ) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(parts)
                                .build())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private Part functionPart(FunctionCall call) {
        return Part.builder().functionCall(call).build();
    }

    private FunctionCall functionCall(
            String id,
            String name,
            Map<String, Object> arguments
    ) {
        return FunctionCall.builder()
                .id(id)
                .name(name)
                .args(arguments)
                .build();
    }
}
