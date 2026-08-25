package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record Diagnosis(
        @NotNull DiagnosisStatus status,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String rootCauseCode,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String affectedService,
        @NotBlank String businessSummary,
        @NotBlank String technicalSummary,
        @NotNull List<@NotNull @Valid Claim> claims,
        @NotNull @Valid SafeNextStep safeNextStep
) {
    public Diagnosis {
        claims = claims == null ? null : List.copyOf(claims);
    }
}
