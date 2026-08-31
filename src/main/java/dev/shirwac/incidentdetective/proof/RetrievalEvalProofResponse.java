package dev.shirwac.incidentdetective.proof;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(
        name = "RetrievalEvalProofResponse",
        description = "Published aggregate proof from the frozen runbook retrieval eval. "
                + "Contains no queries, retrieved text, or case-level data."
)
public record RetrievalEvalProofResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summaryVersion,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "measured"
        )
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Provenance provenance,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RetrievalConfiguration retrieval,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SplitMetrics development,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SplitMetrics heldOut,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProviderUsage providerUsage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SafetyBoundary safetyBoundary
) {

    public record Provenance(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean historicalFrozenRun,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String suiteVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String suiteSha256,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String corpusVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String corpusContentSha256,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String gitSha,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Instant executedAt
    ) {
    }

    public record RetrievalConfiguration(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String backend,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String embeddingProvider,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String embeddingModel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int embeddingDimensions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String embeddingFormatVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int corpusDocumentCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int corpusChunkCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int topK,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal minimumSimilarity,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean thresholdCalibratedOnDevelopment,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean configuredThresholdMatchesCalibration
    ) {
    }

    public record SplitMetrics(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String split,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int positiveCases,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int positiveHits,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal hitAtK,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal meanReciprocalRank,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int noMatchCases,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int correctNoMatches,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal noMatchAccuracy,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal benignUnsafeTop1Rate
    ) {
    }

    public record ProviderUsage(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int embeddingCalls,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            int localInputCharacters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            Integer providerBillableCharacters,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            BigDecimal providerInputTokens,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean providerUsageMetadataComplete,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long providerCallLatencyMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long evaluationLatencyMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
            BigDecimal estimatedListPriceCostUsd,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String costStatus
    ) {
    }

    public record SafetyBoundary(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean adversarialSynthesisSafetyEvaluated,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status
    ) {
    }
}
