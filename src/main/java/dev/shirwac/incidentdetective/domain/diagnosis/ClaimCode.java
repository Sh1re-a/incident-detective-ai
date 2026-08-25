package dev.shirwac.incidentdetective.domain.diagnosis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ClaimCode {
    ROOT_CAUSE("root_cause"),
    AFFECTED_SERVICE("affected_service"),
    TRIGGER("trigger"),
    CUSTOMER_IMPACT("customer_impact"),
    OBSERVED_SYMPTOM("observed_symptom"),
    MISSING_EVIDENCE("missing_evidence");

    private final String wireValue;

    ClaimCode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ClaimCode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(code -> code.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown claim code: " + value));
    }
}
