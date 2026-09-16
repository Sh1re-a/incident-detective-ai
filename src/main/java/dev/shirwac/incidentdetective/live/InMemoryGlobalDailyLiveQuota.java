package dev.shirwac.incidentdetective.live;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@Profile("!rag")
public final class InMemoryGlobalDailyLiveQuota implements GlobalDailyLiveQuota {

    private final Clock clock;
    private LocalDate quotaDay;
    private int consumed;
    private long consumedMicroUsd;

    public InMemoryGlobalDailyLiveQuota(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Decision tryConsume(
            int dailyLimit,
            long dailyBudgetMicroUsd,
            long operationAllowanceMicroUsd
    ) {
        requireValidLimits(
                dailyLimit,
                dailyBudgetMicroUsd,
                operationAllowanceMicroUsd
        );
        LocalDate today = currentDay();
        resetIfNeeded(today);

        boolean allowed = consumed < dailyLimit
                && consumedMicroUsd <= dailyBudgetMicroUsd
                - operationAllowanceMicroUsd;
        if (allowed) {
            consumed++;
            consumedMicroUsd += operationAllowanceMicroUsd;
        }
        return new Decision(
                allowed,
                consumed,
                dailyLimit,
                consumedMicroUsd,
                dailyBudgetMicroUsd,
                resetAt(today)
        );
    }

    @Override
    public synchronized Snapshot snapshot(
            int dailyLimit,
            long dailyBudgetMicroUsd
    ) {
        requireValidLimits(dailyLimit, dailyBudgetMicroUsd, 1);
        LocalDate today = currentDay();
        resetIfNeeded(today);
        return new Snapshot(
                consumed,
                dailyLimit,
                consumedMicroUsd,
                dailyBudgetMicroUsd,
                resetAt(today)
        );
    }

    @Override
    public Scope scope() {
        return Scope.PROCESS_LOCAL;
    }

    private LocalDate currentDay() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private void resetIfNeeded(LocalDate today) {
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            consumed = 0;
            consumedMicroUsd = 0;
        }
    }

    private Instant resetAt(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void requireValidLimits(
            int dailyLimit,
            long dailyBudgetMicroUsd,
            long operationAllowanceMicroUsd
    ) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
        if (dailyBudgetMicroUsd < 1) {
            throw new IllegalArgumentException(
                    "dailyBudgetMicroUsd must be positive"
            );
        }
        if (operationAllowanceMicroUsd < 1
                || operationAllowanceMicroUsd > dailyBudgetMicroUsd) {
            throw new IllegalArgumentException(
                    "operation allowance must fit within the daily budget"
            );
        }
    }
}
