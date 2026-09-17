package dev.shirwac.incidentdetective.incidentlab.followup;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class IncidentRunSnapshotRegistryIT {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.6-pg17-bookworm"
    ).withDatabaseName("incident_followup_test")
            .withUsername("test")
            .withPassword("test");

    private static HikariDataSource dataSource;
    private static IncidentRunSnapshotRegistry registry;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).load().migrate();
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        registry = new IncidentRunSnapshotRegistry(
                JdbcClient.create(dataSource),
                mapper,
                Clock.fixed(
                        Instant.parse("2026-09-17T10:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @AfterAll
    static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void migrationPersistsCanonicalJsonbAndOpaqueIdempotentReference() {
        IncidentFollowUpSnapshot snapshot = snapshot();

        String reference = registry.register(snapshot);
        IncidentRunSnapshotRegistry.StoredSnapshot stored = registry
                .find(reference)
                .orElseThrow();

        assertTrue(reference.matches("^ilr_[a-f0-9]{32}$"));
        assertEquals(snapshot, stored.snapshot());
        assertEquals(64, stored.snapshotSha256().length());
        String fingerprint = "f".repeat(64);
        assertTrue(registry.reserveTurn(reference, "turn_12345678", fingerprint));
        assertFalse(registry.reserveTurn(reference, "turn_12345678", fingerprint));
        IncidentFollowUpResponse response = response(reference);
        registry.completeTurn(reference, "turn_12345678", response);
        assertEquals(
                response,
                registry.cachedTurn(
                        reference,
                        "turn_12345678",
                        fingerprint
                ).orElseThrow()
        );
    }

    private IncidentFollowUpSnapshot snapshot() {
        return new IncidentFollowUpSnapshot(
                "scenario-db-test",
                IncidentFollowUpResponse.Mode.LIVE_AI,
                IncidentLabRunResponse.AnswerState.DIAGNOSED,
                "CATALOG_SERVICE",
                report("Verifierad orsak."),
                report("Verified cause."),
                List.of(new IncidentFollowUpSnapshot.Source(
                        "log-db-1",
                        "synthetic/log-db-1",
                        "log",
                        "Cache mismatch",
                        "Cache mismatch",
                        IncidentFollowUpResponse.TargetScene.LOGS,
                        "log-db-1"
                )),
                List.of(new IncidentFollowUpSnapshot.VerifiedFact(
                        "root_cause",
                        "CATALOG_CACHE_INVALIDATION_FAILURE",
                        List.of("log-db-1")
                )),
                new IncidentFollowUpSnapshot.Boundary(
                        5, false, false, true, true
                )
        );
    }

    private IncidentFollowUpSnapshot.LocalizedReport report(String cause) {
        return new IncidentFollowUpSnapshot.LocalizedReport(
                "Verifierad incident",
                "Berörd tjänst: Catalog service.",
                cause,
                "Kunder såg gammal data.",
                List.of("Larmet är verifierat."),
                List.of("Produktion är inte undersökt."),
                "Verifierad",
                "Ingen åtgärd utfördes."
        );
    }

    private IncidentFollowUpResponse response(String runReference) {
        return new IncidentFollowUpResponse(
                IncidentFollowUpResponse.CONTRACT_VERSION,
                runReference,
                "turn_12345678",
                IncidentFollowUpResponse.Mode.LIVE_AI,
                "synchronous_frozen_snapshot",
                IncidentFollowUpResponse.AnswerState.ANSWERED,
                new IncidentFollowUpResponse.Answer(
                        "Katalogtjänsten hade en verifierad cacheavvikelse.",
                        new IncidentFollowUpResponse.ProblemLocation(
                                "CATALOG_SERVICE",
                                "Berörd tjänst: Katalogtjänsten.",
                                "Verifierad"
                        ),
                        new IncidentFollowUpResponse.Finding(
                                "Cache-invalideringen misslyckades.",
                                "Verifierad"
                        ),
                        "Produktdata blev inaktuell.",
                        List.of("Avvikelsen är verifierad."),
                        List.of("Produktion är inte undersökt."),
                        "Ingen åtgärd utfördes."
                ),
                List.of(new IncidentFollowUpResponse.Claim(
                        IncidentFollowUpResponse.ClaimSection.CAUSE,
                        "Cache-invalideringen misslyckades.",
                        List.of("log-db-1")
                )),
                List.of(new IncidentFollowUpResponse.Citation(
                        "log-db-1",
                        "synthetic/log-db-1",
                        "log",
                        "Cache mismatch",
                        IncidentFollowUpResponse.TargetScene.LOGS,
                        "log-db-1"
                )),
                List.of(new IncidentFollowUpResponse.Step(
                        1,
                        "java_verify",
                        "completed",
                        "Verifierad.",
                        List.of("log-db-1")
                )),
                new IncidentFollowUpResponse.Verification(
                        "verified_from_frozen_receipt",
                        "a".repeat(64),
                        true,
                        true,
                        true,
                        false,
                        false
                ),
                new IncidentFollowUpResponse.Receipt(
                        1,
                        1,
                        0,
                        1,
                        true,
                        false,
                        false
                ),
                null,
                List.of(new IncidentFollowUpResponse.SuggestedQuestion(
                        "show_sources",
                        "Visa källorna"
                )),
                List.of("Endast den avslutade körningen.")
        );
    }
}
