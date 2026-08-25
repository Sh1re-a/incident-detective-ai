package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RecordedCompletedInvestigationVerifierTest {

    @Autowired
    private CompletedInvestigationVerifier verifier;

    @Autowired
    private RecordedScenarioRepository repository;

    @Test
    void returnsOnlyTheSanitizedReportAndComparison() {
        RecordedScenarioPackage scenarioPackage = repository
                .findById("checkout-orders-at-risk-v1")
                .orElseThrow();
        Diagnosis diagnosis = scenarioPackage.recordedDiagnosis();
        Set<String> seenIds = scenarioPackage.evidenceById().keySet();

        CompletedInvestigationVerification result = verifier.verify(
                "checkout-orders-at-risk-v1",
                diagnosis,
                seenIds
        );

        assertTrue(result.report().hardErrors().isEmpty());
        assertTrue(result.comparison().rootCauseCorrect());
        assertTrue(result.comparison().affectedServiceCorrect());
    }
}
