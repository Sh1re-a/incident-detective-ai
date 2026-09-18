package dev.shirwac.incidentdetective.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Scope inferred from the request; Java applies the actual safe boundary. */
public enum IncidentBlastRadius {
    SINGLE_SERVICE("single_service"),
    SERVICE_CHAIN("service_chain"),
    WHOLE_DEMO_WORLD("whole_demo_world"),
    REAL_ENVIRONMENT("real_environment");

    private final String wireValue;

    IncidentBlastRadius(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static IncidentBlastRadius fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(radius -> radius.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown incident blast radius: " + value
                ));
    }
}
