package dev.shirwac.incidentdetective.ai;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ModelCostBreakdown(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal uncachedInputUsd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal cachedInputUsd,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal outputUsd,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Paid-list-price saving calculated only from provider-reported cached tokens."
        )
        BigDecimal observedCacheSavingsUsd
) {
}
