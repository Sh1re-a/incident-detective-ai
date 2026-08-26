package dev.shirwac.incidentdetective.ai;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfig;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimValueTaxonomy;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import jakarta.annotation.PreDestroy;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class GeminiInvestigationModelGateway
        implements InvestigationModelGateway {

    private static final Duration MAX_PROVIDER_TIMEOUT = Duration.ofSeconds(28);
    private static final String COLLECT_PROMPT_RESOURCE =
            "ai/prompts/collect-gemini-live-v6.txt";
    private static final String SYNTHESIZE_PROMPT_RESOURCE =
            "ai/prompts/synthesize-gemini-live-v6.txt";

    private final GeminiAiProperties properties;
    private final GeminiDiagnosisDecoder diagnosisDecoder;
    private final JsonMapper jsonMapper;
    private final String collectInstructions;
    private final String synthesizeInstructions;
    private final Map<String, Object> diagnosisSchema;
    private final List<FunctionDeclaration> functionDeclarations;
    private volatile Client client;

    public GeminiInvestigationModelGateway(
            GeminiAiProperties properties,
            GeminiDiagnosisDecoder diagnosisDecoder,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.diagnosisDecoder = diagnosisDecoder;
        this.jsonMapper = jsonMapper;
        collectInstructions = loadText(COLLECT_PROMPT_RESOURCE);
        synthesizeInstructions = loadText(SYNTHESIZE_PROMPT_RESOURCE);
        diagnosisSchema = loadSchema("ai/diagnosis-schema-v3.json");
        functionDeclarations = List.of(
                function(
                        ToolName.GET_METRICS,
                        "Read named synthetic metrics inside the scenario time window.",
                        "ai/tool-schemas/get_metrics-v1.json"
                ),
                function(
                        ToolName.SEARCH_LOGS,
                        "Search synthetic logs with bounded service, level, text, and time filters.",
                        "ai/tool-schemas/search_logs-v1.json"
                ),
                function(
                        ToolName.GET_TRACE,
                        "Open one synthetic trace by an exact trace ID already found in evidence.",
                        "ai/tool-schemas/get_trace-v1.json"
                ),
                function(
                        ToolName.RETRIEVE_RUNBOOKS,
                        "Retrieve bounded synthetic runbook chunks relevant to the incident.",
                        "ai/tool-schemas/retrieve_runbooks-v1.json"
                )
        );
    }

    @Override
    public CollectionModelResult collect(
            Scenario scenario,
            List<String> availableMetricNames,
            List<Evidence> collectedEvidence,
            CollectionToolBudget toolBudget,
            int round,
            Duration timeout
    ) {
        List<String> allowedFunctionNames = allowedFunctionNames(toolBudget);
        if (allowedFunctionNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Gemini collection requires at least one allowed tool"
            );
        }
        List<FunctionDeclaration> availableDeclarations = functionDeclarations
                .stream()
                .filter(declaration -> allowedFunctionNames.contains(
                        declaration.name().orElseThrow()
                ))
                .toList();
        String prompt = collectInstructions + "\n\n"
                + "collection_round: " + round + "\n"
                + "remaining_tool_budget:\n"
                + serialize(toolBudget.promptView()) + "\n"
                + "scenario:\n" + serialize(scenario) + "\n"
                + "available_metric_names:\n"
                + serialize(availableMetricNames) + "\n"
                + "already_collected_evidence:\n"
                + serialize(collectedEvidence);
        FunctionCallingConfigMode.Known mode = round == 1
                ? FunctionCallingConfigMode.Known.ANY
                : FunctionCallingConfigMode.Known.VALIDATED;
        Tool tools = Tool.builder()
                .functionDeclarations(availableDeclarations)
                .build();
        ToolConfig toolConfig = ToolConfig.builder()
                .functionCallingConfig(FunctionCallingConfig.builder()
                        .mode(mode)
                        .allowedFunctionNames(allowedFunctionNames)
                        .build())
                .build();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(tools)
                .toolConfig(toolConfig)
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder()
                        .disable(true)
                        .build())
                .maxOutputTokens(1_024)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(properties.thinkingLevel().sdkValue())
                        .includeThoughts(false)
                        .build())
                .httpOptions(requestHttpOptions(timeout))
                .build();

        TimedResponse timed = generate(prompt, config);
        List<CollectionToolCall> calls = decodeToolCalls(timed.response());
        if (calls.size() > toolBudget.maxCallsThisRound()) {
            throw new ModelProviderException(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini exceeded the remaining round tool-call budget"
            );
        }
        return new CollectionModelResult(
                calls,
                metadata(ModelPhase.COLLECT, round, timed)
        );
    }

    List<String> allowedFunctionNames(CollectionToolBudget toolBudget) {
        return toolBudget.allowedTools().stream()
                .map(ToolName::wireValue)
                .toList();
    }

    @Override
    public SynthesisModelResult synthesize(
            Scenario scenario,
            List<Evidence> collectedEvidence,
            Duration timeout
    ) {
        String prompt = synthesizeInstructions + "\n\n"
                + "shared_claim_value_taxonomy:\n"
                + serialize(ClaimValueTaxonomy.wireValues()) + "\n"
                + "scenario:\n" + serialize(scenario) + "\n"
                + "tool_returned_evidence:\n" + serialize(collectedEvidence);
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseJsonSchema(diagnosisSchema)
                .maxOutputTokens(2_048)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(properties.thinkingLevel().sdkValue())
                        .includeThoughts(false)
                        .build())
                .httpOptions(requestHttpOptions(timeout))
                .build();

        TimedResponse timed = generate(prompt, config);
        return new SynthesisModelResult(
                decodeDiagnosis(timed.response()),
                metadata(ModelPhase.SYNTHESIZE, 1, timed)
        );
    }

    List<CollectionToolCall> decodeToolCalls(
            GenerateContentResponse response
    ) {
        try {
            List<CollectionToolCall> calls = new ArrayList<>();
            for (FunctionCall functionCall : response.functionCalls()) {
                String callId = functionCall.id().orElseThrow(() -> malformed(
                        "Gemini function call did not include an ID"
                ));
                String name = functionCall.name().orElseThrow(() -> malformed(
                        "Gemini function call did not include a name"
                ));
                ToolName toolName;
                try {
                    toolName = ToolName.fromWireValue(name);
                } catch (IllegalArgumentException exception) {
                    throw malformed("Gemini requested a tool outside the allowlist");
                }
                calls.add(new CollectionToolCall(
                        callId,
                        toolName,
                        functionCall.args().orElse(Map.of())
                ));
            }
            return List.copyOf(calls);
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(
                    "Gemini collection response could not be read",
                    exception
            );
        }
    }

    Diagnosis decodeDiagnosis(GenerateContentResponse response) {
        try {
            return diagnosisDecoder.decode(response.text());
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(
                    "Gemini synthesis response could not be read",
                    exception
            );
        }
    }

    private TimedResponse generate(
            String prompt,
            GenerateContentConfig config
    ) {
        Instant startedAt = Instant.now();
        try {
            GenerateContentResponse response = client().models.generateContent(
                    properties.modelId(),
                    prompt,
                    config
            );
            return new TimedResponse(
                    response,
                    Math.max(0, Duration.between(startedAt, Instant.now()).toMillis())
            );
        } catch (GenAiIOException exception) {
            if (isTimeout(exception)) {
                throw new ModelProviderException(
                        ModelProviderFailure.TIMEOUT,
                        "Gemini request timed out",
                        exception
                );
            }
            throw upstream(exception);
        } catch (ApiException exception) {
            throw upstream(exception);
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw upstream(exception);
        }
    }

    private ModelCallMetadata metadata(
            ModelPhase phase,
            int round,
            TimedResponse timed
    ) {
        GenerateContentResponse response = timed.response();
        GenerateContentResponseUsageMetadata usage = response.usageMetadata()
                .orElse(null);
        return new ModelCallMetadata(
                phase,
                round,
                response.responseId().orElse(null),
                response.modelVersion().orElse(properties.modelId()),
                decodeUsage(usage),
                timed.latencyMs()
        );
    }

    ModelTokenUsage decodeUsage(
            GenerateContentResponseUsageMetadata usage
    ) {
        if (usage == null) {
            return null;
        }
        Integer inputTokens = usage.promptTokenCount().orElse(null);
        Integer cachedInputTokens = usage.cachedContentTokenCount().orElse(null);
        Integer uncachedInputTokens = inputTokens == null
                || cachedInputTokens == null
                ? null
                : inputTokens - cachedInputTokens;
        Integer candidateOutputTokens = usage.candidatesTokenCount().orElse(null);
        Integer thinkingOutputTokens = usage.thoughtsTokenCount().orElse(null);
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

    private FunctionDeclaration function(
            ToolName toolName,
            String description,
            String schemaResource
    ) {
        return FunctionDeclaration.builder()
                .name(toolName.wireValue())
                .description(description)
                .parametersJsonSchema(loadSchema(schemaResource))
                .build();
    }

    private synchronized Client client() {
        if (client == null) {
            if (!properties.hasApiKey()) {
                throw new ModelProviderException(
                        ModelProviderFailure.UPSTREAM,
                        "Gemini API key is not configured"
                );
            }
            client = Client.builder()
                    .apiKey(properties.geminiApiKey())
                    .httpOptions(requestHttpOptions(MAX_PROVIDER_TIMEOUT))
                    .build();
        }
        return client;
    }

    private HttpOptions requestHttpOptions(Duration timeout) {
        if (timeout == null
                || timeout.isZero()
                || timeout.isNegative()
                || timeout.compareTo(MAX_PROVIDER_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    "Gemini timeout must be between 1 ms and 28 seconds"
            );
        }
        int timeoutMs = Math.toIntExact(Math.max(1, timeout.toMillis()));
        return HttpOptions.builder()
                .timeout(timeoutMs)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(1)
                        .build())
                .build();
    }

    @PreDestroy
    void closeClient() {
        Client current = client;
        if (current != null) {
            current.close();
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
                    "Could not load AI schema: " + resourcePath,
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
                    "Could not load AI prompt: " + resourcePath,
                    exception
            );
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize bounded model input",
                    exception
            );
        }
    }

    static boolean isTimeout(Throwable throwable) {
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
                "Gemini provider request failed",
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

    private record TimedResponse(
            GenerateContentResponse response,
            long latencyMs
    ) {
    }
}
