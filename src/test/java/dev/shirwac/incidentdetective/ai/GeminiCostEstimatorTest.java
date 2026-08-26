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
        assertEquals(
                new BigDecimal("0.25000000"),
                estimate.breakdown().uncachedInputUsd()
        );
        assertNull(estimate.breakdown().cachedInputUsd());
        assertEquals(
                new BigDecimal("1.50000000"),
                estimate.breakdown().outputUsd()
        );
        assertNull(estimate.breakdown().observedCacheSavingsUsd());
        assertTrue(estimate.basis().contains("not a provider invoice"));
        assertTrue(estimate.basis().contains("conservatively priced"));
    }

    @Test
    void pricesOnlyProviderReportedCacheHitsAtTheCachedRate() {
        ModelCostEstimate estimate = estimator.estimate(
                "gemini-3.1-flash-lite",
                new ModelTokenUsage(
                        1_000_000,
                        400_000,
                        600_000,
                        1_000_000,
                        0,
                        1_000_000,
                        0,
                        2_000_000
                )
        );

        assertEquals(new BigDecimal("1.66000000"), estimate.estimatedUsd());
        assertEquals(
                new BigDecimal("0.15000000"),
                estimate.breakdown().uncachedInputUsd()
        );
        assertEquals(
                new BigDecimal("0.01000000"),
                estimate.breakdown().cachedInputUsd()
        );
        assertEquals(
                new BigDecimal("1.50000000"),
                estimate.breakdown().outputUsd()
        );
        assertEquals(
                new BigDecimal("0.09000000"),
                estimate.breakdown().observedCacheSavingsUsd()
        );
        assertTrue(estimate.basis().contains("Provider-reported"));
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
        assertNull(estimate.breakdown());
        assertEquals(
                "No paid list-price estimate is configured for this model.",
                estimate.basis()
        );
    }

    @Test
    void keepsTheEstimateUnavailableWhenCoreUsageIsMissing() {
        ModelCostEstimate estimate = estimator.estimate(
                "gemini-3.1-flash-lite",
                new ModelTokenUsage(null, null, null, null, null, null, null, null)
        );

        assertNull(estimate.estimatedUsd());
        assertNull(estimate.breakdown());
    }
}
