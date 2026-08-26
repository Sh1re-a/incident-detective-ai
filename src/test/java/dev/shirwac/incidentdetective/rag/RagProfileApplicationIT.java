package dev.shirwac.incidentdetective.rag;

import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalStrategy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rag")
@Testcontainers(disabledWithoutDocker = true)
class RagProfileApplicationIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.6-pg17-bookworm"
    );

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("incident-detective.rag.database.url", POSTGRES::getJdbcUrl);
        registry.add("incident-detective.rag.database.username", POSTGRES::getUsername);
        registry.add("incident-detective.rag.database.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Autowired
    private RunbookRetrievalStrategy retrieval;

    @Test
    void startsOnlyTheExplicitRagStackAndMigratesPgvector() {
        assertInstanceOf(PgvectorRunbookRetrievalStrategy.class, retrieval);
        JdbcClient jdbc = JdbcClient.create(dataSource);
        assertEquals("0.8.6", jdbc.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class)
                .single());
        assertEquals("4", flyway.info().current().getVersion().getVersion());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM runbook_embeddings")
                .query(Long.class)
                .single());
    }
}
