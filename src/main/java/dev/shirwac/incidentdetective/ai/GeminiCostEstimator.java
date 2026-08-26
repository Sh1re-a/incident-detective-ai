package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public final class GeminiCostEstimator {

    static final String PAID_LIST_PRICE_BASIS =
            "Gemini paid standard list price checked 2026-08-26; "
                    + "actual free-tier charge may be USD 0.";
    private static final String UNKNOWN_PRICE_BASIS =
            "No paid list-price estimate is configured for this model.";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final Map<String, Pricing> PRICES = Map.of(
            "gemini-3.1-flash-lite", new Pricing("0.25", "1.50"),
            "gemini-3.5-flash-lite", new Pricing("0.30", "2.50"),
            "gemini-3.6-flash", new Pricing("0.75", "3.75"),
            "gemini-3.7-flash", new Pricing("0.75", "3.75")
    );

    public ModelCostEstimate estimate(
            String modelId,
            ModelTokenUsage usage
    ) {
        Pricing pricing = PRICES.get(modelId);
        if (pricing == null
                || usage == null
                || usage.inputTokens() == null
                || usage.outputTokens() == null) {
            return new ModelCostEstimate(null, UNKNOWN_PRICE_BASIS);
        }
        BigDecimal inputCost = BigDecimal.valueOf(usage.inputTokens())
                .multiply(pricing.inputUsdPerMillion())
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(usage.outputTokens())
                .multiply(pricing.outputUsdPerMillion())
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return new ModelCostEstimate(
                inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP),
                PAID_LIST_PRICE_BASIS
        );
    }

    private record Pricing(
            BigDecimal inputUsdPerMillion,
            BigDecimal outputUsdPerMillion
    ) {
        private Pricing(String input, String output) {
            this(new BigDecimal(input), new BigDecimal(output));
        }
    }
}
