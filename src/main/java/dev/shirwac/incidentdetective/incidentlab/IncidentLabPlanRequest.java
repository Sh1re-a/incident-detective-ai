package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.planning.IncidentPlanningRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentLabPlanRequest(
        @NotBlank
        @Size(max = IncidentPlanningRequest.MAX_INSTRUCTION_LENGTH)
        @Schema(
                example = "Låt betalningsflödet få timeout efter en syntetisk release."
        )
        String instruction,
        @Schema(
                description = "Explicit opt-in for the one Gemini planning call.",
                example = "true"
        )
        boolean confirmLiveAi
) {
}
