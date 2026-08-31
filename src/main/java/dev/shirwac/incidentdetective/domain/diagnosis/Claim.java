package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Claim(
        @NotNull ClaimCode claimCode,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String claimValueCode,
        @NotBlank String displayText,
        @NotNull @Size(min = 1, max = 2) List<@NotBlank String> evidenceIds
) {
    public Claim {
        evidenceIds = evidenceIds == null ? null : List.copyOf(evidenceIds);
    }
}
