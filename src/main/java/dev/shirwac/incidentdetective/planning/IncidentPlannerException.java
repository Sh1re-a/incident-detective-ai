package dev.shirwac.incidentdetective.planning;

import java.util.Objects;

/** Safe typed failure; raw provider output is never carried by this exception. */
public final class IncidentPlannerException extends RuntimeException {

    private final IncidentPlannerFailure failure;

    public IncidentPlannerException(
            IncidentPlannerFailure failure,
            String internalMessage
    ) {
        super(internalMessage);
        this.failure = Objects.requireNonNull(failure);
    }

    public IncidentPlannerException(
            IncidentPlannerFailure failure,
            String internalMessage,
            Throwable cause
    ) {
        super(internalMessage, cause);
        this.failure = Objects.requireNonNull(failure);
    }

    public IncidentPlannerFailure failure() {
        return failure;
    }
}
