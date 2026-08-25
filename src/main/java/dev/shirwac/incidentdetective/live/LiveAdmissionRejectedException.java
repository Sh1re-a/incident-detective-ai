package dev.shirwac.incidentdetective.live;

import java.time.Duration;

final class LiveAdmissionRejectedException extends RuntimeException {

    private final LiveAdmissionRejection rejection;
    private final Duration retryAfter;

    LiveAdmissionRejectedException(
            LiveAdmissionRejection rejection,
            Duration retryAfter
    ) {
        super("Live AI admission rejected: " + rejection);
        this.rejection = rejection;
        this.retryAfter = retryAfter;
    }

    LiveAdmissionRejection rejection() {
        return rejection;
    }

    Duration retryAfter() {
        return retryAfter;
    }
}
