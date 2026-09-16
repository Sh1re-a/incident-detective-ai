package dev.shirwac.incidentdetective.rag;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagDatabaseConfigurationTest {

    @Test
    void localDatabaseKeepsThePlainJdbcConfiguration() {
        RagDatabaseProperties properties = properties("  ");

        HikariConfig config = RagDatabaseConfiguration.hikariConfig(properties);

        assertFalse(properties.usesCloudSqlConnector());
        assertNull(properties.cloudSqlInstance());
        assertEquals("jdbc:postgresql://127.0.0.1:5433/incident_detective", config.getJdbcUrl());
        assertTrue(config.getDataSourceProperties().isEmpty());
    }

    @Test
    void cloudDatabaseUsesTheJavaConnectorWithLazyRefresh() {
        RagDatabaseProperties properties = properties(
                " shirwac-incident-detective:europe-west1:incident-detective-pg-staging "
        );

        assertDoesNotThrow(() -> Class.forName(
                "com.google.cloud.sql.postgres.SocketFactory"
        ));
        HikariConfig config = RagDatabaseConfiguration.hikariConfig(properties);
        Properties dataSource = config.getDataSourceProperties();

        assertTrue(properties.usesCloudSqlConnector());
        assertEquals(
                "shirwac-incident-detective:europe-west1:incident-detective-pg-staging",
                properties.cloudSqlInstance()
        );
        assertEquals(
                "com.google.cloud.sql.postgres.SocketFactory",
                dataSource.getProperty("socketFactory")
        );
        assertEquals(
                properties.cloudSqlInstance(),
                dataSource.getProperty("cloudSqlInstance")
        );
        assertEquals("PRIVATE", dataSource.getProperty("ipTypes"));
        assertEquals("lazy", dataSource.getProperty("cloudSqlRefreshStrategy"));
        assertEquals(4, config.getMaximumPoolSize());
        assertEquals(0, config.getMinimumIdle());
    }

    private RagDatabaseProperties properties(String cloudSqlInstance) {
        return new RagDatabaseProperties(
                "jdbc:postgresql://127.0.0.1:5433/incident_detective",
                "incident_detective",
                "test-only-password",
                4,
                3_000,
                cloudSqlInstance
        );
    }
}
