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

    public InMemoryGlobalDailyLiveQuota(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized Decision tryConsume(int dailyLimit) {
        requirePositiveLimit(dailyLimit);
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        if (!today.equals(quotaDay)) {
            quotaDay = today;
            consumed = 0;
        }

        boolean allowed = consumed < dailyLimit;
        if (allowed) {
            consumed++;
        }
        return decision(today, allowed, consumed, dailyLimit);
    }

    @Override
    public Scope scope() {
        return Scope.PROCESS_LOCAL;
    }

    private Decision decision(
            LocalDate day,
            boolean allowed,
            int currentCount,
            int dailyLimit
    ) {
        Instant resetsAt = day.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        return new Decision(allowed, currentCount, dailyLimit, resetsAt);
    }

    private void requirePositiveLimit(int dailyLimit) {
        if (dailyLimit < 1) {
            throw new IllegalArgumentException("dailyLimit must be positive");
        }
    }
}
