package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiCostEstimatorTest {

    private final GeminiCostEstimator estimator = new GeminiCostEstimator();

    @Test
    void estimatesTheDefaultFlashLiteFromItsPaidStandardTokenPrices() {
        ModelCostEstimate estimate = estimator.estimate(
                "gemini-3.1-flash-lite",
                new ModelTokenUsage(1_000_000, 1_000_000, 2_000_000)
        );

        assertEquals(new BigDecimal("1.75000000"), estimate.estimatedUsd());
        assertTrue(estimate.basis().contains("free-tier charge may be USD 0"));
    }

    @Test
    void keepsTheMeasuredFlashLiteComparisonPrice() {
        ModelCostEstimate estimate = estimator.estimate(
                "gemini-3.5-flash-lite",
                new ModelTokenUsage(1_000_000, 1_000_000, 2_000_000)
        );

        assertEquals(new BigDecimal("2.80000000"), estimate.estimatedUsd());
    }

    @Test
    void estimatesCurrentFlashModelsFromTheirPaidStandardPrices() {
        ModelTokenUsage usage = new ModelTokenUsage(
                1_000_000,
                1_000_000,
                2_000_000
        );

        assertEquals(
                new BigDecimal("4.50000000"),
                estimator.estimate("gemini-3.6-flash", usage).estimatedUsd()
        );
        assertEquals(
                new BigDecimal("4.50000000"),
                estimator.estimate("gemini-3.7-flash", usage).estimatedUsd()
        );
    }

    @Test
    void doesNotInventAPriceForAnUnknownModel() {
        ModelCostEstimate estimate = estimator.estimate(
                "gemini-future-model",
                new ModelTokenUsage(10, 10, 20)
        );

        assertNull(estimate.estimatedUsd());
        assertEquals(
                "No paid list-price estimate is configured for this model.",
                estimate.basis()
        );
    }
}
