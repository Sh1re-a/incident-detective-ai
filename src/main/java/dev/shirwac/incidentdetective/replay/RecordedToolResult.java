package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;

import java.util.List;

public record RecordedToolResult(
        String eventId,
        ToolName toolName,
        String safeSummary,
        List<Evidence> evidence
) {
    public RecordedToolResult {
        evidence = List.copyOf(evidence);
    }
}
