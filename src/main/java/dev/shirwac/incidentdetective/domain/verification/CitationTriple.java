package dev.shirwac.incidentdetective.domain.verification;

import java.util.Objects;

public record CitationTriple(
        ClaimKey claimKey,
        String evidenceId
) {
    public CitationTriple {
        Objects.requireNonNull(claimKey, "claimKey must not be null");
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");
    }
}
