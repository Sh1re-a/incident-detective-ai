package dev.shirwac.incidentdetective.domain.verification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum VerificationErrorCode {
    DIAGNOSIS_SCHEMA_INVALID("diagnosis_schema_invalid"),
    GROUND_TRUTH_SCHEMA_INVALID("ground_truth_schema_invalid"),
    UNKNOWN_EVIDENCE_ID("unknown_evidence_id");

    private final String wireValue;

    VerificationErrorCode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static VerificationErrorCode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(code -> code.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown verification error code: " + value
                ));
    }
}
