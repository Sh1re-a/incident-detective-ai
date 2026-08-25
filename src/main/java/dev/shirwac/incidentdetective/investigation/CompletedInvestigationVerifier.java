package dev.shirwac.incidentdetective.investigation;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;

import java.util.Set;

/**
 * Opens hidden ground truth only after model work is complete and returns a
 * sanitized comparison.
 */
public interface CompletedInvestigationVerifier {

    CompletedInvestigationVerification verify(
            String scenarioId,
            Diagnosis diagnosis,
            Set<String> seenEvidenceIds
    );
}
