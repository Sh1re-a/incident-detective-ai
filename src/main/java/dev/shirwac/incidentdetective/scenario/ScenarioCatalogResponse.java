package dev.shirwac.incidentdetective.scenario;

import dev.shirwac.incidentdetective.domain.scenario.Scenario;

import java.util.List;

public record ScenarioCatalogResponse(List<Scenario> scenarios) {

    public ScenarioCatalogResponse {
        scenarios = List.copyOf(scenarios);
    }
}
