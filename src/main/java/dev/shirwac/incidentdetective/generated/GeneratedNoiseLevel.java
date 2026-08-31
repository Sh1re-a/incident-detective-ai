package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Controls bounded, explicitly synthetic distractor evidence. */
public enum GeneratedNoiseLevel {
    NONE("none"),
    LOW("low");

    private final String wireValue;

    GeneratedNoiseLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static GeneratedNoiseLevel fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(level -> level.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown generated noise level: " + value
                ));
    }
}
