package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelCostBreakdown;
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
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Released diagnosis, or null when deterministic "
                        + "verification fails."
        )
        Diagnosis diagnosis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        VerificationReport verification,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Always null for live runs because expected-answer "
                        + "ground truth is private."
        )
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
                description = "Model-generation-only paid Standard list-price "
                        + "estimate in USD, calculated from provider-reported token "
                        + "metadata. Not an invoice or actual charged amount. Null "
                        + "when the model price or core usage is unavailable."
        )
        BigDecimal estimatedCostUsd,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Null when the model price or core provider usage is unavailable."
        )
        ModelCostBreakdown modelCostBreakdown,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Human-readable pricing snapshot and assumptions. "
                        + "The frontend must not present the estimate as an invoice."
        )
        String estimatedCostBasis,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        int toolCallCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        int modelCallCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> limitations
) {
    public LiveInvestigationResult {
        if (status == LiveRunStatus.COMPLETED && diagnosis == null) {
            throw new IllegalArgumentException(
                    "a completed live run must release a diagnosis"
            );
        }
        if (status == LiveRunStatus.VERIFICATION_FAILED && diagnosis != null) {
            throw new IllegalArgumentException(
                    "a failed live verification must not release a diagnosis"
            );
        }
        if (comparison != null) {
            throw new IllegalArgumentException(
                    "a live run must not expose expected-answer comparison"
            );
        }
        toolEvents = List.copyOf(toolEvents);
        modelCalls = List.copyOf(modelCalls);
        limitations = List.copyOf(limitations);
    }
}
