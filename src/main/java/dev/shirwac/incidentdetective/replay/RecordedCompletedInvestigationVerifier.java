package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
final class RecordedCompletedInvestigationVerifier
        implements CompletedInvestigationVerifier {

    private final RecordedScenarioRepository repository;
    private final GroundTruthInvestigationVerifier verifier;

    RecordedCompletedInvestigationVerifier(
            RecordedScenarioRepository repository,
            GroundTruthInvestigationVerifier verifier
    ) {
        this.repository = repository;
        this.verifier = verifier;
    }

    @Override
    public CompletedInvestigationVerification verify(
            String scenarioId,
            Diagnosis diagnosis,
            Set<String> seenEvidenceIds
    ) {
        GroundTruth groundTruth = repository.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId))
                .groundTruth();
        return verifier.verify(
                groundTruth,
                diagnosis,
                seenEvidenceIds
        );
    }
}
