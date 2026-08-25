package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.ExpectedClaim;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicVerifierEvidencePrecisionTest {

    private static final double TOLERANCE = 0.000_001;

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @Test
    void countsUniqueSupportedCitationTriples() {
        Diagnosis diagnosis = diagnosed(
                new Claim(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG",
                        "The timeout configuration caused the failures.",
                        List.of("ev-log-001", "ev-log-001", "ev-runbook-999")
                ),
                claim(
                        ClaimCode.AFFECTED_SERVICE,
                        "PAYMENT_ADAPTER",
                        "ev-trace-001"
                )
        );

        EvidencePrecision result = verifier.scoreEvidencePrecision(
                diagnosis,
                diagnosableGroundTruth()
        );

        assertTrue(result.applicable());
        assertEquals(2, result.supportedTriples());
        assertEquals(3, result.totalTriples());
        assertEquals(2.0 / 3.0, result.score(), TOLERANCE);
    }

    @Test
    void scoresZeroWhenADiagnosableCaseIsAbstained() {
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The impact is visible, but the cause is not proven.",
                "The available evidence does not isolate a root cause.",
                List.of(),
                safeNextStep()
        );

        EvidencePrecision result = verifier.scoreEvidencePrecision(
                diagnosis,
                diagnosableGroundTruth()
        );

        assertTrue(result.applicable());
        assertEquals(0, result.supportedTriples());
        assertEquals(0, result.totalTriples());
        assertEquals(0.0, result.score(), TOLERANCE);
    }

    @Test
    void excludesAbstentionCasesFromEvidencePrecision() {
        GroundTruth abstentionGroundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );

        EvidencePrecision result = verifier.scoreEvidencePrecision(
                diagnosed(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-log-001"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                ),
                abstentionGroundTruth
        );

        assertFalse(result.applicable());
        assertEquals(0, result.supportedTriples());
        assertEquals(0, result.totalTriples());
        assertNull(result.score());
    }

    private static GroundTruth diagnosableGroundTruth() {
        return new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        new ExpectedClaim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG"
                        ),
                        new ExpectedClaim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER"
                        )
                ),
                List.of(
                        new ClaimSupport(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                List.of("ev-log-001", "ev-runbook-001")
                        ),
                        new ClaimSupport(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                List.of("ev-trace-001")
                        )
                ),
                List.of()
        );
    }

    private static Diagnosis diagnosed(Claim... claims) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The payment adapter timeout is too low.",
                List.of(claims),
                safeNextStep()
        );
    }

    private static Claim claim(
            ClaimCode claimCode,
            String claimValueCode,
            String... evidenceIds
    ) {
        return new Claim(
                claimCode,
                claimValueCode,
                "Evidence-backed claim.",
                List.of(evidenceIds)
        );
    }

    private static SafeNextStep safeNextStep() {
        return new SafeNextStep(
                "Review and approve reverting the timeout configuration.",
                true
        );
    }
}
