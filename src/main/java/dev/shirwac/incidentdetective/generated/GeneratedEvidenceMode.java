package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Controls whether the generated case contains enough evidence to diagnose. */
public enum GeneratedEvidenceMode {
    DIAGNOSTIC("diagnostic"),
    INSUFFICIENT_EVIDENCE("insufficient_evidence");

    private final String wireValue;

    GeneratedEvidenceMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static GeneratedEvidenceMode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown generated evidence mode: " + value
                ));
    }
}
