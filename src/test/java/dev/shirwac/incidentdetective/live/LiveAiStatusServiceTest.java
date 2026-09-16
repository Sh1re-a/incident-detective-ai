package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.adk.AdkProperties;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.incidentlab.replay.IncidentLabReplayAvailabilityResponse;
import dev.shirwac.incidentdetective.incidentlab.replay.IncidentLabReplayResponse;
import dev.shirwac.incidentdetective.incidentlab.replay.IncidentLabReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveAiStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Instant RESET = Instant.parse("2026-09-16T00:00:00Z");

    @Test
    void reportsAvailableWithoutProbingTheProvider() {
        Fixture fixture = fixture(true, true);
        when(fixture.guard.budgetSnapshot()).thenReturn(snapshot(0, 0));

        LiveAiStatusResponse response = fixture.service.describe();

        assertEquals(LiveAiStatusResponse.LiveState.AVAILABLE,
                response.liveState());
        assertEquals("available_by_configuration_and_budget",
                response.reasonCode());
        assertEquals(RESET, response.resetsAt());
        assertNull(response.retryAfterSeconds());
        assertTrue(response.replayAvailable());
        assertTrue(response.dailyCostGuardActive());
        assertEquals(GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL,
                response.quotaScope());
    }

    @Test
    void requiresRoomForThePlanAndAgentRunTogether() {
        Fixture fixture = fixture(true, false);
        when(fixture.guard.budgetSnapshot()).thenReturn(snapshot(19, 180_000));

        LiveAiStatusResponse response = fixture.service.describe();

        assertEquals(LiveAiStatusResponse.LiveState.DAILY_BUDGET_EXHAUSTED,
                response.liveState());
        assertEquals("daily_ai_allowance_exhausted", response.reasonCode());
        assertEquals(43_200, response.retryAfterSeconds());
        assertFalse(response.replayAvailable());
    }

    @Test
    void disabledConfigurationDoesNotReadOrConsumeTheBudget() {
        Fixture fixture = fixture(false, false);

        LiveAiStatusResponse response = fixture.service.describe();

        assertEquals(LiveAiStatusResponse.LiveState.DISABLED,
                response.liveState());
        assertEquals("live_ai_disabled", response.reasonCode());
        assertNull(response.resetsAt());
        verify(fixture.guard, never()).budgetSnapshot();
    }

    @Test
    void unavailableAgentCostProfileStopsBeforeReadingTheBudget() {
        Fixture fixture = fixture(true, false);
        when(fixture.guard.hasApprovedCostProfile(
                LiveAiOperation.ADK_TURN
        )).thenReturn(false);

        LiveAiStatusResponse response = fixture.service.describe();

        assertEquals(LiveAiStatusResponse.LiveState.NOT_CONFIGURED,
                response.liveState());
        assertEquals("cost_profile_unavailable", response.reasonCode());
        verify(fixture.guard, never()).budgetSnapshot();
    }

    private Fixture fixture(boolean liveEnabled, boolean replayAvailable) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"rag"});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{"replay"});
        LiveAiRunGuard guard = mock(LiveAiRunGuard.class);
        when(guard.hasApprovedCostProfile(
                LiveAiOperation.INCIDENT_PLAN
        )).thenReturn(true);
        when(guard.hasApprovedCostProfile(
                LiveAiOperation.ADK_TURN
        )).thenReturn(true);
        when(guard.completeIncidentLabAllowanceMicroUsd()).thenReturn(25_000L);
        when(guard.quotaScope()).thenReturn(
                GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL
        );
        IncidentLabReplayService replay = mock(IncidentLabReplayService.class);
        when(replay.availability()).thenReturn(
                new IncidentLabReplayAvailabilityResponse(
                        IncidentLabReplayAvailabilityResponse.CONTRACT_VERSION,
                        replayAvailable,
                        IncidentLabReplayResponse.CONTRACT_VERSION,
                        "sv",
                        "en",
                        replayAvailable ? "ready" : "golden_recording_not_captured"
                )
        );
        GeminiAiProperties ai = new GeminiAiProperties(
                "test-key",
                liveEnabled,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );
        LiveAiStatusService service = new LiveAiStatusService(
                ai,
                new AdkProperties(true),
                environment,
                guard,
                replay,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, guard);
    }

    private GlobalDailyLiveQuota.Snapshot snapshot(
            int consumedStarts,
            long consumedMicroUsd
    ) {
        return new GlobalDailyLiveQuota.Snapshot(
                consumedStarts,
                20,
                consumedMicroUsd,
                200_000,
                RESET
        );
    }

    private record Fixture(
            LiveAiStatusService service,
            LiveAiRunGuard guard
    ) {
    }
}
