package dev.shirwac.incidentdetective.investigation;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Scenario data that tools may inspect without access to recorded answers or
 * hidden ground truth.
 */
public record InvestigationData(
        @NotNull @Valid Scenario scenario,
        @NotEmpty List<@NotNull @Valid Evidence> evidenceInventory
) {
    public InvestigationData {
        evidenceInventory = evidenceInventory == null
                ? null
                : List.copyOf(evidenceInventory);
    }
}
