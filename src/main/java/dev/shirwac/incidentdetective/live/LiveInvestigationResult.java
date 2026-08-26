package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import dev.shirwac.incidentdetective.replay.RunMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record LiveInvestigationResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String scenarioId,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "live_ai",
                example = "live_ai"
        )
        RunMode mode,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "Simulated incident — real AI investigation."
        )
        String truthLabel,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"completed", "verification_failed"}
        )
        LiveRunStatus status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant completedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        long latencyMs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Scenario scenario,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<LiveToolEvent> toolEvents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Diagnosis diagnosis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        VerificationReport verification,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ReplayComparison comparison,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String modelId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String promptVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ModelCallMetadata> modelCalls,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Null when no model call returned usable provider token metadata."
        )
        ModelTokenUsage tokenUsage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PromptCacheTelemetry promptCache,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Null when no paid list-price estimate is configured."
        )
        BigDecimal estimatedCostUsd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String estimatedCostBasis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        int toolCallCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        int modelCallCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> limitations
) {
    public LiveInvestigationResult {
        toolEvents = List.copyOf(toolEvents);
        modelCalls = List.copyOf(modelCalls);
        limitations = List.copyOf(limitations);
    }
}
