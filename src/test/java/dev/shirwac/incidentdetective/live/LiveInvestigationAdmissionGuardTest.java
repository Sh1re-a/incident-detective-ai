package dev.shirwac.incidentdetective.live;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
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

    private LiveInvestigationAdmissionGuard guard() {
        return new LiveInvestigationAdmissionGuard(
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
