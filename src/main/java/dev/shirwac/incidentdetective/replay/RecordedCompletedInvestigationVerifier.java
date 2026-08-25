package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.verification.DeterministicVerifier;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
final class RecordedCompletedInvestigationVerifier
        implements CompletedInvestigationVerifier {

    private final RecordedScenarioRepository repository;
    private final DeterministicVerifier verifier;

    RecordedCompletedInvestigationVerifier(
            RecordedScenarioRepository repository,
            Validator validator
    ) {
        this.repository = repository;
        this.verifier = new DeterministicVerifier(validator);
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
        VerificationReport report = verifier.verify(
                diagnosis,
                seenEvidenceIds,
                groundTruth
        );
        DiagnosisCorrectness correctness = report.diagnosisCorrectness();
        ReplayComparison comparison = new ReplayComparison(
                groundTruth.expectedStatus(),
                groundTruth.rootCauseCode(),
                groundTruth.affectedService(),
                correctness.rootCauseCorrect(),
                correctness.affectedServiceCorrect(),
                correctness.abstentionCorrect()
        );
        return new CompletedInvestigationVerification(report, comparison);
    }
}
