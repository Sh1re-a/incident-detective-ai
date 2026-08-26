package dev.shirwac.incidentdetective.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Request path that produced the problem response."
        )
        String instance,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Stable machine-readable error code."
        )
        String code
) {
}
