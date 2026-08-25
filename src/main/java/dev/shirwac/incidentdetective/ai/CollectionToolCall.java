package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.investigation.tools.ToolName;

import java.util.Map;
import java.util.Objects;

public record CollectionToolCall(
        String callId,
        ToolName toolName,
        Map<String, Object> arguments
) {
    public CollectionToolCall {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        arguments = Map.copyOf(arguments);
    }
}
