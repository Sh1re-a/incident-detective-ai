package dev.shirwac.incidentdetective.replay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum RunMode {
    RECORDED_REPLAY("recorded_replay"),
    LIVE_AI("live_ai");

    private final String wireValue;

    RunMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static RunMode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown run mode: " + value));
    }
}
