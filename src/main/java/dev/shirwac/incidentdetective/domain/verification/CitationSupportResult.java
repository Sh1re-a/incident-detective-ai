package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;

import java.util.Objects;

public record CitationSupportResult(
        ClaimCode claimCode,
        String claimValueCode,
        String evidenceId,
        boolean supported
) {
    public CitationSupportResult {
        Objects.requireNonNull(claimCode, "claimCode must not be null");
        requireText(claimValueCode, "claimValueCode");
        requireText(evidenceId, "evidenceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
