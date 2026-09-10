package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(
        description = "Completed, bounded Nordly RAG result. Phase events are "
                + "post-run receipts, never hidden reasoning or chain-of-thought."
)
public record KnowledgeRagResponse(
        String contractVersion,
        String runId,
        String mode,
        String truthLabel,
        String truthLabelEn,
        String outcome,
        @Schema(nullable = true)
        GoogleGenAiProviderRoute providerRoute,
        SubmittedQuestion question,
        SafetyDecision safety,
        List<PhaseEvent> phases,
        RetrievalResult retrieval,
        @Schema(nullable = true)
        Answer answer,
        Verification verification,
        Receipt receipt,
        @Schema(nullable = true)
        ErrorDetail error,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "nordly-knowledge-rag-v2";
    public static final String MODE = "live_rag";
    public static final String BACKEND = "pgvector_exact_cosine";

    public KnowledgeRagResponse {
        phases = phases == null ? null : List.copyOf(phases);
        limitations = limitations == null ? null : List.copyOf(limitations);
    }

    public record SubmittedQuestion(
            String text,
            String locale,
            boolean redacted
    ) {
    }

    public record SafetyDecision(
            String decision,
            String reasonCode,
            String summarySv,
            String summaryEn
    ) {
    }

    public record PhaseEvent(
            String id,
            String status,
            boolean executed,
            String summarySv,
            String summaryEn,
            @Schema(nullable = true)
            Long latencyMs
    ) {
    }

    public record RetrievalResult(
            String backend,
            String corpusVersion,
            String corpusContentSha256,
            @Schema(nullable = true)
            IndexSnapshot indexSnapshot,
            String requiredLifecycle,
            String requiredAccessScope,
            int eligibleDocumentCount,
            int eligibleChunkCount,
            boolean currentVectorSearch,
            int topK,
            double minimumSimilarity,
            QueryEmbedding queryEmbedding,
            List<RankedMatch> rankedMatches
    ) {
        public RetrievalResult {
            rankedMatches = rankedMatches == null
                    ? null
                    : List.copyOf(rankedMatches);
        }
    }

    public record IndexSnapshot(
            String status,
            boolean ready,
            long indexedChunks,
            long currentChunks,
            int expectedChunks
    ) {
    }

    public record QueryEmbedding(
            boolean executedInThisRun,
            @Schema(nullable = true)
            String provider,
            @Schema(nullable = true)
            String modelId,
            @Schema(nullable = true)
            Integer dimensions,
            @Schema(nullable = true)
            Long latencyMs,
            @Schema(nullable = true)
            Integer inputCharacters,
            @Schema(nullable = true)
            Integer providerBillableCharacters,
            @Schema(nullable = true)
            Double providerInputTokens
    ) {
    }

    public record RankedMatch(
            int rank,
            double similarity,
            String status,
            String documentId,
            String chunkId,
            String documentVersion,
            String title,
            String sectionHeading,
            String ownerTeam,
            String sourceRef,
            String evidenceId,
            String contentSha256,
            String displaySummarySv,
            String displaySummaryEn,
            String text
    ) {
    }

    public record Answer(
            String status,
            String summarySv,
            String summaryEn,
            List<AnswerClaim> claims
    ) {
        public Answer {
            claims = claims == null ? null : List.copyOf(claims);
        }
    }

    public record AnswerClaim(
            String textSv,
            String textEn,
            List<String> citationIds
    ) {
        public AnswerClaim {
            citationIds = citationIds == null
                    ? null
                    : List.copyOf(citationIds);
        }
    }

    public record Verification(
            boolean schemaPass,
            boolean citationsWithinRetrievedContext,
            boolean approvedDocumentsOnly,
            boolean outputPiiScanPass,
            boolean outputPolicyScanPass,
            boolean noWriteCapability,
            String overallOutcome
    ) {
    }

    public record Receipt(
            int providerCalls,
            int embeddingCalls,
            int generationCalls,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            long totalLatencyMs,
            @Schema(nullable = true)
            String modelId,
            @Schema(nullable = true)
            String providerResponseId,
            @Schema(nullable = true)
            ModelTokenUsage tokenUsage,
            @Schema(nullable = true)
            BigDecimal estimatedCostUsd,
            String costStatus,
            String costBasis
    ) {
    }

    public record ErrorDetail(
            String code,
            String summarySv,
            String summaryEn
    ) {
    }
}
