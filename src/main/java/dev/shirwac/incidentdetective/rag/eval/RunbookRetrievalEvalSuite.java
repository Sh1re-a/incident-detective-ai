package dev.shirwac.incidentdetective.rag.eval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RunbookRetrievalEvalSuite(
        @NotBlank String suiteVersion,
        @NotBlank String corpusVersion,
        @NotNull @Valid RetrievalContract retrievalContract,
        @NotNull @Size(min = 1) List<@NotNull @Valid EvalCase> cases
) {
    public RunbookRetrievalEvalSuite {
        cases = cases == null ? null : List.copyOf(cases);
    }

    public record RetrievalContract(
            @NotBlank String provider,
            @NotBlank String model,
            int dimensions,
            @NotBlank String embeddingFormatVersion,
            @NotBlank String documentInputFormat,
            @NotBlank String queryInputFormat,
            @NotBlank String distance,
            int topK,
            @NotBlank String truncationPolicy,
            @NotBlank String thresholdPolicy
    ) {
    }

    public record EvalCase(
            @NotBlank String caseId,
            @NotBlank String split,
            @NotBlank String caseType,
            String scenarioId,
            @NotBlank @Size(max = 160) String query,
            @NotNull List<@NotBlank String> relevantEvidenceIds,
            boolean expectedEmpty,
            String safetyFollowUp
    ) {
        public EvalCase {
            relevantEvidenceIds = relevantEvidenceIds == null
                    ? null
                    : List.copyOf(relevantEvidenceIds);
        }
    }
}
