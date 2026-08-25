package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecordedReplayResult(
        String runId,
        String scenarioId,
        RunMode mode,
        String truthLabel,
        ReplayRunStatus status,
        Instant startedAt,
        Instant completedAt,
        long latencyMs,
        Scenario scenario,
        List<RecordedToolResult> toolEvents,
        Diagnosis diagnosis,
        VerificationReport verification,
        ReplayComparison comparison,
        String modelId,
        String promptVersion,
        ModelTokenUsage tokenUsage,
        BigDecimal estimatedCostUsd
) {
    public RecordedReplayResult {
        toolEvents = List.copyOf(toolEvents);
    }
}
