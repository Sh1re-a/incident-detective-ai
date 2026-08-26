package dev.shirwac.incidentdetective.live;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PromptCacheStrategy {
    PROVIDER_IMPLICIT("provider_implicit");

    private final String wireValue;

    PromptCacheStrategy(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
