package dev.shirwac.incidentdetective.rag;

import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.ContentEmbeddingStatistics;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentMetadata;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiEmbeddingGatewayTest {

    private static final int DIMENSIONS = 768;

    @Test
    void usesTheDocumentedAsymmetricSearchFormats() {
        CapturingApi api = new CapturingApi(validResponse());
        GeminiEmbeddingGateway gateway = gateway(api);

        EmbeddingResult query = gateway.embedQuery("payment timeout");
        assertEquals(
                "task: search result | query: payment timeout",
                api.inputs().get(0)
        );

        EmbeddingResult document = gateway.embedDocument(
                "Timeout precedence",
                "Inspect the client deadline first."
        );
        assertEquals(
                "title: Timeout precedence | text: Inspect the client deadline first.",
                api.inputs().get(1)
        );
        assertEquals("gemini-embedding-2", api.models().getFirst());
        assertEquals(DIMENSIONS, api.configs().getFirst()
                .outputDimensionality().orElseThrow());
        assertFalse(api.configs().getFirst().autoTruncate().isPresent());
        assertTrueEmpty(api.configs().getFirst());
        assertEquals(DIMENSIONS, query.values().size());
        assertEquals(42, document.billableCharacters());
        assertEquals(9.0, document.inputTokens());
    }

    @Test
    void rejectsAnUnexpectedNumberOfEmbeddings() {
        EmbedContentResponse response = EmbedContentResponse.builder()
                .embeddings(validEmbedding(), validEmbedding())
                .build();

        RunbookEmbeddingException exception = assertThrows(
                RunbookEmbeddingException.class,
                () -> gateway(new CapturingApi(response)).embedQuery("timeout")
        );

        assertEquals(RunbookEmbeddingFailure.MALFORMED_RESPONSE, exception.failure());
    }

    @Test
    void rejectsWrongDimensionsAndSilentTruncation() {
        ContentEmbedding wrongDimensions = ContentEmbedding.builder()
                .values(Collections.nCopies(12, 0.25f))
                .build();
        assertThrows(
                RunbookEmbeddingException.class,
                () -> gateway(new CapturingApi(EmbedContentResponse.builder()
                        .embeddings(wrongDimensions)
                        .build())).embedQuery("timeout")
        );

        ContentEmbedding truncated = ContentEmbedding.builder()
                .values(Collections.nCopies(DIMENSIONS, 0.25f))
                .statistics(ContentEmbeddingStatistics.builder()
                        .truncated(true)
                        .build())
                .build();
        assertThrows(
                RunbookEmbeddingException.class,
                () -> gateway(new CapturingApi(EmbedContentResponse.builder()
                        .embeddings(truncated)
                        .build())).embedQuery("timeout")
        );
    }

    @Test
    void rejectsOversizedInputBeforeCallingTheProvider() {
        CapturingApi api = new CapturingApi(validResponse());

        assertThrows(
                IllegalArgumentException.class,
                () -> gateway(api).embedDocument("title", "x".repeat(4_001))
        );
        assertTrue(api.inputs().isEmpty());
    }

    private GeminiEmbeddingGateway gateway(GeminiEmbeddingApi api) {
        return new GeminiEmbeddingGateway(
                api,
                new RagProperties(
                        "gemini-embedding-2",
                        DIMENSIONS,
                        "search-result-v1",
                        0.0
                )
        );
    }

    private EmbedContentResponse validResponse() {
        return EmbedContentResponse.builder()
                .embeddings(validEmbedding())
                .metadata(EmbedContentMetadata.builder()
                        .billableCharacterCount(42)
                        .build())
                .build();
    }

    private ContentEmbedding validEmbedding() {
        return ContentEmbedding.builder()
                .values(Collections.nCopies(DIMENSIONS, 0.25f))
                .statistics(ContentEmbeddingStatistics.builder()
                        .truncated(false)
                        .tokenCount(9.0f)
                        .build())
                .build();
    }

    private void assertTrueEmpty(EmbedContentConfig config) {
        assertFalse(config.taskType().isPresent());
        assertFalse(config.title().isPresent());
    }

    private static final class CapturingApi implements GeminiEmbeddingApi {

        private final EmbedContentResponse response;
        private final List<String> models = new ArrayList<>();
        private final List<String> inputs = new ArrayList<>();
        private final List<EmbedContentConfig> configs = new ArrayList<>();

        private CapturingApi(EmbedContentResponse response) {
            this.response = response;
        }

        @Override
        public EmbedContentResponse embed(
                String model,
                String input,
                EmbedContentConfig config
        ) {
            models.add(model);
            inputs.add(input);
            configs.add(config);
            return response;
        }

        private List<String> models() {
            return List.copyOf(models);
        }

        private List<String> inputs() {
            return List.copyOf(inputs);
        }

        private List<EmbedContentConfig> configs() {
            return List.copyOf(configs);
        }
    }
}
