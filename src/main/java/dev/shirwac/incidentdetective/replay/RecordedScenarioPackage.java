package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;

import java.util.List;
import java.util.Map;

record RecordedScenarioPackage(
        Scenario scenario,
        Map<String, Evidence> evidenceById,
        List<RecordedToolEvent> toolEvents,
        Diagnosis recordedDiagnosis,
        GroundTruth groundTruth
) {
    RecordedScenarioPackage {
        evidenceById = Map.copyOf(evidenceById);
        toolEvents = List.copyOf(toolEvents);
    }
}
