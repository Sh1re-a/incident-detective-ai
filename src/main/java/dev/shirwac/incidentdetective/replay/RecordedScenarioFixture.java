package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RecordedScenarioFixture(
        @NotNull @Valid Scenario scenario,
        @NotEmpty List<@NotNull @Valid Evidence> evidenceInventory,
        @NotEmpty List<@NotNull @Valid RecordedToolEvent> toolEvents,
        @NotNull @Valid Diagnosis recordedDiagnosis
) {
    public RecordedScenarioFixture {
        evidenceInventory = evidenceInventory == null
                ? null
                : List.copyOf(evidenceInventory);
        toolEvents = toolEvents == null ? null : List.copyOf(toolEvents);
    }
}
