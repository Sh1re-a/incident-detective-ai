package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LiveToolEvent(
        String eventId,
        int collectionRound,
        ToolName toolName,
        Map<String, Object> arguments,
        String safeSummary,
        List<Evidence> evidence
) {
    public LiveToolEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (collectionRound < 1) {
            throw new IllegalArgumentException("collectionRound must be positive");
        }
        Objects.requireNonNull(toolName, "toolName must not be null");
        arguments = Map.copyOf(arguments);
        Objects.requireNonNull(safeSummary, "safeSummary must not be null");
        evidence = List.copyOf(evidence);
    }
}
