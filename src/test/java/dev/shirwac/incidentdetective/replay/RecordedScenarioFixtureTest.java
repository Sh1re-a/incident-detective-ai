package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.scenario.InitialSymptom;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.scenario.TimeWindow;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordedScenarioFixtureTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsACompleteRecordedFixture() {
        assertTrue(validator.validate(validFixture()).isEmpty());
    }

    @Test
    void copiesMutableFixtureLists() {
        RecordedScenarioFixture original = validFixture();
        List<Evidence> evidence = new ArrayList<>(original.evidenceInventory());
        RecordedScenarioFixture fixture = new RecordedScenarioFixture(
                original.scenario(),
                evidence,
                original.toolEvents(),
                original.recordedDiagnosis()
        );

        evidence.clear();

        assertEquals(1, fixture.evidenceInventory().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> fixture.toolEvents().clear()
        );
    }

    private static RecordedScenarioFixture validFixture() {
        String scenarioId = "checkout-orders-at-risk-v1";
        Evidence evidence = new MetricEvidence(
                "ev-metric-001",
                scenarioId,
                Instant.parse("2026-08-25T08:01:00Z"),
                "Checkout error rate increased.",
                "metrics/checkout-error-rate",
                new MetricEvidence.MetricContent(
                        "checkout_error_rate",
                        0.31,
                        "ratio",
                        Map.of("region", "eu-north")
                )
        );
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Some customers cannot complete checkout.",
                "The payment adapter timeout is too low.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "ev-metric-001"
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "ev-metric-001"
                        )
                ),
                new SafeNextStep(
                        "Review and approve reverting the timeout configuration.",
                        true
                )
        );

        return new RecordedScenarioFixture(
                new Scenario(
                        scenarioId,
                        "Checkout errors threaten orders",
                        "Checkout failures increased shortly after a release.",
                        Instant.parse("2026-08-25T08:00:00Z"),
                        new TimeWindow(
                                Instant.parse("2026-08-25T07:55:00Z"),
                                Instant.parse("2026-08-25T08:15:00Z")
                        ),
                        List.of("CHECKOUT_API", "PAYMENT_ADAPTER"),
                        "A synthetic share of attempted orders cannot be completed.",
                        List.of(new InitialSymptom(
                                "CHECKOUT_FAILURE_RATE_HIGH",
                                "Checkout error rate is above its synthetic baseline.",
                                Instant.parse("2026-08-25T08:01:00Z")
                        )),
                        1
                ),
                List.of(evidence),
                List.of(new RecordedToolEvent(
                        "tool-event-001",
                        ToolName.GET_METRICS,
                        "Read checkout health metrics.",
                        List.of("ev-metric-001")
                )),
                diagnosis
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
}
