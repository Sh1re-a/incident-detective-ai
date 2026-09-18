package dev.shirwac.incidentdetective.live;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class LiveAiBudgetMigrationIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.6-pg17-bookworm"
    );

    @Test
    void backfillsExistingStartsWithTheLargestOperationAllowance()
            throws Exception {
        flywayAt(MigrationVersion.fromVersion("6")).migrate();
        LocalDate day = LocalDate.of(2026, 9, 15);
        try (Connection connection = connection();
             PreparedStatement insert = connection.prepareStatement("""
                     INSERT INTO global_live_daily_quota (
                         quota_day,
                         consumed_starts
                     ) VALUES (?, ?)
                     """)) {
            insert.setObject(1, day);
            insert.setInt(2, 3);
            insert.executeUpdate();
        }

        flywayAt(null).migrate();

        try (Connection connection = connection();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT consumed_micro_usd
                     FROM global_live_daily_quota
                     WHERE quota_day = ?
                     """)) {
            select.setObject(1, day);
            try (ResultSet result = select.executeQuery()) {
                result.next();
                assertEquals(75_000L, result.getLong(1));
            }
        }
    }

    private Flyway flywayAt(MigrationVersion target) {
        var configuration = Flyway.configure().dataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
    }
}
