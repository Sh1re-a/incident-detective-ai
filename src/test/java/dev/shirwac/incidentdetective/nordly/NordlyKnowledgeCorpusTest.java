package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.rag.EmbeddingGateway;
import dev.shirwac.incidentdetective.rag.EmbeddingResult;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import dev.shirwac.incidentdetective.rag.RunbookImportReport;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NordlyKnowledgeCorpusTest {

    private static final String EXPECTED_CORPUS_CONTENT_SHA256 =
            "6ec834d8aa784e433961a798e32edb96c6ed179ef853a1e557da8021c84f466d";
    private static final RagProperties PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.0,
            GoogleGenAiProvider.DEVELOPER_API
    );

    @Autowired
    private NordlyKnowledgeCorpus corpus;

    @Test
    void exposesOnlyApprovedPublicDemoDocuments() {
        assertEquals(
                "nordly-knowledge-manifest-v2",
                corpus.manifestVersion()
        );
        assertEquals("nordly-knowledge-corpus-v2", corpus.version());
        assertEquals(
                EXPECTED_CORPUS_CONTENT_SHA256,
                corpus.corpusContentSha256()
        );
        assertEquals(
                CorpusFingerprint.sha256(corpus.entries()),
                corpus.corpusContentSha256()
        );
        assertEquals(13, corpus.eligibleDocumentCount());
        assertEquals(28, corpus.eligibleChunkCount());
        assertTrue(corpus.entries().stream()
                .allMatch(entry -> corpus.metadata(entry.evidenceId())
                        .status().equals("APPROVED")));
        assertFalse(corpus.entries().stream().anyMatch(entry ->
                entry.documentId().equals("kb-legacy-refund-playbook")
                        || entry.documentId().equals("kb-untrusted-shortcuts")
                        || entry.documentId().equals(
                                "kb-employee-compensation-register"
                        )
        ));
        NordlyKnowledgeCorpus.EntryMetadata support = corpus.metadata(
                "nordly-evidence-manual-support-contact"
        );
        assertEquals("APPROVED", support.status());
        assertEquals(
                "knowledge/kb-order-status-cancellation#manual-support-contact",
                support.sourceRef()
        );
        assertTrue(support.text().contains("synthetic support number 123"));
    }

    @Test
    void importIsExplicitAndIdempotent() {
        FakeStore store = new FakeStore();
        CountingEmbeddings embeddings = new CountingEmbeddings();
        NordlyKnowledgeCorpusImporter importer =
                new NordlyKnowledgeCorpusImporter(
                        corpus,
                        store,
                        embeddings,
                        PROFILE,
                        Clock.fixed(
                                Instant.parse("2026-09-03T10:00:00Z"),
                                ZoneOffset.UTC
                        )
                );

        RunbookImportReport first = importer.importMissingOrChanged();
        RunbookImportReport second = importer.importMissingOrChanged();

        assertEquals(28, first.importedChunks());
        assertEquals("developer_api", first.providerTransport());
        assertEquals(0, first.skippedChunks());
        assertEquals(0, second.importedChunks());
        assertEquals(28, second.skippedChunks());
        assertEquals(28, embeddings.inputs.size());
        assertEquals(28, store.upserted.size());
        assertEquals(28, store.synchronizedMetadata.size());
        assertFalse(store.upserted.contains("nordly-evidence-legacy-refund-window"));
        assertFalse(store.upserted.contains("nordly-evidence-untrusted-instruction"));
        assertFalse(store.upserted.contains(
                "nordly-evidence-restricted-compensation"
        ));
    }

    private static final class CountingEmbeddings implements EmbeddingGateway {
        private final List<String> inputs = new ArrayList<>();

        @Override
        public EmbeddingResult embedQuery(String query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EmbeddingResult embedDocument(String title, String text) {
            inputs.add(title + "\n" + text);
            return new EmbeddingResult(
                    Collections.nCopies(768, 0.1f),
                    title.length() + text.length(),
                    10,
                    2.0,
                    1
            );
        }
    }

    private static final class FakeStore implements RunbookVectorStore {
        private final Set<String> current = new HashSet<>();
        private final List<String> upserted = new ArrayList<>();
        private final List<String> synchronizedMetadata = new ArrayList<>();

        @Override
        public boolean containsCurrent(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile
        ) {
            return current.contains(entry.evidenceId());
        }

        @Override
        public void synchronizeMetadata(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile
        ) {
            synchronizedMetadata.add(entry.evidenceId());
        }

        @Override
        public void upsert(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile,
                EmbeddingResult embedding
        ) {
            current.add(entry.evidenceId());
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
            return current.size();
        }
    }
}
