package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ToolExecution(
        String callId,
        ToolName toolName,
        Map<String, Object> arguments,
        String safeSummary,
        List<Evidence> evidence,
        RunbookRetrievalMetadata runbookRetrieval
) {
    public ToolExecution {
        Objects.requireNonNull(callId, "callId must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        arguments = Map.copyOf(arguments);
        Objects.requireNonNull(safeSummary, "safeSummary must not be null");
        evidence = List.copyOf(evidence);
    }
}
