package dev.shirwac.incidentdetective.diagnostic;

import com.fasterxml.jackson.annotation.JsonValue;

/** Whether a safe probe found enough request-local evidence to report. */
public enum DiagnosticProbeOutcome {
    OBSERVED("observed"),
    NOT_AVAILABLE("not_available");

    private final String wireValue;

    DiagnosticProbeOutcome(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
