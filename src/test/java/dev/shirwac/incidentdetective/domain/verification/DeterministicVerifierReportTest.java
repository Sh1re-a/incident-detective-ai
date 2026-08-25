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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicVerifierReportTest {

    private static final double TOLERANCE = 0.000_001;

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @Test
    void returnsIndependentGreenResultsForACorrectDiagnosis() {
        VerificationReport report = verifier.verify(
                validDiagnosis("ev-log-001"),
                Set.of("ev-log-001", "ev-trace-001"),
                validGroundTruth()
        );

        assertTrue(report.diagnosisSchemaPass());
        assertTrue(report.groundTruthSchemaPass());
        assertTrue(report.citationValidity().valid());
        assertEquals(2, report.evidencePrecision().supportedTriples());
        assertEquals(2, report.evidencePrecision().totalTriples());
        assertEquals(1.0, report.evidencePrecision().score(), TOLERANCE);
        assertTrue(report.diagnosisCorrectness().rootCauseCorrect());
        assertTrue(report.diagnosisCorrectness().affectedServiceCorrect());
        assertTrue(report.hardErrors().isEmpty());
    }

    @Test
    void keepsCitationValiditySeparateFromEvidenceSupport() {
        VerificationReport report = verifier.verify(
                validDiagnosis("ev-runbook-001"),
                Set.of("ev-trace-001"),
                validGroundTruth()
        );

        assertTrue(report.diagnosisSchemaPass());
        assertTrue(report.groundTruthSchemaPass());
        assertFalse(report.citationValidity().valid());
        assertEquals(
                List.of("ev-runbook-001"),
                report.citationValidity().unknownEvidenceIds()
        );
        assertEquals(2, report.evidencePrecision().supportedTriples());
        assertEquals(2, report.evidencePrecision().totalTriples());
        assertEquals(
                List.of(VerificationErrorCode.UNKNOWN_EVIDENCE_ID),
                report.hardErrors()
        );
    }

    @Test
    void returnsAReportInsteadOfThrowingForInvalidDiagnosisSchema() {
        Diagnosis invalidDiagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The payment adapter timeout is too low.",
                List.of(),
                safeNextStep()
        );

        VerificationReport report = verifier.verify(
                invalidDiagnosis,
                Set.of(),
                validGroundTruth()
        );

        assertFalse(report.diagnosisSchemaPass());
        assertTrue(report.groundTruthSchemaPass());
        assertTrue(report.citationValidity().valid());
        assertEquals(0.0, report.evidencePrecision().score(), TOLERANCE);
        assertFalse(report.diagnosisCorrectness().evaluated());
        assertEquals(
                List.of(VerificationErrorCode.DIAGNOSIS_SCHEMA_INVALID),
                report.hardErrors()
        );
    }

    @Test
    void separatesInvalidGroundTruthFromTheModelSchemaMetric() {
        GroundTruth invalidGroundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );

        VerificationReport report = verifier.verify(
                validDiagnosis("ev-log-001"),
                Set.of("ev-log-001", "ev-trace-001"),
                invalidGroundTruth
        );

        assertTrue(report.diagnosisSchemaPass());
        assertFalse(report.groundTruthSchemaPass());
        assertFalse(report.evidencePrecision().applicable());
        assertFalse(report.diagnosisCorrectness().evaluated());
        assertEquals(
                List.of(VerificationErrorCode.GROUND_TRUTH_SCHEMA_INVALID),
                report.hardErrors()
        );
    }

    private static Diagnosis validDiagnosis(String rootCauseEvidenceId) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The payment adapter timeout is too low.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                rootCauseEvidenceId
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                ),
                safeNextStep()
        );
    }

    private static GroundTruth validGroundTruth() {
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

    private static Claim claim(
            ClaimCode claimCode,
            String claimValueCode,
            String evidenceId
    ) {
        return new Claim(
                claimCode,
                claimValueCode,
                "Evidence-backed claim.",
                List.of(evidenceId)
        );
    }

    private static SafeNextStep safeNextStep() {
        return new SafeNextStep(
                "Review and approve reverting the timeout configuration.",
                true
        );
    }
}
