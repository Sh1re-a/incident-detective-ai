package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import dev.shirwac.incidentdetective.replay.RunMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LiveInvestigationResult(
        String runId,
        String scenarioId,
        RunMode mode,
        String truthLabel,
        LiveRunStatus status,
        Instant startedAt,
        Instant completedAt,
        long latencyMs,
        Scenario scenario,
        List<LiveToolEvent> toolEvents,
        Diagnosis diagnosis,
        VerificationReport verification,
        ReplayComparison comparison,
        String modelId,
        String promptVersion,
        List<ModelCallMetadata> modelCalls,
        ModelTokenUsage tokenUsage,
        BigDecimal estimatedCostUsd,
        String estimatedCostBasis,
        int toolCallCount,
        int modelCallCount,
        List<String> limitations
) {
    public LiveInvestigationResult {
        toolEvents = List.copyOf(toolEvents);
        modelCalls = List.copyOf(modelCalls);
        limitations = List.copyOf(limitations);
    }
}
