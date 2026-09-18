package dev.shirwac.incidentdetective.live;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryGlobalDailyLiveQuotaTest {

    @Test
    void consumesUpToTheLimitAndRejectsWithoutIncrementing() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(
                Clock.fixed(
                        Instant.parse("2026-08-31T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        GlobalDailyLiveQuota.Decision first = quota.tryConsume(2, 100, 10);
        GlobalDailyLiveQuota.Decision second = quota.tryConsume(2, 100, 10);
        GlobalDailyLiveQuota.Decision rejected = quota.tryConsume(2, 100, 10);

        assertTrue(first.allowed());
        assertEquals(1, first.consumed());
        assertEquals(1, first.remaining());
        assertTrue(second.allowed());
        assertEquals(2, second.consumed());
        assertFalse(rejected.allowed());
        assertEquals(2, rejected.consumed());
        assertEquals(0, rejected.remaining());
        assertEquals(20, rejected.consumedMicroUsd());
        assertEquals(80, rejected.remainingMicroUsd());
        assertEquals(
                Instant.parse("2026-09-01T00:00:00Z"),
                rejected.resetsAt()
        );
    }

    @Test
    void startsAFreshBudgetAtTheNextUtcDay() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-08-31T23:59:59Z")
        );
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(clock);

        assertTrue(quota.tryConsume(1, 100, 10).allowed());
        assertFalse(quota.tryConsume(1, 100, 10).allowed());
        clock.advance(Duration.ofSeconds(1));

        GlobalDailyLiveQuota.Decision nextDay = quota.tryConsume(1, 100, 10);
        assertTrue(nextDay.allowed());
        assertEquals(1, nextDay.consumed());
        assertEquals(10, nextDay.consumedMicroUsd());
        assertEquals(Instant.parse("2026-09-02T00:00:00Z"), nextDay.resetsAt());
    }

    @Test
    void rejectsBeforeTheConservativeCostAllowanceCanBeExceeded() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(
                Clock.fixed(
                        Instant.parse("2026-08-31T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        assertTrue(quota.tryConsume(20, 25, 10).allowed());
        assertTrue(quota.tryConsume(20, 25, 10).allowed());
        GlobalDailyLiveQuota.Decision rejected = quota.tryConsume(20, 25, 10);

        assertFalse(rejected.allowed());
        assertEquals(2, rejected.consumed());
        assertEquals(20, rejected.consumedMicroUsd());
        assertEquals(5, rejected.remainingMicroUsd());
        assertFalse(quota.snapshot(20, 25).canConsume(10));
    }

    @Test
    void rejectsAnInvalidLimitBeforeChangingState() {
        InMemoryGlobalDailyLiveQuota quota = new InMemoryGlobalDailyLiveQuota(
                Clock.systemUTC()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> quota.tryConsume(0, 100, 10)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> quota.tryConsume(1, 10, 11)
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("test clock only supports UTC");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
