package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SearchLogsResult(
        @NotNull List<String> availableServices,
        @NotNull List<String> availableLevels,
        @NotNull List<String> unknownServices,
        @NotNull List<String> unknownLevels,
        @NotNull List<@Valid LogEvidence> evidence,
        @Min(0) int returnedCount,
        boolean truncated
) {
    public SearchLogsResult {
        availableServices = availableServices == null
                ? null
                : List.copyOf(availableServices);
        availableLevels = availableLevels == null
                ? null
                : List.copyOf(availableLevels);
        unknownServices = unknownServices == null
                ? null
                : List.copyOf(unknownServices);
        unknownLevels = unknownLevels == null
                ? null
                : List.copyOf(unknownLevels);
        evidence = evidence == null ? null : List.copyOf(evidence);
    }
}
