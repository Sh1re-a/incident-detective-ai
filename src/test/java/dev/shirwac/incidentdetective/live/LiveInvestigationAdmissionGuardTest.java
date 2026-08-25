package dev.shirwac.incidentdetective.live;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveInvestigationAdmissionGuardTest {

    private static final Instant NOW = Instant.parse("2026-08-25T15:00:00Z");

    @Test
    void allowsOnlyOneLiveRunAtATimeAndReleasesAfterCompletion() {
        LiveInvestigationAdmissionGuard guard = guard();
        AtomicBoolean nestedActionCalled = new AtomicBoolean(false);

        guard.admit(() -> {
            LiveAdmissionRejectedException rejection = assertThrows(
                    LiveAdmissionRejectedException.class,
                    () -> guard.admit(() -> {
                        nestedActionCalled.set(true);
                        return null;
                    })
            );
            assertEquals(
                    LiveAdmissionRejection.CONCURRENT_RUN,
                    rejection.rejection()
            );
            assertEquals(
                    LiveInvestigationAdmissionGuard.BUSY_RETRY_AFTER,
                    rejection.retryAfter()
            );
            return null;
        });

        assertFalse(nestedActionCalled.get());
        assertEquals("admitted", guard.admit(() -> "admitted"));
    }

    @Test
    void limitsLiveStartsInsideTheRollingWindow() {
        LiveInvestigationAdmissionGuard guard = guard();
        for (int run = 0;
             run < LiveInvestigationAdmissionGuard.MAX_STARTS_PER_WINDOW;
             run++) {
            guard.admit(() -> null);
        }

        LiveAdmissionRejectedException rejection = assertThrows(
                LiveAdmissionRejectedException.class,
                () -> guard.admit(() -> null)
        );

        assertEquals(LiveAdmissionRejection.ROLLING_LIMIT, rejection.rejection());
        assertEquals(
                LiveInvestigationAdmissionGuard.ROLLING_WINDOW,
                rejection.retryAfter()
        );
    }

    @Test
    void releasesConcurrencyPermitWhenTheLiveActionFails() {
        LiveInvestigationAdmissionGuard guard = guard();
        assertThrows(
                IllegalStateException.class,
                () -> guard.admit(() -> {
                    throw new IllegalStateException("provider failed");
                })
        );

        assertTrue(guard.admit(() -> true));
    }

    @Test
    void reopensTheOldestSlotAtTheRollingWindowBoundary() {
        MutableClock clock = new MutableClock(NOW);
        LiveInvestigationAdmissionGuard guard = new LiveInvestigationAdmissionGuard(clock);
        for (int run = 0;
             run < LiveInvestigationAdmissionGuard.MAX_STARTS_PER_WINDOW;
             run++) {
            guard.admit(() -> null);
        }

        clock.advance(LiveInvestigationAdmissionGuard.ROLLING_WINDOW
                .minusSeconds(1));
        LiveAdmissionRejectedException rejection = assertThrows(
                LiveAdmissionRejectedException.class,
                () -> guard.admit(() -> null)
        );
        assertEquals(Duration.ofSeconds(1), rejection.retryAfter());

        clock.advance(Duration.ofSeconds(1));
        assertEquals("reopened", guard.admit(() -> "reopened"));
    }

    private LiveInvestigationAdmissionGuard guard() {
        return new LiveInvestigationAdmissionGuard(
                Clock.fixed(NOW, ZoneOffset.UTC)
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
