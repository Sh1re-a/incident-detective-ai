package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
final class RecordedInvestigationScenarioCatalog
        implements InvestigationScenarioCatalog {

    private final RecordedScenarioRepository recordedScenarios;

    RecordedInvestigationScenarioCatalog(RecordedScenarioRepository recordedScenarios) {
        this.recordedScenarios = recordedScenarios;
    }

    @Override
    public Optional<Scenario> findById(String scenarioId) {
        return recordedScenarios.findById(scenarioId)
                .map(RecordedScenarioPackage::scenario);
    }

    @Override
    public List<Scenario> findAll() {
        return recordedScenarios.findAll().stream()
                .map(RecordedScenarioPackage::scenario)
                .toList();
    }
}
