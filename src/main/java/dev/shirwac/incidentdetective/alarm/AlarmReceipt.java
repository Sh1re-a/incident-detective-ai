package dev.shirwac.incidentdetective.alarm;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Immutable proof of the exact rule and log evidence that raised an alarm. */
public record AlarmReceipt(
        String alarmId,
        String ruleId,
        String scenarioId,
        String service,
        Instant triggeredAt,
        Instant windowStartedAt,
        int threshold,
        int observedFailures,
        long windowSeconds,
        List<String> evidenceIds
) {

    public AlarmReceipt {
        requireText(alarmId, "alarmId");
        requireText(ruleId, "ruleId");
        requireText(scenarioId, "scenarioId");
        requireText(service, "service");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
        Objects.requireNonNull(windowStartedAt, "windowStartedAt must not be null");
        if (windowStartedAt.isAfter(triggeredAt)) {
            throw new IllegalArgumentException("windowStartedAt must not be after triggeredAt");
        }
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        if (observedFailures < threshold) {
            throw new IllegalArgumentException(
                    "observedFailures must meet or exceed threshold"
            );
        }
        if (windowSeconds < 1) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
        Objects.requireNonNull(
                evidenceIds,
                "evidenceIds must not be null"
        );
        if (evidenceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("evidenceIds must not contain blank values");
        }
        if (evidenceIds.size() != observedFailures) {
            throw new IllegalArgumentException(
                    "evidenceIds must identify every observed failure"
            );
        }
        evidenceIds = List.copyOf(evidenceIds);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
