package dev.shirwac.incidentdetective.investigation;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.verification.DeterministicVerifier;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Applies the same deterministic post-model verification to catalog and
 * request-local synthetic incidents.
 */
@Service
public final class GroundTruthInvestigationVerifier {

    private final DeterministicVerifier verifier;

    public GroundTruthInvestigationVerifier(Validator validator) {
        this.verifier = new DeterministicVerifier(validator);
    }

    public CompletedInvestigationVerification verify(
            GroundTruth groundTruth,
            Diagnosis diagnosis,
            Set<String> seenEvidenceIds
    ) {
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
