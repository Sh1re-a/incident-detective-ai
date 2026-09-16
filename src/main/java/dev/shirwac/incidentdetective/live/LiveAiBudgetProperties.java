package dev.shirwac.incidentdetective.live;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "incident-detective.live-ai-budget")
public record LiveAiBudgetProperties(
        long dailyLimitMicroUsd,
        String priceProfileVersion
) {
    public static final long DEFAULT_DAILY_LIMIT_MICRO_USD = 200_000;
    public static final String DEFAULT_PRICE_PROFILE_VERSION =
            "gemini-standard-2026-09-15-v1";

    @ConstructorBinding
    public LiveAiBudgetProperties {
        if (dailyLimitMicroUsd < 1) {
            throw new IllegalArgumentException(
                    "dailyLimitMicroUsd must be positive"
            );
        }
        if (priceProfileVersion == null || priceProfileVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "priceProfileVersion must not be blank"
            );
        }
        long largestAllowance = java.util.Arrays.stream(LiveAiOperation.values())
                .mapToLong(LiveAiOperation::allowanceMicroUsd)
                .max()
                .orElseThrow();
        if (largestAllowance > dailyLimitMicroUsd) {
            throw new IllegalArgumentException(
                    "dailyLimitMicroUsd must cover every live operation"
            );
        }
    }

    public LiveAiBudgetProperties() {
        this(
                DEFAULT_DAILY_LIMIT_MICRO_USD,
                DEFAULT_PRICE_PROFILE_VERSION
        );
    }
}
