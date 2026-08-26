package dev.shirwac.incidentdetective.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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
            0.0
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
