package dev.shirwac.incidentdetective.investigation.tools;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RetrieveRunbooksArguments(
        @NotBlank @Size(max = 160) String query,
        @Min(1) @Max(4) int maxResults
) {
}
