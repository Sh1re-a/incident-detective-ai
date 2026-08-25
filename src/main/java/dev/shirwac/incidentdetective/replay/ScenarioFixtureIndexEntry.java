package dev.shirwac.incidentdetective.replay;

import jakarta.validation.constraints.NotBlank;

public record ScenarioFixtureIndexEntry(
        @NotBlank String scenarioId,
        @NotBlank String recordedResource,
        @NotBlank String groundTruthResource
) {
}
