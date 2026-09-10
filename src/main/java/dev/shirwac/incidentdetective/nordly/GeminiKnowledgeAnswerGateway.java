package dev.shirwac.incidentdetective.nordly;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.FinishReason;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.ThinkingLevel;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
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
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("rag")
final class GeminiKnowledgeAnswerGateway implements KnowledgeAnswerGateway {

    private static final String PROMPT_RESOURCE =
            "ai/prompts/answer-nordly-knowledge-v1.txt";
    private static final String SCHEMA_RESOURCE =
            "ai/knowledge-answer-schema-v1.json";
    private static final int TIMEOUT_MS = 15_000;

    private final GeminiAiProperties properties;
    private final JsonMapper jsonMapper;
    private final String instructions;
    private final Map<String, Object> schema;
    private volatile Client client;

    GeminiKnowledgeAnswerGateway(
            GeminiAiProperties properties,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        instructions = loadText(PROMPT_RESOURCE);
        schema = loadSchema(SCHEMA_RESOURCE);
    }

    @Override
    public KnowledgeGenerationResult generate(
            String question,
            List<KnowledgeRagResponse.RankedMatch> context
    ) {
        String prompt = "bounded_input_json:\n"
                + serialize(Map.of(
                        "question", question,
                        "retrieved_context", context.stream()
                                .map(match -> Map.of(
                                        "citation_id", match.evidenceId(),
                                        "title", match.title(),
                                        "section", match.sectionHeading(),
                                        "text", match.text()
                                ))
                                .toList()
                ));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(instructions)))
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .maxOutputTokens(900)
                .temperature(0.0F)
                .thinkingConfig(ThinkingConfig.builder()
                        .thinkingLevel(ThinkingLevel.Known.valueOf(
                                properties.thinkingLevel().name()
                        ))
                        .includeThoughts(false)
                        .build())
                .httpOptions(HttpOptions.builder()
                        .timeout(TIMEOUT_MS)
                        .retryOptions(HttpRetryOptions.builder()
                                .attempts(1)
                                .build())
                        .build())
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
            rejectTruncated(response);
            KnowledgeGeneratedAnswer answer = jsonMapper.readValue(
                    response.text(),
                    KnowledgeGeneratedAnswer.class
            );
            return new KnowledgeGenerationResult(
                    answer,
                    response.responseId().orElse(null),
                    response.modelVersion()
                            .filter(value -> !value.isBlank())
                            .orElse(null),
                    decodeUsage(response.usageMetadata().orElse(null)),
                    latencyMs
            );
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (GenAiIOException exception) {
            if (isTimeout(exception)) {
                throw providerFailure(
                        ModelProviderFailure.TIMEOUT,
                        "Gemini knowledge synthesis timed out",
                        exception
                );
            }
            throw providerFailure(
                    ModelProviderFailure.UPSTREAM,
                    "Gemini knowledge synthesis failed",
                    exception
            );
        } catch (ApiException exception) {
            ModelProviderFailure failure = exception.code() == 429
                    ? ModelProviderFailure.RATE_LIMITED
                    : ModelProviderFailure.UPSTREAM;
            throw providerFailure(
                    failure,
                    "Gemini knowledge synthesis failed",
                    exception
            );
        } catch (RuntimeException exception) {
            throw providerFailure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini knowledge synthesis response was invalid",
                    exception
            );
        } catch (Exception exception) {
            throw providerFailure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini knowledge synthesis response was invalid",
                    exception
            );
        }
    }

    private synchronized Client client() {
        if (client == null) {
            if (!properties.hasApiKey()) {
                throw providerFailure(
                        ModelProviderFailure.UPSTREAM,
                        "Gemini API key is not configured",
                        null
                );
            }
            client = Client.builder()
                    .apiKey(properties.geminiApiKey())
                    .httpOptions(HttpOptions.builder()
                            .timeout(TIMEOUT_MS)
                            .retryOptions(HttpRetryOptions.builder()
                                    .attempts(1)
                                    .build())
                            .build())
                    .build();
        }
        return client;
    }

    private void rejectTruncated(GenerateContentResponse response) {
        if (response == null) {
            throw providerFailure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini returned no knowledge answer",
                    null
            );
        }
        if (response.finishReason().knownEnum() == FinishReason.Known.MAX_TOKENS) {
            throw providerFailure(
                    ModelProviderFailure.MALFORMED_RESPONSE,
                    "Gemini knowledge answer was truncated",
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
                    "Could not load Nordly answer schema",
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
                    "Could not load Nordly answer prompt",
                    exception
            );
        }
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not serialize bounded Nordly answer input",
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

    private ModelProviderException providerFailure(
            ModelProviderFailure failure,
            String message,
            Throwable cause
    ) {
        return cause == null
                ? new ModelProviderException(failure, message)
                : new ModelProviderException(failure, message, cause);
    }

    @PreDestroy
    void closeClient() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }
}
