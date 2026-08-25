package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicVerifierCitationTest {

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @Test
    void acceptsOnlyEvidenceIdsReturnedToTheModel() {
        CitationValidity result = verifier.verifyCitations(
                diagnosisWithEvidence("ev-log-001", "ev-trace-001"),
                Set.of("ev-log-001", "ev-trace-001", "ev-runbook-001")
        );

        assertTrue(result.valid());
        assertTrue(result.unknownEvidenceIds().isEmpty());
    }

    @Test
    void reportsUnknownIdsOnceInStableOrder() {
        CitationValidity result = verifier.verifyCitations(
                diagnosisWithEvidence(
                        "ev-trace-999",
                        "ev-log-001",
                        "ev-trace-999",
                        "ev-log-999"
                ),
                Set.of("ev-log-001", "ev-trace-001")
        );

        assertFalse(result.valid());
        assertEquals(
                List.of("ev-log-999", "ev-trace-999"),
                result.unknownEvidenceIds()
        );
    }

    private static Diagnosis diagnosisWithEvidence(String... evidenceIds) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The payment adapter timeout is too low.",
                List.of(
                        new Claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "The timeout configuration caused the failures.",
                                List.of(evidenceIds)
                        ),
                        new Claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "The payment adapter is affected.",
                                List.of("ev-trace-001")
                        )
                ),
                new SafeNextStep(
                        "Review and approve reverting the timeout configuration.",
                        true
                )
        );
    }
}
