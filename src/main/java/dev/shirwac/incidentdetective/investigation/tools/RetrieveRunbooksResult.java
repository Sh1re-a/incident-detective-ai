package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RetrieveRunbooksResult(
        @NotNull List<String> availableDocumentIds,
        @NotNull List<@Valid RunbookEvidence> evidence,
        @Min(0) int returnedCount,
        boolean truncated,
        @NotNull @Valid RunbookRetrievalMetadata retrievalMetadata
) {
    public RetrieveRunbooksResult {
        availableDocumentIds = availableDocumentIds == null
                ? null
                : List.copyOf(availableDocumentIds);
        evidence = evidence == null ? null : List.copyOf(evidence);
    }
}
