package dev.shirwac.incidentdetective.live;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LiveRunStatus {
    COMPLETED("completed"),
    VERIFICATION_FAILED("verification_failed");

    private final String wireValue;

    LiveRunStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
