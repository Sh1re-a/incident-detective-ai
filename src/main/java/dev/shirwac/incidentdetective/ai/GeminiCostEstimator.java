package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
public final class GeminiCostEstimator {

    static final String PAID_LIST_PRICE_BASIS =
            "Gemini paid Standard list prices checked 2026-08-26; "
                    + "this is not a provider invoice and the API does not "
                    + "report whether the run was free-tier billed.";
    private static final String UNKNOWN_PRICE_BASIS =
            "No paid list-price estimate is configured for this model.";
    private static final String CACHE_NOT_REPORTED_BASIS =
            " Provider cache usage was not reported, so every input token "
                    + "was conservatively priced at the normal input rate.";
    private static final String CACHE_REPORTED_BASIS =
            " Provider-reported cached input tokens were priced at the "
                    + "context-caching rate; remaining input tokens used the "
                    + "normal rate. No explicit cache or storage cost was used.";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final Map<String, Pricing> PRICES = Map.of(
            "gemini-3.1-flash-lite", new Pricing("0.25", "0.025", "1.50"),
            "gemini-3.5-flash-lite", new Pricing("0.30", "0.03", "2.50"),
            "gemini-3.6-flash", new Pricing("0.75", "0.075", "3.75"),
            "gemini-3.7-flash", new Pricing("0.75", "0.075", "3.75")
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
            return new ModelCostEstimate(null, null, UNKNOWN_PRICE_BASIS);
        }
        Integer reportedCachedTokens = usage.cachedInputTokens();
        int cachedTokens = reportedCachedTokens == null
                ? 0
                : reportedCachedTokens;
        int uncachedTokens = usage.inputTokens() - cachedTokens;
        BigDecimal uncachedInputCost = cost(
                uncachedTokens,
                pricing.inputUsdPerMillion()
        );
        BigDecimal cachedInputCost = reportedCachedTokens == null
                ? null
                : cost(cachedTokens, pricing.cachedInputUsdPerMillion());
        BigDecimal outputCost = cost(
                usage.outputTokens(),
                pricing.outputUsdPerMillion()
        );
        BigDecimal total = uncachedInputCost
                .add(cachedInputCost == null ? BigDecimal.ZERO : cachedInputCost)
                .add(outputCost)
                .setScale(8, RoundingMode.HALF_UP);
        BigDecimal observedSaving = reportedCachedTokens == null
                ? null
                : cost(
                        cachedTokens,
                        pricing.inputUsdPerMillion()
                                .subtract(pricing.cachedInputUsdPerMillion())
                );
        return new ModelCostEstimate(
                total,
                new ModelCostBreakdown(
                        uncachedInputCost,
                        cachedInputCost,
                        outputCost,
                        observedSaving
                ),
                PAID_LIST_PRICE_BASIS
                        + (reportedCachedTokens == null
                        ? CACHE_NOT_REPORTED_BASIS
                        : CACHE_REPORTED_BASIS)
        );
    }

    private BigDecimal cost(int tokens, BigDecimal usdPerMillion) {
        return BigDecimal.valueOf(tokens)
                .multiply(usdPerMillion)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }

    private record Pricing(
            BigDecimal inputUsdPerMillion,
            BigDecimal cachedInputUsdPerMillion,
            BigDecimal outputUsdPerMillion
    ) {
        private Pricing(String input, String cachedInput, String output) {
            this(
                    new BigDecimal(input),
                    new BigDecimal(cachedInput),
                    new BigDecimal(output)
            );
        }
    }
}
