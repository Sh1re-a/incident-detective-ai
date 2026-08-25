package dev.shirwac.incidentdetective.ai;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ModelPhase {
    COLLECT("collect"),
    SYNTHESIZE("synthesize");

    private final String wireValue;

    ModelPhase(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
