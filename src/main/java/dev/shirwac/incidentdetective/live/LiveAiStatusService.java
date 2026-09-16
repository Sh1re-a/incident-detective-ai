package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.adk.AdkProperties;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.incidentlab.replay.IncidentLabReplayService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

@Service
public final class LiveAiStatusService {

    private final GeminiAiProperties ai;
    private final AdkProperties adk;
    private final Environment environment;
    private final LiveAiRunGuard liveAiRunGuard;
    private final IncidentLabReplayService replay;
    private final Clock clock;

    public LiveAiStatusService(
            GeminiAiProperties ai,
            AdkProperties adk,
            Environment environment,
            LiveAiRunGuard liveAiRunGuard,
            IncidentLabReplayService replay,
            Clock clock
    ) {
        this.ai = ai;
        this.adk = adk;
        this.environment = environment;
        this.liveAiRunGuard = liveAiRunGuard;
        this.replay = replay;
        this.clock = clock;
    }

    public LiveAiStatusResponse describe() {
        boolean replayAvailable = replay.availability().available();
        if (!hasProfile("rag")) {
            return unavailable(
                    LiveAiStatusResponse.LiveState.NOT_CONFIGURED,
                    "rag_profile_required",
                    replayAvailable
            );
        }
        if (!adk.enabled()) {
            return unavailable(
                    LiveAiStatusResponse.LiveState.NOT_CONFIGURED,
                    "adk_disabled",
                    replayAvailable
            );
        }
        if (!ai.liveEnabled()) {
            return unavailable(
                    LiveAiStatusResponse.LiveState.DISABLED,
                    "live_ai_disabled",
                    replayAvailable
            );
        }
        if (!ai.hasProviderConfiguration()) {
            return unavailable(
                    LiveAiStatusResponse.LiveState.NOT_CONFIGURED,
                    "provider_routing_not_configured",
                    replayAvailable
            );
        }
        if (!liveAiRunGuard.hasApprovedCostProfile(
                LiveAiOperation.INCIDENT_PLAN
        ) || !liveAiRunGuard.hasApprovedCostProfile(
                LiveAiOperation.ADK_TURN
        )) {
            return unavailable(
                    LiveAiStatusResponse.LiveState.NOT_CONFIGURED,
                    "cost_profile_unavailable",
                    replayAvailable
            );
        }

        GlobalDailyLiveQuota.Snapshot budget = liveAiRunGuard.budgetSnapshot();
        boolean completeJourneyFits = budget.remaining() >= 2
                && budget.remainingMicroUsd()
                >= liveAiRunGuard.completeIncidentLabAllowanceMicroUsd();
        if (!completeJourneyFits) {
            return new LiveAiStatusResponse(
                    LiveAiStatusResponse.CONTRACT_VERSION,
                    LiveAiStatusResponse.LiveState.DAILY_BUDGET_EXHAUSTED,
                    "daily_ai_allowance_exhausted",
                    budget.resetsAt(),
                    retryAfterSeconds(budget.resetsAt()),
                    replayAvailable,
                    true,
                    liveAiRunGuard.quotaScope()
            );
        }
        return new LiveAiStatusResponse(
                LiveAiStatusResponse.CONTRACT_VERSION,
                LiveAiStatusResponse.LiveState.AVAILABLE,
                "available_by_configuration_and_budget",
                budget.resetsAt(),
                null,
                replayAvailable,
                true,
                liveAiRunGuard.quotaScope()
        );
    }

    private LiveAiStatusResponse unavailable(
            LiveAiStatusResponse.LiveState state,
            String reasonCode,
            boolean replayAvailable
    ) {
        return new LiveAiStatusResponse(
                LiveAiStatusResponse.CONTRACT_VERSION,
                state,
                reasonCode,
                null,
                null,
                replayAvailable,
                true,
                liveAiRunGuard.quotaScope()
        );
    }

    private boolean hasProfile(String required) {
        return Stream.concat(
                        Arrays.stream(environment.getActiveProfiles()),
                        Arrays.stream(environment.getDefaultProfiles())
                )
                .anyMatch(required::equals);
    }

    private long retryAfterSeconds(Instant resetsAt) {
        return Math.max(
                1,
                Duration.between(clock.instant(), resetsAt).toSeconds()
        );
    }
}
