package dev.shirwac.incidentdetective.investigation.tools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GetTraceArguments(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,127}$")
        String traceId
) {
}
