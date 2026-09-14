package dev.shirwac.incidentdetective.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Display severity requested for a synthetic incident. */
public enum IncidentSeverity {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    CRITICAL("critical");

    private final String wireValue;

    IncidentSeverity(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static IncidentSeverity fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(severity -> severity.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown incident severity: " + value
                ));
    }
}
