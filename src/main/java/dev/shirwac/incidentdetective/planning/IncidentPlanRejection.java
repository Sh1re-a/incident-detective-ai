package dev.shirwac.incidentdetective.planning;

import java.util.Objects;

public record IncidentPlanRejection(
        IncidentPlanRejectionCode code,
        String safeMessage
) {
    public IncidentPlanRejection {
        Objects.requireNonNull(code, "code must not be null");
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
        safeMessage = safeMessage.strip();
    }
}
