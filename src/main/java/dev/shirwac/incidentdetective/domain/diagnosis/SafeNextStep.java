package dev.shirwac.incidentdetective.domain.diagnosis;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record SafeNextStep(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String summary,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "true",
                description = "Incident Detective recommends only; a human must approve changes."
        )
        @AssertTrue boolean requiresHumanApproval
) {
}
