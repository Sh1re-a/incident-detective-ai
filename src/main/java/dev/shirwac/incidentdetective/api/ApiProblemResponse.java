package dev.shirwac.incidentdetective.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "400")
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai"
        )
        String instance,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Stable machine-readable error code.",
                example = "LIVE_AI_CONFIRMATION_REQUIRED"
        )
        String code
) {
}
