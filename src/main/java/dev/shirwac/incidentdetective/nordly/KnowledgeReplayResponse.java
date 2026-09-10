package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "A provider-free replay of a bounded Nordly knowledge answer.")
public record KnowledgeReplayResponse(
        String contractVersion,
        String runId,
        String mode,
        String truthLabel,
        boolean modelBacked,
        boolean currentVectorSearch,
        ReplayQuestion question,
        RetrievalSnapshot retrieval,
        KnowledgeAnswer answer,
        ReplayVerification verification,
        ReplayReceipt receipt,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "nordly-knowledge-replay-v1";
    public static final String MODE = "recorded_replay";

    public KnowledgeReplayResponse {
        limitations = limitations == null ? null : List.copyOf(limitations);
    }

    public record ReplayQuestion(
            String id,
            String promptSv,
            String promptEn
    ) {
    }

    public record RetrievalSnapshot(
            String backend,
            String corpusVersion,
            EmbeddingProfile embeddingProfile,
            QueryEmbeddingSnapshot queryEmbedding,
            List<RankedMatch> rankedMatches
    ) {
        public RetrievalSnapshot {
            rankedMatches = rankedMatches == null
                    ? null
                    : List.copyOf(rankedMatches);
        }
    }

    public record EmbeddingProfile(
            String provider,
            String modelId,
            int dimensions
    ) {
    }

    public record QueryEmbeddingSnapshot(
            boolean executedInThisRun,
            @Schema(nullable = true)
            Long latencyMs
    ) {
    }

    public record RankedMatch(
            int rank,
            @Schema(nullable = true)
            Double similarity,
            String status,
            String documentId,
            String chunkId,
            String documentVersion,
            String title,
            String sectionHeading,
            String ownerTeam,
            String sourceRef,
            String evidenceId,
            String displaySummarySv,
            String displaySummaryEn,
            String text
    ) {
    }

    public record KnowledgeAnswer(
            String status,
            String summarySv,
            String summaryEn,
            List<AnswerClaim> claims
    ) {
        public KnowledgeAnswer {
            claims = claims == null ? null : List.copyOf(claims);
        }
    }

    public record AnswerClaim(
            String textSv,
            String textEn,
            List<String> citationIds
    ) {
        public AnswerClaim {
            citationIds = citationIds == null ? null : List.copyOf(citationIds);
        }
    }

    public record ReplayVerification(
            boolean schemaPass,
            int citationsSeen,
            boolean approvedDocumentsOnly,
            boolean claimSupportPass,
            boolean actionWithinScope,
            String overallOutcome
    ) {
    }

    public record ReplayReceipt(
            List<String> toolCalls,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            @Schema(nullable = true)
            Long latencyMs,
            @Schema(nullable = true)
            Integer totalTokens,
            @Schema(nullable = true)
            BigDecimal estimatedCostUsd,
            String costStatus
    ) {
        public ReplayReceipt {
            toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
        }
    }
}
