package dev.shirwac.incidentdetective.planning;

/** Stable, safe rejection reasons suitable for translation at the API edge. */
public enum IncidentPlanRejectionCode {
    REAL_ENVIRONMENT_NOT_ALLOWED,
    UNSUPPORTED_REQUEST,
    INCIDENT_FAMILY_NOT_RUNNABLE,
    SERVICE_SCOPE_NOT_SUPPORTED
}
