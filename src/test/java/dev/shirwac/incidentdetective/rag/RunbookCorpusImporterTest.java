package dev.shirwac.incidentdetective.rag;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunbookCorpusImporterTest {

    private static final RagProperties PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.0
    );

    @Test
    void embedsOnlyMissingOrChangedChunksAndReportsUsage() {
        ClasspathRunbookCorpus corpus = corpus();
        FakeStore store = new FakeStore(Set.of(
                corpus.entries().getFirst().evidenceId()
        ));
        CountingEmbeddings embeddings = new CountingEmbeddings();
        RunbookCorpusImporter importer = new RunbookCorpusImporter(
                corpus,
                store,
                embeddings,
                PROFILE,
                Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC)
        );

        RunbookImportReport report = importer.importMissingOrChanged();

        assertEquals(12, report.totalChunks());
        assertEquals(11, report.importedChunks());
        assertEquals(1, report.skippedChunks());
        assertEquals(11, embeddings.inputs.size());
        assertEquals(11, store.upserted.size());
        assertTrue(report.inputCharacters() > 0);
        assertEquals(110, report.providerBillableCharacters());
        assertEquals(33.0, report.providerInputTokens());
        assertTrue(report.providerUsageMetadataComplete());
        assertEquals(55, report.embeddingLatencyMs());
        assertEquals(Instant.parse("2026-08-26T10:00:00Z"), report.completedAt());
        assertTrue(embeddings.inputs.stream().allMatch(input -> input.startsWith("title: ")));
    }

    private ClasspathRunbookCorpus corpus() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new ClasspathRunbookCorpus(
                mapper,
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    private static final class CountingEmbeddings implements EmbeddingGateway {

        private final List<String> inputs = new ArrayList<>();

        @Override
        public EmbeddingResult embedQuery(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmbeddingResult embedDocument(String title, String text) {
            inputs.add("title: " + title + " | text: " + text);
            return new EmbeddingResult(
                    java.util.Collections.nCopies(768, 0.25f),
                    ("title: " + title + " | text: " + text).length(),
                    10,
                    3.0,
                    5
            );
        }
    }

    private static final class FakeStore implements RunbookVectorStore {

        private final Set<String> current;
        private final List<String> upserted = new ArrayList<>();

        private FakeStore(Set<String> current) {
            this.current = new HashSet<>(current);
        }

        @Override
        public boolean containsCurrent(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile
        ) {
            return current.contains(entry.evidenceId());
        }

        @Override
        public void upsert(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile,
                EmbeddingResult embedding
        ) {
            upserted.add(entry.evidenceId());
        }

        @Override
        public List<RunbookSearchHit> search(
                String corpusVersion,
                RagProperties profile,
                List<Float> queryEmbedding,
                int topK,
                double minimumSimilarity
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> documentIds(
                String corpusVersion,
                RagProperties profile
        ) {
            return List.of();
        }

        @Override
        public long count(String corpusVersion, RagProperties profile) {
            return current.size() + upserted.size();
        }
    }
}
