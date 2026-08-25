package dev.shirwac.incidentdetective.replay;

import java.util.List;
import java.util.Optional;

interface RecordedScenarioRepository {

    Optional<RecordedScenarioPackage> findById(String scenarioId);

    List<RecordedScenarioPackage> findAll();
}
