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

class DeterministicVerifierClaimCoverageTest {

    private static final double TOLERANCE = 0.000_001;

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @Test
    void exposesAnIncompleteAnswerEvenWhenReturnedCitationsArePerfect() {
        Diagnosis diagnosis = diagnosed(
                claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-root"),
                claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-service")
        );

        VerificationReport report = verifier.verify(
                diagnosis,
                Set.of("ev-root", "ev-service"),
                diagnosableGroundTruth()
        );

        assertEquals(1.0, report.evidencePrecision().score(), TOLERANCE);
        assertEquals(2, report.claimCoverage().matchedClaimCount());
        assertEquals(5, report.claimCoverage().referenceClaimCount());
        assertEquals(0.4, report.claimCoverage().score(), TOLERANCE);
        assertTrue(report.hardErrors().isEmpty());
    }

    @Test
    void requiresBothClaimCodeAndValueToMatch() {
        Diagnosis diagnosis = diagnosed(
                claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-root"),
                claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-service"),
                claim(ClaimCode.TRIGGER, "INVENTORY_SERVICE_RELEASE", "ev-trigger"),
                claim(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES", "ev-impact"),
                claim(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE", "ev-symptom")
        );

        ClaimCoverage coverage = verifier.scoreClaimCoverage(
                diagnosis,
                diagnosableGroundTruth()
        );

        assertEquals(4, coverage.matchedClaimCount());
        assertEquals(5, coverage.referenceClaimCount());
        assertEquals(0.8, coverage.score(), TOLERANCE);
    }

    @Test
    void reportsFullCoverageForAllFiveReferenceClaims() {
        ClaimCoverage coverage = verifier.scoreClaimCoverage(
                diagnosed(
                        claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-root"),
                        claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-service"),
                        claim(ClaimCode.TRIGGER, "PAYMENT_ADAPTER_RELEASE", "ev-trigger"),
                        claim(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES", "ev-impact"),
                        claim(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE", "ev-symptom")
                ),
                diagnosableGroundTruth()
        );

        assertEquals(1.0, coverage.score(), TOLERANCE);
    }

    @Test
    void scoresAnInvalidDiagnosisAsZeroAgainstTheFixedReference() {
        Diagnosis invalidDiagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The timeout is too low.",
                List.of(),
                safeNextStep()
        );

        VerificationReport report = verifier.verify(
                invalidDiagnosis,
                Set.of(),
                diagnosableGroundTruth()
        );

        assertFalse(report.diagnosisSchemaPass());
        assertEquals(0, report.claimCoverage().matchedClaimCount());
        assertEquals(5, report.claimCoverage().referenceClaimCount());
        assertEquals(0.0, report.claimCoverage().score(), TOLERANCE);
    }

    @Test
    void doesNotEvaluateCoverageAgainstInvalidGroundTruth() {
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
                diagnosed(
                        claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-root"),
                        claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-service")
                ),
                Set.of("ev-root", "ev-service"),
                invalidGroundTruth
        );

        assertFalse(report.claimCoverage().applicable());
    }

    @Test
    void scoresExpectedMissingEvidenceForAnAbstentionCase() {
        GroundTruth abstentionGroundTruth = new GroundTruth(
                "checkout-provider-gap-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(new ExpectedClaim(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE"
                )),
                List.of(new ClaimSupport(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE",
                        List.of("ev-gap")
                )),
                List.of()
        );
        Diagnosis abstention = new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The impact is visible, but the cause is not proven.",
                "A provider response is needed to isolate the cause.",
                List.of(claim(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE",
                        "ev-gap"
                )),
                safeNextStep()
        );

        VerificationReport report = verifier.verify(
                abstention,
                Set.of("ev-gap"),
                abstentionGroundTruth
        );

        assertTrue(report.diagnosisCorrectness().abstentionCorrect());
        assertEquals(1.0, report.claimCoverage().score(), TOLERANCE);
    }

    @Test
    void leavesEmptyAbstentionReferenceOutOfCoverage() {
        GroundTruth abstentionGroundTruth = new GroundTruth(
                "checkout-provider-gap-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );

        ClaimCoverage coverage = verifier.scoreClaimCoverage(
                new Diagnosis(
                        DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                        null,
                        null,
                        "The impact is visible, but the cause is not proven.",
                        "The available evidence does not isolate a root cause.",
                        List.of(),
                        safeNextStep()
                ),
                abstentionGroundTruth
        );

        assertFalse(coverage.applicable());
    }

    private static GroundTruth diagnosableGroundTruth() {
        List<ExpectedClaim> expectedClaims = List.of(
                expected(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG"),
                expected(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER"),
                expected(ClaimCode.TRIGGER, "PAYMENT_ADAPTER_RELEASE"),
                expected(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES"),
                expected(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE")
        );
        List<ClaimSupport> claimSupport = List.of(
                support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-root"),
                support(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-service"),
                support(ClaimCode.TRIGGER, "PAYMENT_ADAPTER_RELEASE", "ev-trigger"),
                support(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES", "ev-impact"),
                support(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE", "ev-symptom")
        );
        return new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                expectedClaims,
                claimSupport,
                List.of()
        );
    }

    private static ExpectedClaim expected(ClaimCode code, String value) {
        return new ExpectedClaim(code, value);
    }

    private static ClaimSupport support(
            ClaimCode code,
            String value,
            String evidenceId
    ) {
        return new ClaimSupport(code, value, List.of(evidenceId));
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
                "Review the evidence before a human approves any change.",
                true
        );
    }
}
