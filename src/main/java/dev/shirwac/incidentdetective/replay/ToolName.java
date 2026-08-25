package dev.shirwac.incidentdetective.replay;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ToolName {
    GET_METRICS("get_metrics"),
    SEARCH_LOGS("search_logs"),
    GET_TRACE("get_trace"),
    RETRIEVE_RUNBOOKS("retrieve_runbooks");

    private final String wireValue;

    ToolName(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ToolName fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(tool -> tool.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + value));
    }
}
