package dev.shirwac.incidentdetective.nordly;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GeminiCustomerChatAnswerGatewayTest {

    private static final String ORDER_EVIDENCE =
            "nordly-demo-order-2051-snapshot";

    private final GoogleGenAiClientFactory clientFactory = mock(
            GoogleGenAiClientFactory.class
    );
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void decodesANaturalAnswerWithBoundedEvidenceAndProviderMetadata() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response(validAnswer(), FinishReason.Known.STOP),
                answeredInput(),
                87
        );

        assertTrue(result.answer().textSv().startsWith("Hej!"));
        assertEquals(1, result.answer().claims().size());
        assertEquals(
                List.of(ORDER_EVIDENCE),
                result.answer().claims().getFirst().citationIds()
        );
        assertEquals("developer_api", result.provider().route().transport());
        assertEquals("api_key", result.provider().route().authenticationMode());
        assertEquals("gemini-3.1-flash-lite", result.provider().modelVersion());
        assertEquals(87, result.provider().latencyMs());
        assertNull(result.provider().tokenUsage());
    }

    @Test
    void allowsAClarificationWithoutClaimsWhenNoEvidenceWasUsed() {
        CustomerChatAnswerGateway.Input input = new CustomerChatAnswerGateway.Input(
                "Vad menar du?",
                "sv",
                "clarification_required",
                "clarification_required",
                List.of()
        );

        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Menar du leveransstatus eller returregler?",
                          "text_en": "Do you mean delivery status or return rules?",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                input,
                12
        );

        assertTrue(result.answer().claims().isEmpty());
    }

    @Test
    void allowsAnAnsweredConversationWithoutEvidenceClaims() {
        CustomerChatAnswerGateway.Input input = new CustomerChatAnswerGateway.Input(
                "Hej!",
                "sv",
                "conversation",
                "answered",
                List.of(evidence())
        );

        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Vad vill du ha hjälp med idag?",
                          "text_en": "Hi! What would you like help with today?",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                input,
                9
        );

        assertEquals("Hej! Vad vill du ha hjälp med idag?",
                result.answer().textSv());
        assertTrue(result.answer().claims().isEmpty());
        assertEquals(9, result.provider().latencyMs());
    }

    @Test
    void rejectsACitationOutsideTheBoundedEvidenceList() {
        assertMalformed("""
                {
                  "text_sv": "Hej! Din order är skickad.",
                  "text_en": "Hi! Your order has shipped.",
                  "claims": [{
                    "text_sv": "Ordern är skickad.",
                    "text_en": "The order has shipped.",
                    "citation_ids": ["unknown-evidence"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void rejectsAnAnsweredOutcomeWithoutEvidenceClaims() {
        assertMalformed("""
                {
                  "text_sv": "Hej! Din order är skickad.",
                  "text_en": "Hi! Your order has shipped.",
                  "claims": []
                }
                """, answeredInput());
    }

    @Test
    void rejectsAClaimThatTheAssistantPerformedABusinessAction() {
        assertMalformed("""
                {
                  "text_sv": "Jag har återbetalat ordern.",
                  "text_en": "I have refunded the order.",
                  "claims": [{
                    "text_sv": "Jag har återbetalat ordern.",
                    "text_en": "I have refunded the order.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void doesNotConfuseAReadOnlyShippingStatusWithAnExecutedAction() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Ordern är skickad.",
                          "text_en": "Hi! The order has shipped.",
                          "claims": [{
                            "text_sv": "Ordern är skickad.",
                            "text_en": "The order has shipped.",
                            "citation_ids": ["nordly-demo-order-2051-snapshot"]
                          }]
                        }
                        """, FinishReason.Known.STOP),
                answeredInput(),
                1
        );

        assertEquals("Hej! Ordern är skickad.", result.answer().textSv());
    }

    @Test
    void rejectsMissingAndTruncatedProviderResponses() {
        GeminiCustomerChatAnswerGateway gateway = gateway(properties(
                "test-only-key"
        ));

        ModelProviderException missing = assertThrows(
                ModelProviderException.class,
                () -> gateway.decodeResponse(
                        GenerateContentResponse.builder().build(),
                        answeredInput(),
                        1
                )
        );
        ModelProviderException truncated = assertThrows(
                ModelProviderException.class,
                () -> gateway.decodeResponse(
                        response(validAnswer(), FinishReason.Known.MAX_TOKENS),
                        answeredInput(),
                        1
                )
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, missing.failure());
        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                truncated.failure()
        );
    }

    @Test
    void missingProviderConfigurationStopsBeforeClientCreation() {
        GeminiCustomerChatAnswerGateway gateway = gateway(properties(null));

        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway.generate(answeredInput())
        );

        assertEquals(ModelProviderFailure.UPSTREAM, exception.failure());
        verifyNoInteractions(clientFactory);
    }

    @Test
    void inputRejectsDuplicateEvidenceIdsAndUnboundedLists() {
        CustomerChatAnswerGateway.Evidence evidence = evidence();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatAnswerGateway.Input(
                        "Var är min order?",
                        "sv",
                        "order_status",
                        "answered",
                        List.of(evidence, evidence)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatAnswerGateway.Input(
                        "Var är min order?",
                        "sv",
                        "order_status",
                        "answered",
                        java.util.stream.IntStream.rangeClosed(
                                        1,
                                        CustomerChatAnswerGateway.MAX_EVIDENCE_ITEMS + 1
                                )
                                .mapToObj(index -> new CustomerChatAnswerGateway.Evidence(
                                        "evidence-" + index,
                                        "Order",
                                        "Order evidence"
                                ))
                                .toList()
                )
        );
    }

    private void assertMalformed(
            String body,
            CustomerChatAnswerGateway.Input input
    ) {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway(properties("test-only-key")).decodeResponse(
                        response(body, FinishReason.Known.STOP),
                        input,
                        1
                )
        );

        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                exception.failure()
        );
    }

    private CustomerChatAnswerGateway.Input answeredInput() {
        return new CustomerChatAnswerGateway.Input(
                "Var är min order?",
                "sv",
                "order_status",
                "answered",
                List.of(evidence())
        );
    }

    private CustomerChatAnswerGateway.Evidence evidence() {
        return new CustomerChatAnswerGateway.Evidence(
                ORDER_EVIDENCE,
                "Aktuell order",
                "Order NORD-2051 är skickad."
        );
    }

    private String validAnswer() {
        return """
                {
                  "text_sv": "Hej! Din order NORD-2051 är skickad.",
                  "text_en": "Hi! Your order NORD-2051 has shipped.",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """;
    }

    private GenerateContentResponse response(
            String text,
            FinishReason.Known finishReason
    ) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(text))
                                .build())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private GeminiCustomerChatAnswerGateway gateway(
            GeminiAiProperties properties
    ) {
        return new GeminiCustomerChatAnswerGateway(
                properties,
                clientFactory,
                jsonMapper
        );
    }

    private GeminiAiProperties properties(String apiKey) {
        return new GeminiAiProperties(
                apiKey,
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.DEVELOPER_API,
                null,
                null
        );
    }
}
