package dev.shirwac.incidentdetective.domain.scenario;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TimeWindow(
        @NotNull Instant start,
        @NotNull Instant end
) {
    public TimeWindow {
        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }
}
