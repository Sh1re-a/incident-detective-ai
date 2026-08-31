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
    public Decision tryConsume(int dailyLimit) {
        requirePositiveLimit(dailyLimit);
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        List<Integer> updatedCounts = jdbc.sql("""
                        INSERT INTO global_live_daily_quota (
                            quota_day,
                            consumed_starts,
                            updated_at
                        ) VALUES (
                            :quotaDay,
                            1,
                            CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (quota_day) DO UPDATE SET
                            consumed_starts = global_live_daily_quota.consumed_starts + 1,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE global_live_daily_quota.consumed_starts < :dailyLimit
                        RETURNING consumed_starts
                        """)
                .param("quotaDay", today)
                .param("dailyLimit", dailyLimit)
                .query(Integer.class)
                .list();

        boolean allowed = !updatedCounts.isEmpty();
        int currentCount = allowed
                ? updatedCounts.getFirst()
                : currentCount(today);
        Instant resetsAt = today.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        return new Decision(allowed, currentCount, dailyLimit, resetsAt);
    }

    @Override
    public Scope scope() {
        return Scope.DATABASE_GLOBAL;
    }

    private int currentCount(LocalDate quotaDay) {
        Integer count = jdbc.sql("""
                        SELECT consumed_starts
                        FROM global_live_daily_quota
                        WHERE quota_day = :quotaDay
                        """)
                .param("quotaDay", quotaDay)
                .query(Integer.class)
                .single();
        return count;
    }

    private void requirePositiveLimit(int dailyLimit) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
    }
}
