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
}
