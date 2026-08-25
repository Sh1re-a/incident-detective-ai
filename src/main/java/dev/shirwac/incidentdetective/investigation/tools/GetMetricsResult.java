package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GetMetricsResult(
        @NotNull List<String> availableMetricNames,
        @NotNull List<String> unknownMetricNames,
        @NotNull List<@Valid MetricEvidence> evidence,
        @Min(0) int returnedCount,
        boolean truncated
) {
    public GetMetricsResult {
        availableMetricNames = availableMetricNames == null
                ? null
                : List.copyOf(availableMetricNames);
        unknownMetricNames = unknownMetricNames == null
                ? null
                : List.copyOf(unknownMetricNames);
        evidence = evidence == null ? null : List.copyOf(evidence);
    }
}
