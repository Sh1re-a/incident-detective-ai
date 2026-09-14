package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.planning.IncidentPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record IncidentLabRunRequest(
        @NotNull
        IncidentPlan plan,
        @NotNull
        @Schema(example = "42")
        Long seed,
        @Schema(
                description = "Explicit opt-in for the conditional ADK investigation.",
                example = "true"
        )
        boolean confirmLiveAi
) {
}
