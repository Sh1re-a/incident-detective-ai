package dev.shirwac.incidentdetective.live;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@Profile("rag")
public final class JdbcGlobalDailyLiveQuota implements GlobalDailyLiveQuota {

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcGlobalDailyLiveQuota(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Decision tryConsume(
            int dailyLimit,
            long dailyBudgetMicroUsd,
            long operationAllowanceMicroUsd
    ) {
        requireValidLimits(
                dailyLimit,
                dailyBudgetMicroUsd,
                operationAllowanceMicroUsd
        );
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<Usage> updated = jdbc.sql("""
                        INSERT INTO global_live_daily_quota (
                            quota_day,
                            consumed_starts,
                            consumed_micro_usd,
                            updated_at
                        ) VALUES (
                            :quotaDay,
                            1,
                            :operationAllowanceMicroUsd,
                            CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (quota_day) DO UPDATE SET
                            consumed_starts = global_live_daily_quota.consumed_starts + 1,
                            consumed_micro_usd = global_live_daily_quota.consumed_micro_usd
                                + :operationAllowanceMicroUsd,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE global_live_daily_quota.consumed_starts < :dailyLimit
                          AND global_live_daily_quota.consumed_micro_usd
                                <= :dailyBudgetMicroUsd - :operationAllowanceMicroUsd
                        RETURNING consumed_starts, consumed_micro_usd
                        """)
                .param("quotaDay", today)
                .param("dailyLimit", dailyLimit)
                .param("dailyBudgetMicroUsd", dailyBudgetMicroUsd)
                .param("operationAllowanceMicroUsd", operationAllowanceMicroUsd)
                .query((resultSet, rowNumber) -> new Usage(
                        resultSet.getInt("consumed_starts"),
                        resultSet.getLong("consumed_micro_usd")
                ))
                .list();

        boolean allowed = !updated.isEmpty();
        Usage usage = allowed ? updated.getFirst() : currentUsage(today);
        return new Decision(
                allowed,
                usage.consumedStarts(),
                dailyLimit,
                usage.consumedMicroUsd(),
                dailyBudgetMicroUsd,
                resetAt(today)
        );
    }

    @Override
    public Snapshot snapshot(int dailyLimit, long dailyBudgetMicroUsd) {
        requireValidLimits(dailyLimit, dailyBudgetMicroUsd, 1);
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        Usage usage = currentUsage(today);
        return new Snapshot(
                usage.consumedStarts(),
                dailyLimit,
                usage.consumedMicroUsd(),
                dailyBudgetMicroUsd,
                resetAt(today)
        );
    }

    @Override
    public Scope scope() {
        return Scope.DATABASE_GLOBAL;
    }

    private Usage currentUsage(LocalDate quotaDay) {
        return jdbc.sql("""
                        SELECT consumed_starts, consumed_micro_usd
                        FROM global_live_daily_quota
                        WHERE quota_day = :quotaDay
                        """)
                .param("quotaDay", quotaDay)
                .query((resultSet, rowNumber) -> new Usage(
                        resultSet.getInt("consumed_starts"),
                        resultSet.getLong("consumed_micro_usd")
                ))
                .optional()
                .orElseGet(() -> new Usage(0, 0));
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

    private record Usage(int consumedStarts, long consumedMicroUsd) {
    }
}
