package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTimeoutTelemetryTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-14T10:00:00Z");

    @Test
    void createsHealthyTrafficFollowedByExactlyThreeHttp5xxResponses() {
        PaymentTimeoutTelemetry telemetry = PaymentTimeoutTelemetry.startingAt(
                "incident-lab-payment-timeout-v1",
                STARTED_AT
        );

        assertEquals(6, telemetry.logs().size());
        assertEquals(List.of("200", "200", "200", "504", "504", "504"),
                telemetry.logs().stream().map(this::httpStatus).toList());
        assertEquals(
                List.of(
                        "synthetic-payment-001",
                        "synthetic-payment-002",
                        "synthetic-payment-003",
                        "synthetic-payment-004",
                        "synthetic-payment-005",
                        "synthetic-payment-006"
                ),
                telemetry.logs().stream()
                        .map(log -> log.content().attributes().get("request_id"))
                        .toList()
        );
        assertEquals(
                List.of(
                        "incident-lab-payment-timeout-v1-payment-http-001",
                        "incident-lab-payment-timeout-v1-payment-http-002",
                        "incident-lab-payment-timeout-v1-payment-http-003",
                        "incident-lab-payment-timeout-v1-payment-http-004",
                        "incident-lab-payment-timeout-v1-payment-http-005",
                        "incident-lab-payment-timeout-v1-payment-http-006"
                ),
                telemetry.logs().stream().map(LogEvidence::evidenceId).toList()
        );
        assertEquals(3, telemetry.logs().stream().filter(this::isHttp5xx).count());
        assertTrue(telemetry.logs().stream().allMatch(this::hasStableEnvelope));

        List<LogEvidence> failures = telemetry.logs().stream()
                .filter(this::isHttp5xx)
                .toList();
        assertTrue(Duration.between(
                failures.getFirst().observedAt(),
                failures.getLast().observedAt()
        ).compareTo(Http5xxBurstRule.WINDOW) <= 0);
    }

    @Test
    void sameInputProducesTheSameImmutableSeries() {
        PaymentTimeoutTelemetry first = PaymentTimeoutTelemetry.startingAt(
                "incident-lab-payment-timeout-v1",
                STARTED_AT
        );
        PaymentTimeoutTelemetry second = PaymentTimeoutTelemetry.startingAt(
                "incident-lab-payment-timeout-v1",
                STARTED_AT
        );

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, () -> first.logs().clear());
        assertThrows(UnsupportedOperationException.class, () ->
                first.logs().getFirst().content().attributes().clear()
        );
    }

    private String httpStatus(LogEvidence log) {
        return log.content().attributes().get(Http5xxBurstRule.HTTP_STATUS_ATTRIBUTE);
    }

    private boolean isHttp5xx(LogEvidence log) {
        int status = Integer.parseInt(httpStatus(log));
        return status >= 500 && status <= 599;
    }

    private boolean hasStableEnvelope(LogEvidence log) {
        Map<String, String> attributes = log.content().attributes();
        return "incident-lab-payment-timeout-v1".equals(log.scenarioId())
                && PaymentTimeoutTelemetry.SERVICE.equals(log.content().service())
                && PaymentTimeoutTelemetry.EVENT_KIND.equals(attributes.get("event_kind"))
                && PaymentTimeoutTelemetry.METHOD.equals(attributes.get("method"))
                && PaymentTimeoutTelemetry.PATH.equals(attributes.get("path"))
                && "true".equals(attributes.get("synthetic"));
    }
}
