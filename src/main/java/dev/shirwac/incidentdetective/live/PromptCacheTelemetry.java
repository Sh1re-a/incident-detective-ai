package dev.shirwac.incidentdetective.live;

import io.swagger.v3.oas.annotations.media.Schema;

public record PromptCacheTelemetry(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = "provider_implicit"
        )
        PromptCacheStrategy strategy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        int providerReportedModelCalls,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        int modelCallCount,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                minimum = "0",
                description = "Sum of provider-reported cached input tokens; null when no call reported the field."
        )
        Integer cachedInputTokens,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean cacheHitObserved
) {
}
