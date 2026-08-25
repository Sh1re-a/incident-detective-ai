package dev.shirwac.incidentdetective.domain.scenario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public record Scenario(
        @NotBlank String scenarioId,
        @NotBlank String title,
        @NotBlank String description,
        @NotNull Instant incidentStartedAt,
        @NotNull @Valid TimeWindow timeWindow,
        @NotEmpty List<@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$") String> affectedServices,
        @NotBlank String businessImpactSummary,
        @NotEmpty List<@Valid InitialSymptom> initialSymptoms,
        @Min(1) int version
) {
    public Scenario {
        affectedServices = affectedServices == null ? null : List.copyOf(affectedServices);
        initialSymptoms = initialSymptoms == null ? null : List.copyOf(initialSymptoms);
    }
}
