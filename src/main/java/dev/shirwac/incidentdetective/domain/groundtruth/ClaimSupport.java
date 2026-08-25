package dev.shirwac.incidentdetective.domain.groundtruth;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ClaimSupport(
        @NotNull ClaimCode claimCode,
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String claimValueCode,
        @NotEmpty List<@NotBlank String> allowedEvidenceIds
) {
    public ClaimSupport {
        allowedEvidenceIds = allowedEvidenceIds == null
                ? null
                : List.copyOf(allowedEvidenceIds);
    }
}
