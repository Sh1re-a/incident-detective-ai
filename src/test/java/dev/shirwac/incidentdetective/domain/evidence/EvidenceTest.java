package dev.shirwac.incidentdetective.domain.evidence;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsAllFourEvidenceTypes() {
        List<Evidence> evidence = List.of(
                new MetricEvidence(
                        "ev-metric-001",
                        "checkout-payment-timeout-v1",
                        Instant.parse("2026-08-25T08:01:00Z"),
                        "Checkout error rate increased.",
                        "metrics/checkout-error-rate",
                        new MetricEvidence.MetricContent(
                                "checkout_error_rate",
                                0.31,
                                "ratio",
                                Map.of("region", "eu-north")
                        )
                ),
                new LogEvidence(
                        "ev-log-001",
                        "checkout-payment-timeout-v1",
                        Instant.parse("2026-08-25T08:01:05Z"),
                        "Payment adapter timed out.",
                        "logs/payment-adapter/42",
                        new LogEvidence.LogContent(
                                "PAYMENT_ADAPTER",
                                "ERROR",
                                "Upstream payment request timed out.",
                                Map.of("timeout_ms", "1500")
                        )
                ),
                new TraceEvidence(
                        "ev-trace-001",
                        "checkout-payment-timeout-v1",
                        Instant.parse("2026-08-25T08:01:07Z"),
                        "Checkout waited on the payment adapter.",
                        "traces/trace-001",
                        new TraceEvidence.TraceContent(
                                "trace-001",
                                List.of(new TraceEvidence.TraceSpan(
                                        "span-001",
                                        "PAYMENT_ADAPTER",
                                        "authorize-payment",
                                        1500,
                                        "ERROR"
                                ))
                        )
                ),
                new RunbookEvidence(
                        "ev-runbook-001",
                        "checkout-payment-timeout-v1",
                        "Runbook describes the payment timeout setting.",
                        "runbooks/payment-timeout#chunk-2",
                        new RunbookEvidence.RunbookContent(
                                "payment-timeout",
                                "chunk-2",
                                "1.0.0",
                                "Compare the adapter timeout with the upstream timeout."
                        )
                )
        );

        evidence.forEach(item -> assertEquals(0, validator.validate(item).size()));
        assertEquals(
                List.of(EvidenceType.METRIC, EvidenceType.LOG, EvidenceType.TRACE, EvidenceType.RUNBOOK),
                evidence.stream().map(Evidence::type).toList()
        );
    }

    @Test
    void rejectsInvalidNestedEvidenceContent() {
        TraceEvidence evidence = new TraceEvidence(
                "ev-trace-001",
                "checkout-payment-timeout-v1",
                Instant.parse("2026-08-25T08:01:07Z"),
                "Checkout waited on the payment adapter.",
                "traces/trace-001",
                new TraceEvidence.TraceContent(
                        "trace-001",
                        List.of(new TraceEvidence.TraceSpan(
                                "span-001",
                                "payment-adapter",
                                "authorize-payment",
                                -1,
                                "ERROR"
                        ))
                )
        );

        assertEquals(2, validator.validate(evidence).size());
    }
}
