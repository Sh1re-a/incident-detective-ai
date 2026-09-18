package dev.shirwac.incidentdetective.live;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class JdbcGlobalDailyLiveQuotaIT {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "pgvector/pgvector:0.8.6-pg17-bookworm"
    );

    private static JdbcClient jdbc;
    private static JdbcGlobalDailyLiveQuota quota;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );
        jdbc = JdbcClient.create(dataSource);
        quota = new JdbcGlobalDailyLiveQuota(
                jdbc,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @BeforeEach
    void clearQuota() {
        jdbc.sql("TRUNCATE TABLE global_live_daily_quota").update();
    }

    @Test
    void rejectsAnAllowanceThatWouldExceedTheBudgetWithoutIncrementing() {
        GlobalDailyLiveQuota.Decision admitted = quota.tryConsume(
                20,
                25_000,
                20_000
        );
        GlobalDailyLiveQuota.Decision rejected = quota.tryConsume(
                20,
                25_000,
                10_000
        );
        GlobalDailyLiveQuota.Snapshot snapshot = quota.snapshot(20, 25_000);

        assertTrue(admitted.allowed());
        assertFalse(rejected.allowed());
        assertEquals(1, rejected.consumed());
        assertEquals(20_000, rejected.consumedMicroUsd());
        assertEquals(1, snapshot.consumed());
        assertEquals(20_000, snapshot.consumedMicroUsd());
        assertEquals(
                Instant.parse("2026-09-17T00:00:00Z"),
                snapshot.resetsAt()
        );
        assertEquals(GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL, quota.scope());
    }

    @Test
    void grantsExactlyOneConcurrentClaimOnTheFinalBudgetSlot()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<GlobalDailyLiveQuota.Decision>> decisions = List.of(
                    executor.submit(() -> consumeTogether(ready, start)),
                    executor.submit(() -> consumeTogether(ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<GlobalDailyLiveQuota.Decision> results = decisions.stream()
                    .map(this::await)
                    .toList();
            GlobalDailyLiveQuota.Snapshot snapshot = quota.snapshot(1, 20_000);

            assertEquals(1, results.stream()
                    .filter(GlobalDailyLiveQuota.Decision::allowed)
                    .count());
            assertTrue(results.stream().allMatch(result ->
                    result.consumed() == 1
                            && result.consumedMicroUsd() == 20_000
            ));
            assertEquals(1, snapshot.consumed());
            assertEquals(20_000, snapshot.consumedMicroUsd());
            assertEquals(0, snapshot.remaining());
            assertEquals(0, snapshot.remainingMicroUsd());
        } finally {
            executor.shutdownNow();
        }
    }

    private GlobalDailyLiveQuota.Decision consumeTogether(
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return quota.tryConsume(1, 20_000, 20_000);
    }

    private GlobalDailyLiveQuota.Decision await(
            Future<GlobalDailyLiveQuota.Decision> future
    ) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent quota claim failed", exception);
        }
    }
}
