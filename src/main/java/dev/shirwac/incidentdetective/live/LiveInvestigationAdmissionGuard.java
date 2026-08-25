package dev.shirwac.incidentdetective.live;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Component
final class LiveInvestigationAdmissionGuard {

    static final int MAX_STARTS_PER_WINDOW = 5;
    static final Duration ROLLING_WINDOW = Duration.ofMinutes(10);
    static final Duration BUSY_RETRY_AFTER = Duration.ofSeconds(2);

    private final Clock clock;
    private final Semaphore concurrentRuns = new Semaphore(1, true);
    private final Deque<Instant> admittedStarts = new ArrayDeque<>();

    LiveInvestigationAdmissionGuard(Clock clock) {
        this.clock = clock;
    }

    <T> T admit(Supplier<T> action) {
        if (!concurrentRuns.tryAcquire()) {
            throw new LiveAdmissionRejectedException(
                    LiveAdmissionRejection.CONCURRENT_RUN,
                    BUSY_RETRY_AFTER
            );
        }

        try {
            recordStartOrReject();
            return action.get();
        } finally {
            concurrentRuns.release();
        }
    }

    private void recordStartOrReject() {
        Instant now = clock.instant();
        synchronized (admittedStarts) {
            Instant cutoff = now.minus(ROLLING_WINDOW);
            while (!admittedStarts.isEmpty()
                    && !admittedStarts.getFirst().isAfter(cutoff)) {
                admittedStarts.removeFirst();
            }

            if (admittedStarts.size() >= MAX_STARTS_PER_WINDOW) {
                Instant nextSlot = admittedStarts.getFirst().plus(ROLLING_WINDOW);
                throw new LiveAdmissionRejectedException(
                        LiveAdmissionRejection.ROLLING_LIMIT,
                        positive(Duration.between(now, nextSlot))
                );
            }
            admittedStarts.addLast(now);
        }
    }

    private Duration positive(Duration duration) {
        return duration.isPositive() ? duration : Duration.ofSeconds(1);
    }
}
