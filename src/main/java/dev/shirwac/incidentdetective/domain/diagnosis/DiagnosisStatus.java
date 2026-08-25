package dev.shirwac.incidentdetective.domain.diagnosis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum DiagnosisStatus {
    DIAGNOSED("diagnosed"),
    INSUFFICIENT_EVIDENCE("insufficient_evidence");

    private final String wireValue;

    DiagnosisStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DiagnosisStatus fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown diagnosis status: " + value));
    }
}
