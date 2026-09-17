package dev.shirwac.incidentdetective.nordly;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.ToolConfig;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gemini function-calling router with no automatic tool execution. */
@Component
@Profile("rag")
public final class GeminiCustomerChatModelRouter
        implements CustomerChatModelRouter {

    static final int TIMEOUT_MS = 10_000;
    private static final String PROMPT_RESOURCE =
            "ai/prompts/route-nordly-customer-chat-v1.txt";
    private static final Map<CustomerChatModelRouter.Tool, String>
            SCHEMA_RESOURCES = Map.of(
            CustomerChatModelRouter.Tool.GET_CURRENT_ORDER,
            "ai/tool-schemas/customer-get-current-order-v1.json",
            CustomerChatModelRouter.Tool.SEARCH_APPROVED_COMPANY_KNOWLEDGE,
            "ai/tool-schemas/customer-search-company-knowledge-v1.json",
            CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION,
            "ai/tool-schemas/customer-deny-action-v1.json",
            CustomerChatModelRouter.Tool.CONTINUE_CUSTOMER_CONVERSATION,
            "ai/tool-schemas/customer-continue-conversation-v1.json"
    );

    private final GeminiAiProperties properties;
    private final GoogleGenAiClientFactory clientFactory;
    private final GeminiCostEstimator costEstimator;
    private final LiveAiRunGuard liveAiRunGuard;
    private final JsonMapper jsonMapper;
    private final String instructions;
    private final List<FunctionDeclaration> declarations;
    private volatile Client client;

    public GeminiCustomerChatModelRouter(
            GeminiAiProperties properties,
            GoogleGenAiClientFactory clientFactory,
            GeminiCostEstimator costEstimator,
            LiveAiRunGuard liveAiRunGuard,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.costEstimator = costEstimator;
        this.liveAiRunGuard = liveAiRunGuard;
        this.jsonMapper = jsonMapper;
        instructions = loadText(PROMPT_RESOURCE);
        declarations = List.of(
                declaration(
                        CustomerChatModelRouter.Tool.GET_CURRENT_ORDER,
                        "Select this for the current customer's order status, delivery estimate, shipment progress, or item-count question."
                ),
                declaration(
                        CustomerChatModelRouter.Tool.SEARCH_APPROVED_COMPANY_KNOWLEDGE,
                        "Select this for an informational question that requires approved Nordly company knowledge rather than the current order snapshot."
                ),
                declaration(
                        CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION,
                        "Select this when the customer asks the assistant to cancel, return, refund, purchase, add, or change business data."
                ),
                declaration(
                        CustomerChatModelRouter.Tool.CONTINUE_CUSTOMER_CONVERSATION,
                        "Select this for a greeting, thanks, unclear request, or request outside the bounded customer-support scope."
                )
        );
    }

    @Override
    public RoutingResult route(RoutingRequest request) {
        return liveAiRunGuard.runConfirmed(
                request.confirmLiveAi(),
                LiveAiOperation.CUSTOMER_CHAT_ROUTE,
                () -> routeAdmitted(request)
        );
    }

    private RoutingResult routeAdmitted(RoutingRequest request) {
        List<String> allowedNames = allowedFunctionNames(request.scope());
        List<FunctionDeclaration> allowedDeclarations = declarations.stream()
                .filter(declaration -> allowedNames.contains(
                        declaration.name().orElseThrow()
                ))
                .toList();
        com.google.genai.types.Tool tool =
                com.google.genai.types.Tool.builder()
                .functionDeclarations(allowedDeclarations)
                .build();
        ToolConfig toolConfig = ToolConfig.builder()
                .functionCallingConfig(FunctionCallingConfig.builder()
                        .mode(FunctionCallingConfigMode.Known.ANY)
                        .allowedFunctionNames(allowedNames)
                        .build())
                .build();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(
                        Part.fromText(instructions)
                ))
                .tools(tool)
                .toolConfig(toolConfig)
                .automaticFunctionCalling(
                        AutomaticFunctionCallingConfig.builder()
                                .disable(true)
                                .build()
                )
                .maxOutputTokens(256)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.valueOf(
                                properties.thinkingLevel().name()
                        ))
                        .includeThoughts(false)
                        .build())
                .httpOptions(requestHttpOptions())
                .build();
        String prompt = "untrusted_customer_input_json:\n" + serialize(Map.of(
                "message", request.message(),
                "locale", request.locale(),
                "routing_scope", request.scope().name().toLowerCase(),
                "recent_conversation", request.recentConversation()
        ));

        Instant startedAt = Instant.now();
        GenerateContentResponse response = generate(prompt, config);
        long latencyMs = Math.max(
                0,
                Duration.between(startedAt, Instant.now()).toMillis()
        );
        DecodedCall decoded = decodeRoute(response, allowedNames);
        ModelTokenUsage usage = decodeUsage(
                response.usageMetadata().orElse(null)
        );
        ModelCostEstimate cost = costEstimator.estimate(
                properties.modelId(),
                usage
        );
        return new RoutingResult(
                CustomerChatModelRouter.CLASSIFIER,
                CustomerChatModelRouter.PROMPT_VERSION,
                decoded.callId(),
                decoded.tool(),
                decoded.deniedAction(),
                decoded.conversationKind(),
                properties.modelId(),
                response.modelVersion()
                        .filter(value -> !value.isBlank())
                        .orElse(null),
                response.responseId().orElse(null),
                usage,
                cost,
                latencyMs
        );
    }

    List<String> allowedFunctionNames(RoutingScope scope) {
        if (scope == RoutingScope.DENY_ONLY) {
            return List.of(
                    CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION
                            .wireValue()
            );
        }
        return List.of(
                CustomerChatModelRouter.Tool.GET_CURRENT_ORDER.wireValue(),
                CustomerChatModelRouter.Tool
                        .SEARCH_APPROVED_COMPANY_KNOWLEDGE.wireValue(),
                CustomerChatModelRouter.Tool.DENY_BUSINESS_ACTION.wireValue(),
                CustomerChatModelRouter.Tool
                        .CONTINUE_CUSTOMER_CONVERSATION.wireValue()
        );
    }

    DecodedCall decodeRoute(GenerateContentResponse response) {
        try {
            if (response == null) {
                throw malformed("Gemini returned no customer-chat route");
            }
            if (response.finishReason().knownEnum()
                    == FinishReason.Known.MAX_TOKENS) {
                throw malformed("Gemini customer-chat route was truncated");
            }
            List<FunctionCall> calls = response.functionCalls();
            if (calls.size() != 1) {
                throw malformed(
                        "Gemini must select exactly one customer-chat tool"
                );
            }
            boolean hasVisibleText = response.parts().stream()
                    .flatMap(part -> part.text().stream())
                    .anyMatch(text -> !text.isBlank());
            if (hasVisibleText) {
                throw malformed(
                        "Gemini customer-chat route included narrative text"
                );
            }
            FunctionCall call = calls.getFirst();
            String callId = call.id()
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> malformed(
                            "Gemini customer-chat tool call had no ID"
                    ));
            String name = call.name()
                    .filter(value -> !value.isBlank())
                    .orElseThrow(() -> malformed(
                            "Gemini customer-chat tool call had no name"
                    ));
            CustomerChatModelRouter.Tool selected;
            try {
                selected = CustomerChatModelRouter.Tool.fromWireValue(name);
            } catch (IllegalArgumentException exception) {
                throw malformed(
                        "Gemini selected a customer-chat tool outside the allowlist",
                        exception
                );
            }
            Map<String, Object> arguments = call.args().orElse(Map.of());
            return switch (selected) {
                case GET_CURRENT_ORDER -> {
                    requireExactKeys(arguments, Set.of());
                    yield new DecodedCall(
                            callId,
                            selected,
                            null,
                            null
                    );
                }
                case SEARCH_APPROVED_COMPANY_KNOWLEDGE -> {
                    requireExactKeys(arguments, Set.of());
                    yield new DecodedCall(
                            callId,
                            selected,
                            null,
                            null
                    );
                }
                case DENY_BUSINESS_ACTION -> {
                    requireExactKeys(arguments, Set.of("action"));
                    yield new DecodedCall(
                            callId,
                            selected,
                            deniedAction(arguments.get("action")),
                            null
                    );
                }
                case CONTINUE_CUSTOMER_CONVERSATION -> {
                    requireExactKeys(arguments, Set.of("kind"));
                    yield new DecodedCall(
                            callId,
                            selected,
                            null,
                            conversationKind(arguments.get("kind"))
                    );
                }
            };
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(
                    "Gemini customer-chat route could not be validated",
                    exception
            );
        }
    }

    DecodedCall decodeRoute(
            GenerateContentResponse response,
            List<String> allowedNames
    ) {
        DecodedCall decoded = decodeRoute(response);
        if (allowedNames == null
                || !allowedNames.contains(decoded.tool().wireValue())) {
            throw malformed(
                    "Gemini selected a customer-chat tool outside the active scope"
            );
        }
        return decoded;
    }

    ModelTokenUsage decodeUsage(
            GenerateContentResponseUsageMetadata usage
    ) {
        if (usage == null) {
            return null;
        }
        Integer inputTokens = usage.promptTokenCount().orElse(null);
        Integer cachedInputTokens = usage.cachedContentTokenCount()
                .orElse(null);
        Integer uncachedInputTokens = inputTokens == null
                || cachedInputTokens == null
                ? null
                : inputTokens - cachedInputTokens;
        Integer candidateOutputTokens = usage.candidatesTokenCount()
                .orElse(null);
        Integer thinkingOutputTokens = usage.thoughtsTokenCount()
                .orElse(null);
        Integer outputTokens = candidateOutputTokens == null
                ? null
                : candidateOutputTokens
                + (thinkingOutputTokens == null ? 0 : thinkingOutputTokens);
        return new ModelTokenUsage(
                inputTokens,
                cachedInputTokens,
                uncachedInputTokens,
                candidateOutputTokens,
                thinkingOutputTokens,
                outputTokens,
                usage.toolUsePromptTokenCount().orElse(null),
                usage.totalTokenCount().orElse(null)
        );
    }

    private GenerateContentResponse generate(
            String prompt,
            GenerateContentConfig config
    ) {
        try {
            return client().models.generateContent(
                    properties.modelId(),
                    prompt,
                    config
            );
        } catch (GenAiIOException exception) {
            if (isTimeout(exception)) {
                throw new ModelProviderException(
                        ModelProviderFailure.TIMEOUT,
                        "Gemini customer-chat routing timed out",
                        exception
                );
            }
            throw upstream(exception);
        } catch (ApiException exception) {
            if (exception.code() == 429) {
                throw new ModelProviderException(
                        ModelProviderFailure.RATE_LIMITED,
                        "Gemini customer-chat routing rate limit reached",
                        exception
                );
            }
            throw upstream(exception);
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw upstream(exception);
        }
    }

    private synchronized Client client() {
        if (client == null) {
            if (!properties.hasProviderConfiguration()) {
                throw new ModelProviderException(
                        ModelProviderFailure.UPSTREAM,
                        "Google Gen AI provider is not configured"
                );
            }
            client = clientFactory.create(requestHttpOptions());
        }
        return client;
    }

    private HttpOptions requestHttpOptions() {
        return HttpOptions.builder()
                .timeout(TIMEOUT_MS)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(1)
                        .build())
                .build();
    }

    private FunctionDeclaration declaration(
            CustomerChatModelRouter.Tool tool,
            String description
    ) {
        return FunctionDeclaration.builder()
                .name(tool.wireValue())
                .description(description)
                .parametersJsonSchema(loadSchema(
                        SCHEMA_RESOURCES.get(tool)
                ))
                .build();
    }

    private void requireExactKeys(
            Map<String, Object> arguments,
            Set<String> expected
    ) {
        if (!arguments.keySet().equals(expected)) {
            throw malformed(
                    "Gemini customer-chat tool arguments did not match the contract"
            );
        }
    }

    private DeniedAction deniedAction(Object value) {
        return enumValue(
                value,
                DeniedAction::fromWireValue,
                "denied action"
        );
    }

    private ConversationKind conversationKind(Object value) {
        return enumValue(
                value,
                ConversationKind::fromWireValue,
                "conversation kind"
        );
    }

    private <T> T enumValue(
            Object value,
            java.util.function.Function<String, T> decoder,
            String description
    ) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw malformed(
                    "Gemini customer-chat " + description + " was invalid"
            );
        }
        try {
            return decoder.apply(text);
        } catch (IllegalArgumentException exception) {
            throw malformed(
                    "Gemini customer-chat " + description + " was invalid",
                    exception
            );
        }
    }

    private Map<String, Object> loadSchema(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            Map<String, Object> schema = jsonMapper.readValue(
                    input,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            return Map.copyOf(schema);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load customer-chat tool schema: "
                            + resourcePath,
                    exception
            );
        }
    }

    private String loadText(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load customer-chat router prompt",
                    exception
            );
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize customer-chat router input",
                    exception
            );
        }
    }

    private static boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedIOException
                    || current instanceof HttpTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ModelProviderException upstream(Throwable cause) {
        return new ModelProviderException(
                ModelProviderFailure.UPSTREAM,
                "Gemini customer-chat routing failed",
                cause
        );
    }

    private ModelProviderException malformed(String message) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                message
        );
    }

    private ModelProviderException malformed(
            String message,
            Throwable cause
    ) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                message,
                cause
        );
    }

    @PreDestroy
    void closeClient() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }

    record DecodedCall(
            String callId,
            CustomerChatModelRouter.Tool tool,
            CustomerChatModelRouter.DeniedAction deniedAction,
            CustomerChatModelRouter.ConversationKind conversationKind
    ) {
    }
}
