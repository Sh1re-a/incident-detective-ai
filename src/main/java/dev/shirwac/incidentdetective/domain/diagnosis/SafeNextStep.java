package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record SafeNextStep(
        @NotBlank String summary,
        @AssertTrue boolean requiresHumanApproval
) {
}
