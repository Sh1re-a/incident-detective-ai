package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.rag.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveAiRunGuardTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-15T12:00:00Z"),
            ZoneOffset.UTC
    );

    @Test
    void consumesTheOperationsConservativeAllowanceBeforeRunning() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(CLOCK);
        LiveAiRunGuard guard = guard("gemini-3.1-flash-lite", quota, 25_000);

        String result = guard.runConfirmed(
                true,
                LiveAiOperation.ADK_TURN,
                () -> "completed"
        );

        assertEquals("completed", result);
        GlobalDailyLiveQuota.Snapshot snapshot = quota.snapshot(20, 25_000);
        assertEquals(1, snapshot.consumed());
        assertEquals(20_000, snapshot.consumedMicroUsd());
        assertEquals(5_000, snapshot.remainingMicroUsd());
    }

    @Test
    void exhaustedAllowanceStopsBeforeTheSuppliedAction() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(CLOCK);
        LiveAiRunGuard guard = guard("gemini-3.1-flash-lite", quota, 25_000);
        guard.runConfirmed(true, LiveAiOperation.ADK_TURN, () -> "first");
        AtomicBoolean called = new AtomicBoolean();

        assertThrows(
                LiveDailyQuotaExceededException.class,
                () -> guard.runConfirmed(
                        true,
                        LiveAiOperation.ADK_TURN,
                        () -> {
                            called.set(true);
                            return "must not run";
                        }
                )
        );

        assertFalse(called.get());
        assertEquals(1, quota.snapshot(20, 25_000).consumed());
    }

    @Test
    void failedProviderActionKeepsTheConservativeAllowance() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(CLOCK);
        LiveAiRunGuard guard = guard("gemini-3.1-flash-lite", quota, 25_000);

        assertThrows(
                IllegalStateException.class,
                () -> guard.runConfirmed(
                        true,
                        LiveAiOperation.INCIDENT_PLAN,
                        () -> {
                            throw new IllegalStateException("provider outcome unknown");
                        }
                )
        );

        GlobalDailyLiveQuota.Snapshot snapshot = quota.snapshot(20, 25_000);
        assertEquals(1, snapshot.consumed());
        assertEquals(5_000, snapshot.consumedMicroUsd());
    }

    @Test
    void unknownModelStopsBeforeBudgetOrAction() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(CLOCK);
        LiveAiRunGuard guard = guard("unpriced-model", quota, 25_000);
        AtomicBoolean called = new AtomicBoolean();

        LiveInvestigationException exception = assertThrows(
                LiveInvestigationException.class,
                () -> guard.runConfirmed(
                        true,
                        LiveAiOperation.INCIDENT_PLAN,
                        () -> {
                            called.set(true);
                            return "must not run";
                        }
                )
        );

        assertEquals(
                LiveInvestigationFailure.COST_PROFILE_MISSING,
                exception.failure()
        );
        assertFalse(called.get());
        assertEquals(0, quota.snapshot(20, 25_000).consumed());
        assertTrue(quota.snapshot(20, 25_000).canConsume(5_000));
    }

    @Test
    void unknownEmbeddingModelStopsEmbeddingBearingOperationOnly() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(CLOCK);
        LiveAiRunGuard guard = guard(
                "gemini-3.1-flash-lite",
                "unpriced-embedding-model",
                quota,
                25_000
        );
        AtomicBoolean called = new AtomicBoolean();

        String plan = guard.runConfirmed(
                true,
                LiveAiOperation.INCIDENT_PLAN,
                () -> "plan"
        );
        LiveInvestigationException exception = assertThrows(
                LiveInvestigationException.class,
                () -> guard.runConfirmed(
                        true,
                        LiveAiOperation.ADK_TURN,
                        () -> {
                            called.set(true);
                            return "must not run";
                        }
                )
        );

        assertEquals("plan", plan);
        assertEquals(
                LiveInvestigationFailure.COST_PROFILE_MISSING,
                exception.failure()
        );
        assertFalse(called.get());
        assertEquals(1, quota.snapshot(20, 25_000).consumed());
        assertEquals(5_000, quota.snapshot(20, 25_000).consumedMicroUsd());
    }

    @Test
    void unavailableBudgetStoreStopsBeforeTheProviderAction() {
        GlobalDailyLiveQuota quota = mock(GlobalDailyLiveQuota.class);
        when(quota.tryConsume(anyInt(), anyLong(), anyLong())).thenThrow(
                new DataAccessResourceFailureException("database unavailable")
        );
        LiveAiRunGuard guard = guard(
                "gemini-3.1-flash-lite",
                quota,
                25_000
        );
        AtomicBoolean called = new AtomicBoolean();

        assertThrows(
                LiveAiBudgetStoreUnavailableException.class,
                () -> guard.runConfirmed(
                        true,
                        LiveAiOperation.INCIDENT_PLAN,
                        () -> {
                            called.set(true);
                            return "must not run";
                        }
                )
        );

        assertFalse(called.get());
    }

    @Test
    void unavailableBudgetStoreAlsoStopsTheStatusSnapshot() {
        GlobalDailyLiveQuota quota = mock(GlobalDailyLiveQuota.class);
        when(quota.snapshot(anyInt(), anyLong())).thenThrow(
                new DataAccessResourceFailureException("database unavailable")
        );
        LiveAiRunGuard guard = guard(
                "gemini-3.1-flash-lite",
                quota,
                25_000
        );

        assertThrows(
                LiveAiBudgetStoreUnavailableException.class,
                guard::budgetSnapshot
        );
    }

    private LiveAiRunGuard guard(
            String modelId,
            GlobalDailyLiveQuota quota,
            long dailyBudgetMicroUsd
    ) {
        return guard(
                modelId,
                "gemini-embedding-2",
                quota,
                dailyBudgetMicroUsd
        );
    }

    private LiveAiRunGuard guard(
            String modelId,
            String embeddingModel,
            GlobalDailyLiveQuota quota,
            long dailyBudgetMicroUsd
    ) {
        GeminiAiProperties ai = new GeminiAiProperties(
                "test-key",
                true,
                modelId,
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );
        LiveAiBudgetProperties budget = new LiveAiBudgetProperties(
                dailyBudgetMicroUsd,
                LiveAiBudgetProperties.DEFAULT_PRICE_PROFILE_VERSION
        );
        RagProperties rag = new RagProperties(
                embeddingModel,
                768,
                "search-result-v1",
                0.6,
                GoogleGenAiProvider.DEVELOPER_API
        );
        return new LiveAiRunGuard(
                ai,
                rag,
                budget,
                new LiveInvestigationAdmissionGuard(CLOCK),
                quota
        );
    }
}
