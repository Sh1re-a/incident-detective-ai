package dev.shirwac.incidentdetective.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import jakarta.validation.Validation;
import dev.shirwac.incidentdetective.nordly.NordlyKnowledgeCorpus;
import dev.shirwac.incidentdetective.nordly.NordlyKnowledgeCorpusImporter;
import dev.shirwac.incidentdetective.nordly.NordlyKnowledgeIndexReadiness;
import dev.shirwac.incidentdetective.nordly.NordlyResourceCatalog;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRunbookVectorStoreIT {

    private static final String IMAGE = "pgvector/pgvector:0.8.6-pg17-bookworm";
    private static final String CORPUS_VERSION = "test-corpus-v1";
    private static final RagProperties PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.0,
            GoogleGenAiProvider.DEVELOPER_API
    );

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE)
            .withDatabaseName("incident_detective_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static JdbcRunbookVectorStore store;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);
        Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate();
        store = new JdbcRunbookVectorStore(JdbcClient.create(dataSource));
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void clearRows() {
        JdbcClient.create(dataSource)
                .sql("TRUNCATE TABLE runbook_embeddings")
                .update();
    }

    @Test
    void migratesPgvectorAndRanksWithExactCosineSearch() {
        RunbookCorpusEntry payment = entry(
                "payment-timeout",
                "Payment timeout",
                "Check client timeout precedence."
        );
        RunbookCorpusEntry contract = entry(
                "contract-change",
                "Contract compatibility",
                "Check response schema compatibility."
        );
        store.upsert(
                CORPUS_VERSION,
                payment,
                PROFILE,
                new EmbeddingResult(unitVector(0), 20, null, null, 5)
        );
        store.upsert(CORPUS_VERSION, contract, PROFILE, embedding(unitVector(1)));

        List<RunbookSearchHit> hits = store.search(
                CORPUS_VERSION,
                PROFILE,
                unitVector(0),
                4,
                0.5
        );

        assertEquals(1, hits.size());
        assertEquals("payment-timeout", hits.getFirst().entry().evidenceId());
        assertEquals(1.0, hits.getFirst().cosineSimilarity(), 0.000_001);
        assertEquals(List.of("doc-contract-change", "doc-payment-timeout"),
                store.documentIds(CORPUS_VERSION, PROFILE));
    }

    @Test
    void upsertIsIdempotentAndRefreshesChangedContent() {
        RunbookCorpusEntry first = entry(
                "payment-timeout",
                "Payment timeout",
                "Old guidance."
        );
        RunbookCorpusEntry updated = entry(
                "payment-timeout",
                "Payment timeout",
                "New reviewed guidance."
        );

        store.upsert(CORPUS_VERSION, first, PROFILE, embedding(unitVector(0)));
        assertTrue(store.containsCurrent(CORPUS_VERSION, first, PROFILE));
        assertFalse(store.containsCurrent(CORPUS_VERSION, updated, PROFILE));

        store.upsert(CORPUS_VERSION, updated, PROFILE, embedding(unitVector(0)));

        assertEquals(1, store.count(CORPUS_VERSION, PROFILE));
        assertTrue(store.containsCurrent(CORPUS_VERSION, updated, PROFILE));
        assertEquals(
                "New reviewed guidance.",
                store.search(CORPUS_VERSION, PROFILE, unitVector(0), 1, -1)
                        .getFirst().entry().text()
        );
    }

    @Test
    void neverTreatsDeveloperApiVectorsAsCurrentVertexVectors() {
        RagProperties vertexProfile = new RagProperties(
                "gemini-embedding-2",
                768,
                "search-result-v1",
                0.0,
                GoogleGenAiProvider.VERTEX_AI
        );
        RunbookCorpusEntry entry = entry(
                "provider-bound-vector",
                "Provider-bound vector",
                "The same model ID can still use a different provider transport."
        );

        store.upsert(CORPUS_VERSION, entry, PROFILE, embedding(unitVector(0)));

        assertTrue(store.containsCurrent(CORPUS_VERSION, entry, PROFILE));
        assertFalse(store.containsCurrent(
                CORPUS_VERSION,
                entry,
                vertexProfile
        ));
        assertEquals(0, store.count(CORPUS_VERSION, vertexProfile));

        store.upsert(
                CORPUS_VERSION,
                entry,
                vertexProfile,
                embedding(unitVector(1))
        );

        assertEquals(1, store.count(CORPUS_VERSION, PROFILE));
        assertEquals(1, store.count(CORPUS_VERSION, vertexProfile));
        assertEquals(2L, JdbcClient.create(dataSource)
                .sql("SELECT COUNT(*) FROM runbook_embeddings")
                .query(Long.class)
                .single());
    }

    @Test
    void importsTheVersionedCorpusAndReportsEveryChunkCurrentWithoutAProvider() {
        ClasspathRunbookCorpus corpus = new ClasspathRunbookCorpus(
                JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build(),
                Validation.buildDefaultValidatorFactory().getValidator()
        );
        EmbeddingGateway deterministicEmbeddings = new EmbeddingGateway() {
            @Override
            public EmbeddingResult embedQuery(String query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EmbeddingResult embedDocument(String title, String text) {
                int activeIndex = Math.floorMod(title.hashCode(), 768);
                return new EmbeddingResult(
                        unitVector(activeIndex),
                        title.length() + text.length(),
                        null,
                        null,
                        0
                );
            }
        };
        RunbookCorpusImporter importer = new RunbookCorpusImporter(
                corpus,
                store,
                deterministicEmbeddings,
                PROFILE,
                Clock.fixed(Instant.parse("2026-09-03T08:00:00Z"), ZoneOffset.UTC)
        );

        RunbookImportReport first = importer.importMissingOrChanged();
        RunbookImportReport second = importer.importMissingOrChanged();
        RunbookIndexStatus status = new RunbookIndexReadiness(
                corpus,
                store,
                PROFILE
        ).inspect();

        assertEquals(12, first.importedChunks());
        assertEquals("developer_api", first.providerTransport());
        assertEquals(0, first.skippedChunks());
        assertEquals(0, second.importedChunks());
        assertEquals(12, second.skippedChunks());
        assertTrue(status.ready());
        assertEquals(12, status.indexedChunks());
        assertEquals(12, status.currentChunks());
        assertEquals(12, status.expectedChunks());
    }

    @Test
    void importsNordlyIntoASeparateVersionWithApprovedDocumentsOnly() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        NordlyKnowledgeCorpus corpus = new NordlyKnowledgeCorpus(
                new NordlyResourceCatalog(mapper)
        );
        EmbeddingGateway deterministicEmbeddings = new EmbeddingGateway() {
            @Override
            public EmbeddingResult embedQuery(String query) {
                return embedding(unitVector(0));
            }

            @Override
            public EmbeddingResult embedDocument(String title, String text) {
                return embedding(unitVector(Math.floorMod(title.hashCode(), 768)));
            }
        };
        NordlyKnowledgeCorpusImporter importer =
                new NordlyKnowledgeCorpusImporter(
                        corpus,
                        store,
                        deterministicEmbeddings,
                        PROFILE,
                        Clock.fixed(
                                Instant.parse("2026-09-03T08:00:00Z"),
                                ZoneOffset.UTC
                        )
                );

        RunbookImportReport first = importer.importMissingOrChanged();
        RunbookImportReport second = importer.importMissingOrChanged();
        RunbookIndexStatus status = new NordlyKnowledgeIndexReadiness(
                corpus,
                store,
                PROFILE
        ).inspect();

        assertEquals("nordly-knowledge-corpus-v2", first.corpusVersion());
        assertEquals("developer_api", first.providerTransport());
        assertEquals(27, first.importedChunks());
        assertEquals(0, second.importedChunks());
        assertEquals(27, second.skippedChunks());
        assertTrue(status.ready());
        assertEquals(27, store.count(corpus.version(), PROFILE));
        assertFalse(store.documentIds(corpus.version(), PROFILE)
                .contains("kb-legacy-refund-playbook"));
        assertFalse(store.documentIds(corpus.version(), PROFILE)
                .contains("kb-untrusted-shortcuts"));
        assertFalse(store.documentIds(corpus.version(), PROFILE)
                .contains("kb-employee-compensation-register"));
        assertEquals(0, store.count("runbook-corpus-v1", PROFILE));
    }

    private static RunbookCorpusEntry entry(
            String evidenceId,
            String title,
            String text
    ) {
        return new RunbookCorpusEntry(
                evidenceId,
                "doc-" + evidenceId,
                "1.0",
                "chunk-1",
                title,
                title + " summary",
                "runbooks/doc-" + evidenceId + "#chunk-1",
                text
        );
    }

    private static EmbeddingResult embedding(List<Float> values) {
        return new EmbeddingResult(values, 20, 10, 3.0, 5);
    }

    private static List<Float> unitVector(int activeIndex) {
        List<Float> values = new ArrayList<>(java.util.Collections.nCopies(768, 0.0f));
        values.set(activeIndex, 1.0f);
        return List.copyOf(values);
    }
}
