package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.investigation.tools.ToolName;

import java.time.Duration;
import java.util.Map;

/**
 * Read-only projection of the limits enforced by {@link LiveInvestigationService}.
 */
public record LiveInvestigationLimits(
        int maxCollectionRounds,
        int maxToolCallsTotal,
        int maxToolCallsPerRound,
        Map<ToolName, Integer> maxCallsByTool,
        Duration hardDeadline,
        Duration providerCallCap,
        int dailyLiveRunLimit
) {
    public LiveInvestigationLimits {
        maxCallsByTool = Map.copyOf(maxCallsByTool);
    }

    public static LiveInvestigationLimits current() {
        return new LiveInvestigationLimits(
                LiveInvestigationService.MAX_COLLECTION_ROUNDS,
                LiveInvestigationService.MAX_TOOL_CALLS_TOTAL,
                LiveInvestigationService.MAX_TOOL_CALLS_PER_ROUND,
                Map.of(
                        ToolName.GET_METRICS,
                        LiveInvestigationService.MAX_METRIC_CALLS,
                        ToolName.SEARCH_LOGS,
                        LiveInvestigationService.MAX_TOOL_CALLS_PER_TYPE,
                        ToolName.GET_TRACE,
                        LiveInvestigationService.MAX_TOOL_CALLS_PER_TYPE,
                        ToolName.RETRIEVE_RUNBOOKS,
                        LiveInvestigationService.MAX_RUNBOOK_CALLS
                ),
                LiveInvestigationService.HARD_DEADLINE,
                LiveInvestigationService.PROVIDER_CALL_CAP,
                LiveInvestigationService.DAILY_LIVE_RUN_LIMIT
        );
    }
}
