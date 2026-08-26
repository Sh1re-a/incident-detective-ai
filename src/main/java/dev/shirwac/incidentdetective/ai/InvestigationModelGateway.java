package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;

import java.time.Duration;
import java.util.List;

public interface InvestigationModelGateway {

    CollectionModelResult collect(
            Scenario scenario,
            List<String> availableMetricNames,
            List<Evidence> collectedEvidence,
            CollectionToolBudget toolBudget,
            int round,
            Duration timeout
    );

    SynthesisModelResult synthesize(
            Scenario scenario,
            List<Evidence> collectedEvidence,
            Duration timeout
    );
}
