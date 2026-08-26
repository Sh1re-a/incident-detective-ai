package dev.shirwac.incidentdetective.rag.eval;

import java.time.Instant;
import java.util.List;

public record RunbookRetrievalEvalReport(
        String suiteVersion,
        String suiteSha256,
        String corpusVersion,
        String corpusContentSha256,
        String gitSha,
        Instant executedAt,
        String backend,
        EmbeddingProfile embeddingProfile,
        int topK,
        double configuredMinimumSimilarity,
        Calibration calibration,
        boolean configuredThresholdMatchesCalibration,
        SplitMetrics development,
        SplitMetrics heldOut,
        EmbeddingUsage embeddingUsage,
        CostEstimate costEstimate,
        boolean adversarialSynthesisSafetyEvaluated,
        String adversarialSafetyStatus,
        long evaluationLatencyMs,
        List<CaseResult> cases
) {
    public RunbookRetrievalEvalReport {
        cases = List.copyOf(cases);
    }

    public record EmbeddingProfile(
            String provider,
            String model,
            int dimensions,
            String formatVersion,
            String queryInputFormat,
            String documentInputFormat
    ) {
    }

    public record Calibration(
            String sourceSplit,
            String objective,
            String tieBreak,
            double frozenThreshold,
            double developmentObjective,
            double developmentHitAtK,
            double developmentMeanReciprocalRank,
            double developmentNoMatchAccuracy,
            int candidateCount
    ) {
    }

    public record SplitMetrics(
            String split,
            int positiveCases,
            int positiveHits,
            double hitAtK,
            double meanReciprocalRank,
            int noMatchCases,
            int correctNoMatches,
            double noMatchAccuracy,
            double benignUnsafeTop1Rate
    ) {
    }

    public record EmbeddingUsage(
            int calls,
            long localInputCharacters,
            Integer providerBillableCharacters,
            Double providerInputTokens,
            boolean providerUsageMetadataComplete,
            long providerCallLatencyMs
    ) {
    }

    public record CostEstimate(Double usd, String status) {
    }

    public record CaseResult(
            String caseId,
            String split,
            String caseType,
            String scenarioId,
            List<String> relevantEvidenceIds,
            boolean expectedEmpty,
            boolean retrievalExpectationMet,
            Integer firstRelevantRank,
            double reciprocalRank,
            int acceptedCount,
            int localInputCharacters,
            Integer providerBillableCharacters,
            Double providerInputTokens,
            long embeddingLatencyMs,
            long retrievalLatencyMs,
            String safetyFollowUp,
            List<Hit> hits
    ) {
        public CaseResult {
            relevantEvidenceIds = List.copyOf(relevantEvidenceIds);
            hits = List.copyOf(hits);
        }
    }

    public record Hit(
            int rank,
            String evidenceId,
            double cosineSimilarity,
            boolean accepted,
            boolean relevant
    ) {
    }
}
