package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.verification.CitationSupportResult;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void keepsValidEvidenceIdsSeparateFromDirectClaimSupport() {
        Diagnosis weaklySupportedDiagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "INVENTORY_SCHEMA_MISMATCH",
                "INVENTORY_SERVICE",
                "Synthetic multi-item checkouts fail before payment.",
                "Checkout received an incompatible inventory response.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "INVENTORY_SCHEMA_MISMATCH",
                                "cic-v1-log-schema-mismatch"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "INVENTORY_SERVICE",
                                "cic-v1-metric-contract-errors"
                        ),
                        claim(
                                ClaimCode.TRIGGER,
                                "INVENTORY_SERVICE_RELEASE",
                                "cic-v1-log-schema-mismatch"
                        ),
                        claim(
                                ClaimCode.CUSTOMER_IMPACT,
                                "MULTI_ITEM_CHECKOUT_FAILURES",
                                "cic-v1-metric-failed-checkouts"
                        ),
                        claim(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "INVENTORY_CONTRACT_VALIDATION_ERRORS",
                                "cic-v1-metric-contract-errors"
                        )
                ),
                new SafeNextStep(
                        "Review a backward-compatible decoder with a human.",
                        true
                )
        );
        Set<String> seenIds = Set.of(
                "cic-v1-log-schema-mismatch",
                "cic-v1-metric-contract-errors",
                "cic-v1-metric-failed-checkouts"
        );

        CompletedInvestigationVerification result = verifier.verify(
                "checkout-cart-segment-failures-v1",
                weaklySupportedDiagnosis,
                seenIds
        );

        assertTrue(result.report().citationValidity().valid());
        assertTrue(result.comparison().rootCauseCorrect());
        assertTrue(result.comparison().affectedServiceCorrect());
        assertEquals(3, result.report().evidencePrecision().supportedTriples());
        assertEquals(5, result.report().evidencePrecision().totalTriples());
        assertEquals(0.6, result.report().evidencePrecision().score(), 0.000_001);
        assertEquals(2, result.report().evidencePrecision().citationSupport()
                .stream()
                .filter(support -> !support.supported())
                .count());
        assertTrue(result.report().evidencePrecision().citationSupport().contains(
                new CitationSupportResult(
                        ClaimCode.AFFECTED_SERVICE,
                        "INVENTORY_SERVICE",
                        "cic-v1-metric-contract-errors",
                        false
                )
        ));
        assertTrue(result.report().evidencePrecision().citationSupport().contains(
                new CitationSupportResult(
                        ClaimCode.TRIGGER,
                        "INVENTORY_SERVICE_RELEASE",
                        "cic-v1-log-schema-mismatch",
                        false
                )
        ));
    }

    private static Claim claim(
            ClaimCode code,
            String valueCode,
            String evidenceId
    ) {
        return new Claim(
                code,
                valueCode,
                "Synthetic claim under verification.",
                List.of(evidenceId)
        );
    }
}
