package dev.shirwac.incidentdetective.planning;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
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
import java.util.Map;

/**
 * One structured-output Gemini call that proposes, but never approves or runs,
 * a synthetic incident plan.
 */
@Component
@Profile("rag")
public final class GeminiIncidentPlannerGateway
        implements IncidentPlannerGateway {

    static final String PROMPT_RESOURCE =
            "ai/prompts/plan-synthetic-incident-v1.txt";
    static final String SCHEMA_RESOURCE =
            "ai/incident-plan-proposal-schema-v1.json";
    private static final int TIMEOUT_MS = 15_000;

    private final GeminiAiProperties properties;
    private final GoogleGenAiClientFactory clientFactory;
    private final JsonMapper jsonMapper;
    private final String instructions;
    private final Map<String, Object> schema;
    private volatile Client client;

    public GeminiIncidentPlannerGateway(
            GeminiAiProperties properties,
            GoogleGenAiClientFactory clientFactory,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.jsonMapper = jsonMapper;
        instructions = loadText(PROMPT_RESOURCE);
        schema = loadSchema(SCHEMA_RESOURCE);
    }

    @Override
    public IncidentPlannerResponse propose(IncidentPlanningRequest request) {
        if (!properties.liveEnabled()) {
            throw failure(
                    IncidentPlannerFailure.DISABLED,
                    "Incident planning AI is disabled",
                    null
            );
        }
        if (!properties.hasProviderConfiguration()) {
            throw failure(
                    IncidentPlannerFailure.NOT_CONFIGURED,
                    "Google Gen AI provider is not configured",
                    null
            );
        }

        String prompt = "untrusted_request_json:\n" + serialize(Map.of(
                "instruction", request.instruction()
        ));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(instructions)))
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .maxOutputTokens(450)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.valueOf(
                                properties.thinkingLevel().name()
                        ))
                        .includeThoughts(false)
                        .build())
                .httpOptions(httpOptions())
                .build();

        Instant startedAt = Instant.now();
        try {
            GenerateContentResponse response = client().models.generateContent(
                    properties.modelId(),
                    prompt,
                    config
            );
            long latencyMs = Math.max(
                    0,
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
            rejectMissingOrTruncated(response);
            IncidentPlanProposal proposal = jsonMapper.readValue(
                    response.text(),
                    IncidentPlanProposal.class
            );
            String model = response.modelVersion()
                    .filter(value -> !value.isBlank())
                    .orElse(properties.modelId());
            return new IncidentPlannerResponse(
                    proposal,
                    new IncidentPlannerReceipt(
                            properties.provider().transport(),
                            model,
                            response.responseId().orElse(null),
                            decodeUsage(response.usageMetadata().orElse(null)),
                            latencyMs
                    )
            );
        } catch (IncidentPlannerException exception) {
            throw exception;
        } catch (GenAiIOException exception) {
            if (isTimeout(exception)) {
                throw failure(
                        IncidentPlannerFailure.TIMEOUT,
                        "Incident planning timed out",
                        exception
                );
            }
            throw failure(
                    IncidentPlannerFailure.UPSTREAM,
                    "Incident planning provider call failed",
                    exception
            );
        } catch (ApiException exception) {
            IncidentPlannerFailure providerFailure = exception.code() == 429
                    ? IncidentPlannerFailure.RATE_LIMITED
                    : IncidentPlannerFailure.UPSTREAM;
            throw failure(
                    providerFailure,
                    "Incident planning provider call failed",
                    exception
            );
        } catch (IllegalStateException exception) {
            throw failure(
                    IncidentPlannerFailure.NOT_CONFIGURED,
                    "Google Gen AI client could not be initialized",
                    exception
            );
        } catch (RuntimeException exception) {
            throw failure(
                    IncidentPlannerFailure.MALFORMED_RESPONSE,
                    "Incident planning response was invalid",
                    exception
            );
        } catch (Exception exception) {
            throw failure(
                    IncidentPlannerFailure.MALFORMED_RESPONSE,
                    "Incident planning response was invalid",
                    exception
            );
        }
    }

    private synchronized Client client() {
        if (client == null) {
            client = clientFactory.create(httpOptions());
        }
        return client;
    }

    private HttpOptions httpOptions() {
        return HttpOptions.builder()
                .timeout(TIMEOUT_MS)
                .retryOptions(HttpRetryOptions.builder()
                        .attempts(1)
                        .build())
                .build();
    }

    private void rejectMissingOrTruncated(GenerateContentResponse response) {
        if (response == null || response.text() == null
                || response.text().isBlank()) {
            throw failure(
                    IncidentPlannerFailure.MALFORMED_RESPONSE,
                    "Gemini returned no incident plan proposal",
                    null
            );
        }
        if (response.finishReason().knownEnum()
                == FinishReason.Known.MAX_TOKENS) {
            throw failure(
                    IncidentPlannerFailure.MALFORMED_RESPONSE,
                    "Gemini incident plan proposal was truncated",
                    null
            );
        }
    }

    private ModelTokenUsage decodeUsage(
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

    private Map<String, Object> loadSchema(String resourcePath) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            Map<String, Object> value = jsonMapper.readValue(
                    input,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            return Map.copyOf(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load incident planning schema",
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
                    "Could not load incident planning prompt",
                    exception
            );
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize bounded incident planning input",
                    exception
            );
        }
    }

    private boolean isTimeout(Throwable throwable) {
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

    private IncidentPlannerException failure(
            IncidentPlannerFailure providerFailure,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new IncidentPlannerException(providerFailure, message)
                : new IncidentPlannerException(
                        providerFailure,
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
}
