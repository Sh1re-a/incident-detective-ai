package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record IncidentLabRunRequest(
        @NotNull
        IncidentPlan plan,
        @Schema(
                nullable = true,
                example = "42",
                description = "Optional replay seed. Omit it for a new server-generated case."
        )
        Long seed,
        @Schema(
                nullable = true,
                description = "Optional evidence mode. Omit it for a reproducible "
                        + "automatic selection from the resolved seed; the literal "
                        + "value 'auto' is not accepted."
        )
        GeneratedEvidenceMode evidenceMode,
        @Schema(
                description = "Explicit opt-in for the conditional ADK investigation.",
                example = "true"
        )
        boolean confirmLiveAi
) {

    public IncidentLabRunRequest(
            IncidentPlan plan,
            Long seed,
            boolean confirmLiveAi
    ) {
        this(plan, seed, null, confirmLiveAi);
    }
}
