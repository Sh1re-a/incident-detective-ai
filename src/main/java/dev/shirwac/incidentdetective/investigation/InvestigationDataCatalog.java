package dev.shirwac.incidentdetective.investigation;

import java.util.Optional;

public interface InvestigationDataCatalog {

    Optional<InvestigationData> findById(String scenarioId);
}
