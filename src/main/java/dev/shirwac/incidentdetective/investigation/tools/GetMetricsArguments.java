package dev.shirwac.incidentdetective.investigation.tools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record GetMetricsArguments(
        @NotEmpty
        @Size(max = 8)
        List<
                @NotBlank
                @Pattern(regexp = "^[a-z][a-z0-9_]{1,63}$")
                String
                > metricNames,
        @NotNull Instant start,
        @NotNull Instant end
) {
    public GetMetricsArguments {
        metricNames = metricNames == null ? null : List.copyOf(metricNames);
    }
}
