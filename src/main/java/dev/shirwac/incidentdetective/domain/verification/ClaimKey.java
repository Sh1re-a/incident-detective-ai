package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;

import java.util.Objects;

public record ClaimKey(
        ClaimCode claimCode,
        String claimValueCode
) {
    public ClaimKey {
        Objects.requireNonNull(claimCode, "claimCode must not be null");
        Objects.requireNonNull(claimValueCode, "claimValueCode must not be null");
    }
}
