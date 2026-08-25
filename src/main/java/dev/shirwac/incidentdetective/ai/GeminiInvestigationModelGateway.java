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
import com.google.genai.types.ThinkingLevel;
import com.google.genai.types.Tool;
import com.google.genai.types.ToolConfig;
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
import java.net.SocketTimeoutException;
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

    private static final int PROVIDER_TIMEOUT_MS = 14_000;
    private static final int MAX_TOOL_CALLS_PER_ROUND = 3;
    private static final String COLLECT_PROMPT_RESOURCE =
            "ai/prompts/collect-gemini-live-v1.txt";
    private static final String SYNTHESIZE_PROMPT_RESOURCE =
            "ai/prompts/synthesize-gemini-live-v1.txt";

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
        diagnosisSchema = loadSchema("ai/diagnosis-schema-v1.json");
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
            int round
    ) {
        String prompt = collectInstructions + "\n\n"
                + "collection_round: " + round + "\n"
                + "scenario:\n" + serialize(scenario) + "\n"
                + "available_metric_names:\n"
                + serialize(availableMetricNames) + "\n"
                + "already_collected_evidence:\n"
                + serialize(collectedEvidence);
        FunctionCallingConfigMode.Known mode = round == 1
                ? FunctionCallingConfigMode.Known.ANY
                : FunctionCallingConfigMode.Known.VALIDATED;
        Tool tools = Tool.builder()
                .functionDeclarations(functionDeclarations)
                .build();
        ToolConfig toolConfig = ToolConfig.builder()
                .functionCallingConfig(FunctionCallingConfig.builder()
                        .mode(mode)
                        .allowedFunctionNames(functionDeclarations.stream()
                                .map(declaration -> declaration.name().orElseThrow())
                                .toList())
                        .build())
                .build();
        GenerateContentConfig config = GenerateContentConfig.builder()
                .tools(tools)
                .toolConfig(toolConfig)
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder()
                        .disable(true)
                        .build())
                .maxOutputTokens(1_024)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.LOW)
                        .includeThoughts(false)
                        .build())
                .build();

        TimedResponse timed = generate(prompt, config);
        List<CollectionToolCall> calls = decodeToolCalls(timed.response());
        if (calls.size() > MAX_TOOL_CALLS_PER_ROUND) {
            throw new ModelProviderException(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini exceeded the per-round tool-call budget"
            );
        }
        return new CollectionModelResult(
                calls,
                metadata(ModelPhase.COLLECT, round, timed)
        );
    }

    @Override
    public SynthesisModelResult synthesize(
            Scenario scenario,
            List<Evidence> collectedEvidence
    ) {
        String prompt = synthesizeInstructions + "\n\n"
                + "scenario:\n" + serialize(scenario) + "\n"
                + "tool_returned_evidence:\n" + serialize(collectedEvidence);
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseJsonSchema(diagnosisSchema)
                .maxOutputTokens(2_048)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.LOW)
                        .includeThoughts(false)
                        .build())
                .build();

        TimedResponse timed = generate(prompt, config);
        return new SynthesisModelResult(
                diagnosisDecoder.decode(timed.response().text()),
                metadata(ModelPhase.SYNTHESIZE, 1, timed)
        );
    }

    private List<CollectionToolCall> decodeToolCalls(
            GenerateContentResponse response
    ) {
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
        int inputTokens = usage == null ? 0 : usage.promptTokenCount().orElse(0);
        int outputTokens = usage == null ? 0
                : usage.candidatesTokenCount().orElse(0)
                + usage.thoughtsTokenCount().orElse(0);
        int totalTokens = usage == null
                ? inputTokens + outputTokens
                : usage.totalTokenCount().orElse(inputTokens + outputTokens);
        return new ModelCallMetadata(
                phase,
                round,
                response.responseId().orElse(null),
                response.modelVersion().orElse(properties.modelId()),
                new ModelTokenUsage(inputTokens, outputTokens, totalTokens),
                timed.latencyMs()
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
            HttpOptions httpOptions = HttpOptions.builder()
                    .timeout(PROVIDER_TIMEOUT_MS)
                    .retryOptions(HttpRetryOptions.builder()
                            .attempts(1)
                            .build())
                    .build();
            client = Client.builder()
                    .apiKey(properties.geminiApiKey())
                    .httpOptions(httpOptions)
                    .build();
        }
        return client;
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

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
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

    private record TimedResponse(
            GenerateContentResponse response,
            long latencyMs
    ) {
    }
}
