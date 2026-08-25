package dev.shirwac.incidentdetective.domain.verification;

import java.util.List;
import java.util.Objects;

public record CitationValidity(
        boolean valid,
        List<String> unknownEvidenceIds
) {
    public CitationValidity {
        Objects.requireNonNull(unknownEvidenceIds, "unknownEvidenceIds must not be null");
        unknownEvidenceIds = List.copyOf(unknownEvidenceIds);

        if (valid != unknownEvidenceIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "valid must be true exactly when unknownEvidenceIds is empty"
            );
        }
    }
}
