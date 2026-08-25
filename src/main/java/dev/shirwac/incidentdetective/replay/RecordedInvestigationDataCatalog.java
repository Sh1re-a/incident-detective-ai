package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
final class RecordedInvestigationDataCatalog implements InvestigationDataCatalog {

    private final RecordedScenarioRepository recordedScenarios;

    RecordedInvestigationDataCatalog(RecordedScenarioRepository recordedScenarios) {
        this.recordedScenarios = recordedScenarios;
    }

    @Override
    public Optional<InvestigationData> findById(String scenarioId) {
        return recordedScenarios.findById(scenarioId).map(scenarioPackage -> {
            List<Evidence> evidence = scenarioPackage.evidenceById().values().stream()
                    .sorted(Comparator.comparing(Evidence::evidenceId))
                    .toList();
            return new InvestigationData(scenarioPackage.scenario(), evidence);
        });
    }
}
