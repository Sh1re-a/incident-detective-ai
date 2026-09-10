package dev.shirwac.incidentdetective.nordly;

import java.math.BigDecimal;
import java.util.List;

record KnowledgeReplayFixtureCollection(
        String collectionVersion,
        List<ReplayFixture> replays
) {
    static final String COLLECTION_VERSION = "nordly-knowledge-replays-v1";

    KnowledgeReplayFixtureCollection {
        replays = replays == null ? null : List.copyOf(replays);
    }

    record ReplayFixture(
            String contractVersion,
            String runId,
            String mode,
            String truthLabel,
            boolean modelBacked,
            boolean currentVectorSearch,
            String questionId,
            List<MatchReference> rankedMatches,
            AnswerFixture answer,
            VerificationFixture verification,
            ReceiptFixture receipt,
            List<String> limitations
    ) {
        ReplayFixture {
            rankedMatches = rankedMatches == null
                    ? null
                    : List.copyOf(rankedMatches);
            limitations = limitations == null
                    ? null
                    : List.copyOf(limitations);
        }
    }

    record MatchReference(
            int rank,
            Double similarity,
            String documentId,
            String chunkId
    ) {
    }

    record AnswerFixture(
            String status,
            String summarySv,
            String summaryEn,
            List<KnowledgeReplayResponse.AnswerClaim> claims
    ) {
        AnswerFixture {
            claims = claims == null ? null : List.copyOf(claims);
        }
    }

    record VerificationFixture(
            boolean schemaPass,
            int citationsSeen,
            boolean approvedDocumentsOnly,
            boolean claimSupportPass,
            boolean actionWithinScope,
            String overallOutcome
    ) {
    }

    record ReceiptFixture(
            List<String> toolCalls,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            Long latencyMs,
            Integer totalTokens,
            BigDecimal estimatedCostUsd,
            String costStatus
    ) {
        ReceiptFixture {
            toolCalls = toolCalls == null ? null : List.copyOf(toolCalls);
        }
    }
}
