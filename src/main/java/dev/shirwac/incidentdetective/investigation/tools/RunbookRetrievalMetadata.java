package dev.shirwac.incidentdetective.investigation.tools;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record RunbookRetrievalMetadata(
        @NotBlank String backend,
        String corpusVersion,
        @Valid EmbeddingProfile embeddingProfile,
        @Valid QueryEmbeddingUsage queryEmbedding,
        @NotNull List<@NotNull @Valid Match> matches
) {
    private static final String FIXTURE_BACKEND =
            RunbookRetrievalBackend.DETERMINISTIC_FIXTURE.wireValue();
    private static final String PGVECTOR_BACKEND =
            RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE.wireValue();

    public RunbookRetrievalMetadata {
        matches = matches == null ? null : List.copyOf(matches);
        if (!Set.of(FIXTURE_BACKEND, PGVECTOR_BACKEND).contains(backend)) {
            throw new IllegalArgumentException("unknown runbook retrieval backend");
        }
        if (FIXTURE_BACKEND.equals(backend)
                && (corpusVersion != null
                || embeddingProfile != null
                || queryEmbedding != null)) {
            throw new IllegalArgumentException(
                    "fixture retrieval cannot claim an embedding profile"
            );
        }
        if (PGVECTOR_BACKEND.equals(backend)
                && (corpusVersion == null
                || embeddingProfile == null
                || queryEmbedding == null)) {
            throw new IllegalArgumentException(
                    "pgvector retrieval requires corpus and embedding metadata"
            );
        }
    }

    public static RunbookRetrievalMetadata fixture(List<Match> matches) {
        return new RunbookRetrievalMetadata(
                FIXTURE_BACKEND,
                null,
                null,
                null,
                matches
        );
    }

    public static RunbookRetrievalMetadata pgvector(
            String corpusVersion,
            EmbeddingProfile embeddingProfile,
            QueryEmbeddingUsage queryEmbedding,
            List<Match> matches
    ) {
        return new RunbookRetrievalMetadata(
                PGVECTOR_BACKEND,
                corpusVersion,
                embeddingProfile,
                queryEmbedding,
                matches
        );
    }

    public record EmbeddingProfile(
            @NotBlank String modelId,
            int dimensions,
            @NotBlank String formatVersion,
            double minimumSimilarity
    ) {
        public EmbeddingProfile {
            if (dimensions < 1) {
                throw new IllegalArgumentException("embedding dimensions must be positive");
            }
            if (!Double.isFinite(minimumSimilarity)
                    || minimumSimilarity < -1
                    || minimumSimilarity > 1) {
                throw new IllegalArgumentException(
                        "minimum similarity must be finite and between -1 and 1"
                );
            }
        }
    }

    public record QueryEmbeddingUsage(
            @Min(0) int localInputCharacters,
            Integer providerBillableCharacters,
            Double providerInputTokens,
            @Min(0) long latencyMs
    ) {
        public QueryEmbeddingUsage {
            if (providerBillableCharacters != null
                    && providerBillableCharacters < 0) {
                throw new IllegalArgumentException(
                        "provider billable characters cannot be negative"
                );
            }
            if (providerInputTokens != null
                    && (!Double.isFinite(providerInputTokens)
                    || providerInputTokens < 0)) {
                throw new IllegalArgumentException(
                        "provider input tokens must be finite and non-negative"
                );
            }
        }
    }

    public record Match(
            @Min(1) int rank,
            @NotBlank String evidenceId,
            Double cosineSimilarity,
            String contentSha256
    ) {
        public Match {
            if (cosineSimilarity != null
                    && (!Double.isFinite(cosineSimilarity)
                    || cosineSimilarity < -1
                    || cosineSimilarity > 1)) {
                throw new IllegalArgumentException(
                        "cosine similarity must be finite and between -1 and 1"
                );
            }
            if (contentSha256 != null
                    && !contentSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "content SHA-256 must contain 64 lowercase hex characters"
                );
            }
        }
    }
}
