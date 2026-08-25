package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record Claim(
        @NotNull ClaimCode claimCode,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String claimValueCode,
        @NotBlank String displayText,
        @NotNull List<@NotBlank String> evidenceIds
) {
    public Claim {
        evidenceIds = evidenceIds == null ? null : List.copyOf(evidenceIds);
    }
}
