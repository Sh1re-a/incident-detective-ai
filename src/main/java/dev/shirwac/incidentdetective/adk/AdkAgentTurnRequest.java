package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One request-scoped synthetic incident turn through the real ADK runner. */
public record AdkAgentTurnRequest(
        @NotNull
        @Schema(example = "42")
        Long seed,
        @NotNull
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "payment_timeout"
        )
        GeneratedIncidentFamily incidentFamily,
        @NotNull
        @Schema(example = "diagnostic")
        GeneratedEvidenceMode evidenceMode,
        @NotNull
        @Schema(example = "low")
        GeneratedNoiseLevel noiseLevel,
        @NotBlank
        @Size(min = 3, max = 500)
        @Schema(
                description = "Investigation question. Safety checked before ADK or Gemini runs.",
                example = "Undersök larmet och förklara vad bevisen faktiskt stödjer."
        )
        String message,
        @Schema(
                description = "Explicit opt-in for the ADK-managed Gemini calls.",
                example = "true"
        )
        boolean confirmLiveAi
) {
}
