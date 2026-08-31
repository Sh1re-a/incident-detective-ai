package dev.shirwac.incidentdetective.domain.groundtruth;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundTruthTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsACompleteGroundTruth() {
        assertTrue(validator.validate(validGroundTruth()).isEmpty());
    }

    @Test
    void rejectsInvalidCanonicalCodes() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "payment-timeout",
                "PAYMENT_ADAPTER",
                validGroundTruth().expectedClaims(),
                validGroundTruth().claimSupport(),
                validGroundTruth().relevantRunbooks()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void copiesMutableLists() {
        List<ExpectedClaim> expectedClaims = new ArrayList<>(validGroundTruth().expectedClaims());
        GroundTruth groundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                expectedClaims,
                validGroundTruth().claimSupport(),
                validGroundTruth().relevantRunbooks()
        );

        expectedClaims.clear();

        assertEquals(2, groundTruth.expectedClaims().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> groundTruth.expectedClaims().clear()
        );
    }

    @Test
    void rejectsDiagnosedTruthWithoutRootCauseAndService() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                null,
                null,
                validGroundTruth().expectedClaims(),
                validGroundTruth().claimSupport(),
                validGroundTruth().relevantRunbooks()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsAConflictingRootCauseClaim() {
        GroundTruth groundTruth = new GroundTruth(
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
                                ClaimCode.ROOT_CAUSE,
                                "INVENTORY_SCHEMA_MISMATCH"
                        ),
                        new ExpectedClaim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER"
                        )
                ),
                List.of(
                        support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-log-001"),
                        support(ClaimCode.ROOT_CAUSE, "INVENTORY_SCHEMA_MISMATCH", "ev-log-002"),
                        support(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-trace-001")
                ),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsANonCanonicalExpectedClaim() {
        GroundTruth groundTruth = new GroundTruth(
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
                        ),
                        new ExpectedClaim(
                                ClaimCode.CUSTOMER_IMPACT,
                                "CHECKOUT_FAILURES"
                        )
                ),
                List.of(
                        support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-log-001"),
                        support(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-trace-001"),
                        support(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_FAILURES", "ev-metric-001")
                ),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsSupportWithoutAnExpectedClaim() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                validGroundTruth().expectedClaims(),
                List.of(
                        support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-log-001"),
                        support(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-trace-001"),
                        support(ClaimCode.TRIGGER, "RELEASE_2026_08_25", "ev-log-002")
                ),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void acceptsAnAbstentionGroundTruth() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(new ExpectedClaim(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE"
                )),
                List.of(support(
                        ClaimCode.MISSING_EVIDENCE,
                        "PAYMENT_PROVIDER_RESPONSE",
                        "ev-log-001"
                )),
                List.of()
        );

        assertTrue(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void acceptsOnlyReachableClaimTypesForAnAbstentionGroundTruth() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(
                        new ExpectedClaim(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "PAYMENT_LATENCY_SPIKE"
                        ),
                        new ExpectedClaim(
                                ClaimCode.MISSING_EVIDENCE,
                                "PAYMENT_PROVIDER_RESPONSE"
                        )
                ),
                List.of(
                        support(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "PAYMENT_LATENCY_SPIKE",
                                "ev-metric-001"
                        ),
                        support(
                                ClaimCode.MISSING_EVIDENCE,
                                "PAYMENT_PROVIDER_RESPONSE",
                                "ev-log-001"
                        )
                ),
                List.of()
        );

        assertTrue(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsAnUnreachableCustomerImpactClaimForAnAbstentionGroundTruth() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(new ExpectedClaim(
                        ClaimCode.CUSTOMER_IMPACT,
                        "CHECKOUT_PAYMENT_FAILURES"
                )),
                List.of(support(
                        ClaimCode.CUSTOMER_IMPACT,
                        "CHECKOUT_PAYMENT_FAILURES",
                        "ev-metric-001"
                )),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsRootCauseFieldsForAnAbstentionCase() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                List.of(),
                List.of(),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsRootCauseClaimsForAnAbstentionCase() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-missing-provider-data-v1",
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(new ExpectedClaim(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG"
                )),
                List.of(support(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG",
                        "ev-log-001"
                )),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
    }

    @Test
    void rejectsDuplicateClaimSupportKeys() {
        GroundTruth groundTruth = new GroundTruth(
                "checkout-timeout-v1",
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                validGroundTruth().expectedClaims(),
                List.of(
                        support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-log-001"),
                        support(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", "ev-runbook-001"),
                        support(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", "ev-trace-001")
                ),
                List.of()
        );

        assertFalse(validator.validate(groundTruth).isEmpty());
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
                List.of(new RunbookReference(
                        "payment-adapter-timeouts",
                        "timeout-precedence",
                        "1.0"
                ))
        );
    }

    private static ClaimSupport support(
            ClaimCode claimCode,
            String claimValueCode,
            String... evidenceIds
    ) {
        return new ClaimSupport(
                claimCode,
                claimValueCode,
                List.of(evidenceIds)
        );
    }
}
