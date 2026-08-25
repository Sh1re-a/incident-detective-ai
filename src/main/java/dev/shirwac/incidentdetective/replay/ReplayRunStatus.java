package dev.shirwac.incidentdetective.replay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ReplayRunStatus {
    COMPLETED("completed"),
    VERIFICATION_FAILED("verification_failed");

    private final String wireValue;

    ReplayRunStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ReplayRunStatus fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown replay run status: " + value
                ));
    }
}
