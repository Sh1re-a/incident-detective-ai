package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecordedReplayResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String scenarioId,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "recorded_replay",
                example = "recorded_replay"
        )
        RunMode mode,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Simulated incident — recorded deterministic replay."
        )
        String truthLabel,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "completed",
                example = "completed"
        )
        ReplayRunStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant completedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        long latencyMs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Scenario scenario,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<RecordedToolResult> toolEvents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Diagnosis diagnosis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        VerificationReport verification,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ReplayComparison comparison,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = "null",
                description = "Always null because replay makes no model call."
        )
        String modelId,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = "null",
                description = "Always null because replay uses no model prompt."
        )
        String promptVersion,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = "null",
                description = "Always null because replay uses no model tokens."
        )
        ModelTokenUsage tokenUsage,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                types = "null",
                description = "Always null because replay has no model API cost."
        )
        BigDecimal estimatedCostUsd
) {
    public RecordedReplayResult {
        toolEvents = List.copyOf(toolEvents);
    }
}
