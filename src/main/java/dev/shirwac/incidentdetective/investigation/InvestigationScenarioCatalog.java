package dev.shirwac.incidentdetective.investigation;

import dev.shirwac.incidentdetective.domain.scenario.Scenario;

import java.util.List;
import java.util.Optional;

/**
 * Scenario-only view for model orchestration. It deliberately exposes neither
 * the evidence inventory nor recorded answers and hidden ground truth.
 */
public interface InvestigationScenarioCatalog {

    Optional<Scenario> findById(String scenarioId);

    List<Scenario> findAll();
}
