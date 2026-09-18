package dev.shirwac.incidentdetective.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Whether the model found one candidate inside the bounded demo catalogue. */
public enum IncidentPlanProposalStatus {
    CANDIDATE("candidate"),
    UNSUPPORTED("unsupported");

    private final String wireValue;

    IncidentPlanProposalStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static IncidentPlanProposalStatus fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(status -> status.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown incident plan proposal status: " + value
                ));
    }
}
