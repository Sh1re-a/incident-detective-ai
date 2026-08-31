package dev.shirwac.incidentdetective.live;

import java.time.Instant;

final class LiveDailyQuotaExceededException extends RuntimeException {

    private final Instant resetsAt;

    LiveDailyQuotaExceededException(Instant resetsAt) {
        super("The daily live-AI demo quota is exhausted");
        this.resetsAt = resetsAt;
    }

    Instant resetsAt() {
        return resetsAt;
    }
}
