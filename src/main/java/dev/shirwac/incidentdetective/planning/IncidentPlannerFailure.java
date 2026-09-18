package dev.shirwac.incidentdetective.planning;

/** Provider-neutral failures from the planning model boundary. */
public enum IncidentPlannerFailure {
    DISABLED,
    NOT_CONFIGURED,
    TIMEOUT,
    RATE_LIMITED,
    UPSTREAM,
    MALFORMED_RESPONSE
}
