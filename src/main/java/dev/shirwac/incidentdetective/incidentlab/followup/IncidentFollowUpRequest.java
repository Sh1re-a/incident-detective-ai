package dev.shirwac.incidentdetective.incidentlab.followup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IncidentFollowUpRequest(
        @NotBlank
        @Size(max = 80)
        @Pattern(regexp = "^ilr_[A-Za-z0-9_-]{20,72}$")
        String runReference,
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,100}$")
        String clientTurnId,
        @NotBlank
        @Size(max = 500)
        String question,
        @NotBlank
        @Pattern(regexp = "^(sv|en)$")
        String locale,
        @Schema(nullable = true, description = "Provider-free replay prompt ID. Ignored for live runs unless it matches a returned suggestion.")
        @Size(max = 40)
        String suggestionId,
        boolean confirmLiveAi
) {
}
