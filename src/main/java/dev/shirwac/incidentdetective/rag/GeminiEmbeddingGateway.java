package dev.shirwac.incidentdetective.rag;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.ContentEmbeddingStatistics;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentMetadata;
import com.google.genai.types.EmbedContentResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "incident-detective.rag.mode",
        havingValue = "pgvector"
)
public final class GeminiEmbeddingGateway implements EmbeddingGateway {

    private static final String QUERY_PREFIX = "task: search result | query: ";

    private final GeminiEmbeddingApi api;
    private final RagProperties properties;

    public GeminiEmbeddingGateway(
            GeminiEmbeddingApi api,
            RagProperties properties
    ) {
        this.api = api;
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embedQuery(String query) {
        return embed(QUERY_PREFIX + requireText(query, "query"));
    }

    @Override
    public EmbeddingResult embedDocument(String title, String text) {
        return embed("title: " + requireText(title, "title")
                + " | text: " + requireText(text, "text"));
    }

    private EmbeddingResult embed(String input) {
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(properties.embeddingDimensions())
                .autoTruncate(false)
                .build();
        Instant startedAt = Instant.now();
        EmbedContentResponse response = api.embed(
                properties.embeddingModel(),
                input,
                config
        );
        long latencyMs = Math.max(
                0,
                Duration.between(startedAt, Instant.now()).toMillis()
        );
        return decode(response, latencyMs);
    }

    private EmbeddingResult decode(
            EmbedContentResponse response,
            long latencyMs
    ) {
        if (response == null) {
            throw malformed("Gemini returned no embedding response");
        }
        List<ContentEmbedding> embeddings = response.embeddings().orElse(List.of());
        if (embeddings.size() != 1) {
            throw malformed("Gemini must return exactly one embedding per input");
        }
        ContentEmbedding embedding = embeddings.getFirst();
        List<Float> values = embedding.values().orElse(List.of());
        if (values.size() != properties.embeddingDimensions()) {
            throw malformed("Gemini embedding dimensions did not match configuration");
        }
        if (values.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw malformed("Gemini embedding contained a non-finite value");
        }

        ContentEmbeddingStatistics statistics = embedding.statistics().orElse(null);
        if (statistics != null && statistics.truncated().orElse(false)) {
            throw malformed("Gemini truncated an embedding input");
        }
        double inputTokens = statistics == null
                ? 0
                : statistics.tokenCount().orElse(0.0f);
        EmbedContentMetadata metadata = response.metadata().orElse(null);
        int billableCharacters = metadata == null
                ? 0
                : metadata.billableCharacterCount().orElse(0);
        return new EmbeddingResult(
                values,
                billableCharacters,
                inputTokens,
                latencyMs
        );
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private RunbookEmbeddingException malformed(String message) {
        return new RunbookEmbeddingException(
                RunbookEmbeddingFailure.MALFORMED_RESPONSE,
                message
        );
    }
}
