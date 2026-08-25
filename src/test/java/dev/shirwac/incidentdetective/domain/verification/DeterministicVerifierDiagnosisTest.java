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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicVerifierDiagnosisTest {

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @Test
    void verifiesRootCauseAndAffectedServiceSeparately() {
        DiagnosisCorrectness result = verifier.verifyDiagnosis(
                diagnosed("PAYMENT_TIMEOUT_CONFIG", "PAYMENT_ADAPTER"),
                diagnosableGroundTruth()
        );

        assertTrue(result.evaluated());
        assertTrue(result.diagnosisApplicable());
        assertTrue(result.rootCauseCorrect());
        assertTrue(result.affectedServiceCorrect());
        assertFalse(result.abstentionCorrect());
    }

    @Test
    void keepsACorrectRootCauseWhenTheServiceIsWrong() {
        DiagnosisCorrectness result = verifier.verifyDiagnosis(
                diagnosed("PAYMENT_TIMEOUT_CONFIG", "CHECKOUT_API"),
                diagnosableGroundTruth()
        );

        assertTrue(result.rootCauseCorrect());
        assertFalse(result.affectedServiceCorrect());
    }

    @Test
    void rejectsAWrongRootCauseDespiteAValidService() {
        DiagnosisCorrectness result = verifier.verifyDiagnosis(
                diagnosed("INVENTORY_SCHEMA_MISMATCH", "PAYMENT_ADAPTER"),
                diagnosableGroundTruth()
        );

        assertFalse(result.rootCauseCorrect());
        assertTrue(result.affectedServiceCorrect());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void evaluatesExpectedAbstention(boolean modelAbstained) {
        Diagnosis diagnosis = modelAbstained
                ? insufficientEvidence()
                : diagnosed("PAYMENT_TIMEOUT_CONFIG", "PAYMENT_ADAPTER");

        DiagnosisCorrectness result = verifier.verifyDiagnosis(
                diagnosis,
                abstentionGroundTruth()
        );

        assertTrue(result.evaluated());
        assertFalse(result.diagnosisApplicable());
        assertFalse(result.rootCauseCorrect());
        assertFalse(result.affectedServiceCorrect());
        assertEquals(modelAbstained, result.abstentionCorrect());
    }

    @Test
    void doesNotCountAbstentionAsCorrectForADiagnosableCase() {
        DiagnosisCorrectness result = verifier.verifyDiagnosis(
                insufficientEvidence(),
                diagnosableGroundTruth()
        );

        assertTrue(result.diagnosisApplicable());
        assertFalse(result.rootCauseCorrect());
        assertFalse(result.affectedServiceCorrect());
        assertFalse(result.abstentionCorrect());
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
                                List.of("ev-log-001")
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

    private static GroundTruth abstentionGroundTruth() {
        return new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static Diagnosis diagnosed(String rootCauseCode, String affectedService) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                rootCauseCode,
                affectedService,
                "Some customers cannot complete checkout.",
                "The checkout failure has an identified cause.",
                List.of(
                        claim(ClaimCode.ROOT_CAUSE, rootCauseCode, "ev-log-001"),
                        claim(ClaimCode.AFFECTED_SERVICE, affectedService, "ev-trace-001")
                ),
                safeNextStep()
        );
    }

    private static Diagnosis insufficientEvidence() {
        return new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The impact is visible, but the cause is not proven.",
                "The available evidence does not isolate a root cause.",
                List.of(),
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
                "Review and approve the recommended change.",
                true
        );
    }
}
