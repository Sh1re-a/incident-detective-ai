package dev.shirwac.incidentdetective.ai;

public enum ModelProviderFailure {
    TIMEOUT,
    RATE_LIMITED,
    UPSTREAM,
    MALFORMED_RESPONSE
}
