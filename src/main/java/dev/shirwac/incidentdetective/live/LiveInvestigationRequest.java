package dev.shirwac.incidentdetective.live;

import io.swagger.v3.oas.annotations.media.Schema;

public record LiveInvestigationRequest(
        @Schema(
                description = "Explicit confirmation that this request may call Gemini.",
                example = "true"
        )
        boolean confirmLiveAi
) {
}
