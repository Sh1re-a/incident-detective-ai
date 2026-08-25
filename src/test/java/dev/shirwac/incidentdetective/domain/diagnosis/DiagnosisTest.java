package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAConsistentDiagnosis() {
        assertTrue(validator.validate(validDiagnosis()).isEmpty());
    }

    @Test
    void rejectsADiagnosisWithoutRootCauseAndService() {
        Diagnosis diagnosis = diagnosed(null, null, validDiagnosis().claims());

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsAClaimThatDoesNotMatchTheDiagnosis() {
        Diagnosis diagnosis = diagnosed(
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "INVENTORY_SCHEMA_MISMATCH",
                                "ev-log-001"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                )
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsADiagnosedClaimWithoutEvidence() {
        Diagnosis diagnosis = diagnosed(
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG"),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                )
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsDuplicateClaimKeys() {
        Diagnosis diagnosis = diagnosed(
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-log-001"
                        ),
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-runbook-001"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                )
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsASecondConflictingRootCauseClaim() {
        Diagnosis diagnosis = diagnosed(
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-log-001"
                        ),
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "INVENTORY_SCHEMA_MISMATCH",
                                "ev-log-002"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        )
                )
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void acceptsAnHonestInsufficientEvidenceResult() {
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The impact is visible, but the cause is not proven.",
                "The available evidence does not isolate a root cause.",
                List.of(claim(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE",
                        "ev-log-001"
                )),
                safeNextStep()
        );

        assertTrue(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsRootCauseClaimsWhenEvidenceIsInsufficient() {
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The impact is visible, but the cause is not proven.",
                "The available evidence does not isolate a root cause.",
                List.of(claim(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG",
                        "ev-log-001"
                )),
                safeNextStep()
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    @Test
    void rejectsANextStepWithoutHumanApproval() {
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The adapter timeout is lower than the upstream timeout.",
                validDiagnosis().claims(),
                new SafeNextStep("Roll back the timeout change.", false)
        );

        assertFalse(validator.validate(diagnosis).isEmpty());
    }

    private static Diagnosis validDiagnosis() {
        return diagnosed(
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-log-001",
                                "ev-runbook-001"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-trace-001"
                        ),
                        claim(
                                ClaimCode.CUSTOMER_IMPACT,
                                "CHECKOUT_FAILURES",
                                "ev-metric-001"
                        )
                )
        );
    }

    private static Diagnosis diagnosed(
            String rootCauseCode,
            String affectedService,
            List<Claim> claims
    ) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                rootCauseCode,
                affectedService,
                "Some customers cannot complete checkout.",
                "The adapter timeout is lower than the upstream timeout.",
                claims,
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
