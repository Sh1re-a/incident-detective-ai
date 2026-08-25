package dev.shirwac.incidentdetective.replay;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ScenarioFixtureIndex(
        @NotEmpty List<@NotNull @Valid ScenarioFixtureIndexEntry> scenarios
) {
    public ScenarioFixtureIndex {
        scenarios = scenarios == null ? null : List.copyOf(scenarios);
    }
}
