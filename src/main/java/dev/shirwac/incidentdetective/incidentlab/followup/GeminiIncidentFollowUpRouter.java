package dev.shirwac.incidentdetective.incidentlab.followup;

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
import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("rag")
public final class GeminiIncidentFollowUpRouter
        implements IncidentFollowUpRouter {

    static final String PROMPT = "ai/prompts/route-incident-follow-up-v1.txt";
    static final String SCHEMA = "ai/incident-follow-up-route-schema-v1.json";

    private final GeminiAiProperties properties;
    private final GoogleGenAiClientFactory clientFactory;
    private final JsonMapper jsonMapper;
    private final String instructions;
    private final Map<String, Object> schema;
    private volatile Client client;

    public GeminiIncidentFollowUpRouter(
            GeminiAiProperties properties,
            GoogleGenAiClientFactory clientFactory,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.jsonMapper = jsonMapper;
        instructions = text(PROMPT);
        schema = schema(SCHEMA);
    }

    @Override
    public Result route(Input input) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(instructions)))
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .maxOutputTokens(180)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.valueOf(
                                properties.thinkingLevel().name()
                        ))
                        .includeThoughts(false)
                        .build())
                .httpOptions(httpOptions())
                .build();
        Instant started = Instant.now();
        try {
            GenerateContentResponse response = client().models.generateContent(
                    properties.modelId(),
                    "untrusted_request_json:\n" + jsonMapper.writeValueAsString(input),
                    config
            );
            if (response == null || response.text() == null
                    || response.text().isBlank()
                    || response.finishReason().knownEnum()
                    == FinishReason.Known.MAX_TOKENS) {
                throw failure(ModelProviderFailure.MALFORMED_RESPONSE,
                        "Gemini returned no complete incident follow-up route", null);
            }
            Decision decision = jsonMapper.readValue(
                    response.text(),
                    Decision.class
            );
            long latency = Math.max(0, Duration.between(
                    started,
                    Instant.now()
            ).toMillis());
            return new Result(decision, new ProviderMetadata(
                    GoogleGenAiProviderRoute.from(properties),
                    response.responseId().orElse(null),
                    response.modelVersion().orElse(properties.modelId()),
                    usage(response.usageMetadata().orElse(null)),
                    latency
            ));
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (ApiException exception) {
            throw failure(exception.code() == 429
                            ? ModelProviderFailure.RATE_LIMITED
                            : ModelProviderFailure.UPSTREAM,
                    "Gemini incident follow-up routing failed", exception);
        } catch (GenAiIOException exception) {
            throw failure(ModelProviderFailure.UPSTREAM,
                    "Gemini incident follow-up routing failed", exception);
        } catch (Exception exception) {
            throw failure(ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini incident follow-up route was invalid", exception);
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
                .timeout(12_000)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                .build();
    }

    private ModelTokenUsage usage(GenerateContentResponseUsageMetadata usage) {
        if (usage == null) {
            return null;
        }
        Integer input = usage.promptTokenCount().orElse(null);
        Integer cached = usage.cachedContentTokenCount().orElse(null);
        Integer candidate = usage.candidatesTokenCount().orElse(null);
        Integer thoughts = usage.thoughtsTokenCount().orElse(null);
        return new ModelTokenUsage(
                input,
                cached,
                input == null || cached == null ? null : Math.max(0, input - cached),
                candidate,
                thoughts,
                candidate == null ? null : candidate + (thoughts == null ? 0 : thoughts),
                usage.toolUsePromptTokenCount().orElse(null),
                usage.totalTokenCount().orElse(null)
        );
    }

    private String text(String path) {
        try {
            return new ClassPathResource(path)
                    .getContentAsString(StandardCharsets.UTF_8)
                    .strip();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load follow-up prompt", exception);
        }
    }

    private Map<String, Object> schema(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return Map.copyOf(jsonMapper.readValue(
                    input,
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load follow-up schema", exception);
        }
    }

    private ModelProviderException failure(
            ModelProviderFailure type,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new ModelProviderException(type, message)
                : new ModelProviderException(type, message, cause);
    }

    @PreDestroy
    void close() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }
}
