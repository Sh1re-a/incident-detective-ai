package dev.shirwac.incidentdetective.replay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecordedToolEvent(
        @NotBlank String eventId,
        @NotNull ToolName toolName,
        @NotBlank String safeSummary,
        @NotEmpty List<@NotBlank String> evidenceIds
) {
    public RecordedToolEvent {
        evidenceIds = evidenceIds == null ? null : List.copyOf(evidenceIds);
    }
}
