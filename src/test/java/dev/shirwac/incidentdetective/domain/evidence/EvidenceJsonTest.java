package dev.shirwac.incidentdetective.domain.evidence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class EvidenceJsonTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void roundTripsEveryEvidenceTypeWithStableSnakeCaseFields() throws Exception {
        for (Evidence original : allEvidenceTypes()) {
            String json = jsonMapper.writeValueAsString(original);

            assertTrue(json.contains("\"evidence_type\":\""
                    + original.type().name().toLowerCase() + "\""));
            assertTrue(json.contains("\"evidence_id\""));
            assertTrue(json.contains("\"scenario_id\""));
            assertFalse(json.contains("\"type\""));
            assertEquals(original, jsonMapper.readValue(json, Evidence.class));
        }
    }

    private static List<Evidence> allEvidenceTypes() {
        return List.of(
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
    }
}
