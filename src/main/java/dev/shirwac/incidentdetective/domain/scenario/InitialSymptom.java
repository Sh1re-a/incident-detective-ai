package dev.shirwac.incidentdetective.domain.scenario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record InitialSymptom(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String symptomCode,
        @NotBlank String summary,
        @NotNull Instant observedAt
) {
}
