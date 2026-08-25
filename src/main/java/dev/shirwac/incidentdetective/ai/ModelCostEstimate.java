package dev.shirwac.incidentdetective.ai;

import java.math.BigDecimal;

public record ModelCostEstimate(
        BigDecimal estimatedUsd,
        String basis
) {
}
