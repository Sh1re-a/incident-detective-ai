package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.generated.GeneratedCaseReceipt;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record IncidentLabRunResponse(
        String contractVersion,
        String outcome,
        String delivery,
        String truthLabel,
        IncidentPlan plan,
        GeneratedCaseReceipt generationReceipt,
        Scenario scenario,
        List<LogEvidence> backendLogs,
        @Schema(nullable = true)
        SignalAlarmReceipt alarmReceipt,
        @Schema(nullable = true)
        AdkAgentTurnResponse agentTurn,
        List<String> limitations
) {

    public static final String CONTRACT_VERSION = "incident-lab-run-v2";
    public static final String DELIVERY = "synchronous_post_run";
    public static final String TRUTH_LABEL =
            "Backend-generated synthetic telemetry — deterministic Java alarm; Google ADK runs only when triggered.";

    public IncidentLabRunResponse {
        backendLogs = backendLogs == null ? List.of() : List.copyOf(backendLogs);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }
}
