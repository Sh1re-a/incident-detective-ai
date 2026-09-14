package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Http5xxBurstRuleTest {

    private static final Instant STARTED_AT = Instant.parse("2026-09-14T10:00:00Z");
    private final Http5xxBurstRule rule = new Http5xxBurstRule();

    @Test
    void fixedPaymentSeriesRaisesOneDeterministicEvidenceLinkedAlarm() {
        PaymentTimeoutTelemetry telemetry = PaymentTimeoutTelemetry.startingAt(
                "incident-lab-payment-timeout-v1",
                STARTED_AT
        );

        AlarmReceipt receipt = rule.evaluate(telemetry.logs()).orElseThrow();

        assertEquals(Http5xxBurstRule.RULE_ID, receipt.ruleId());
        assertEquals(telemetry.scenarioId(), receipt.scenarioId());
        assertEquals(PaymentTimeoutTelemetry.SERVICE, receipt.service());
        assertEquals(STARTED_AT.plusSeconds(45), receipt.triggeredAt());
        assertEquals(STARTED_AT.plusSeconds(20), receipt.windowStartedAt());
        assertEquals(3, receipt.threshold());
        assertEquals(3, receipt.observedFailures());
        assertEquals(30, receipt.windowSeconds());
        assertEquals(
                telemetry.logs().subList(3, 6).stream()
                        .map(LogEvidence::evidenceId)
                        .toList(),
                receipt.evidenceIds()
        );
    }

    @Test
    void evaluationIsIndependentOfInputOrder() {
        PaymentTimeoutTelemetry telemetry = PaymentTimeoutTelemetry.startingAt(
                "incident-lab-payment-timeout-v1",
                STARTED_AT
        );
        List<LogEvidence> reversed = new ArrayList<>(telemetry.logs());
        Collections.reverse(reversed);

        assertEquals(rule.evaluate(telemetry.logs()), rule.evaluate(reversed));
    }

    @Test
    void twoFailuresOrThreeFailuresOutsideTheWindowDoNotRaiseAnAlarm() {
        List<LogEvidence> twoFailures = List.of(
                log("scenario-a", "PAYMENT_ADAPTER", "a-1", STARTED_AT, "500"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-2", STARTED_AT.plusSeconds(10), "502")
        );
        List<LogEvidence> outsideWindow = List.of(
                log("scenario-a", "PAYMENT_ADAPTER", "a-1", STARTED_AT, "500"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-2", STARTED_AT.plusSeconds(16), "502"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-3", STARTED_AT.plusSeconds(31), "504")
        );

        assertTrue(rule.evaluate(twoFailures).isEmpty());
        assertTrue(rule.evaluate(outsideWindow).isEmpty());
    }

    @Test
    void failuresAtTheThirtySecondBoundaryRaiseAnAlarm() {
        List<LogEvidence> logs = List.of(
                log("scenario-a", "PAYMENT_ADAPTER", "a-1", STARTED_AT, "500"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-2", STARTED_AT.plusSeconds(15), "502"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-3", STARTED_AT.plusSeconds(30), "504")
        );

        assertTrue(rule.evaluate(logs).isPresent());
    }

    @Test
    void failuresFromDifferentServicesAreNotCombined() {
        List<LogEvidence> logs = List.of(
                log("scenario-a", "PAYMENT_ADAPTER", "a-1", STARTED_AT, "500"),
                log("scenario-a", "CHECKOUT_API", "a-2", STARTED_AT.plusSeconds(5), "500"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-3", STARTED_AT.plusSeconds(10), "500")
        );

        assertTrue(rule.evaluate(logs).isEmpty());
    }

    @Test
    void healthyMissingAndMalformedStatusesAreIgnored() {
        List<LogEvidence> logs = List.of(
                log("scenario-a", "PAYMENT_ADAPTER", "a-1", STARTED_AT, "200"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-2", STARTED_AT.plusSeconds(1), null),
                log("scenario-a", "PAYMENT_ADAPTER", "a-3", STARTED_AT.plusSeconds(2), "timeout"),
                log("scenario-a", "PAYMENT_ADAPTER", "a-4", STARTED_AT.plusSeconds(3), "599")
        );

        assertTrue(rule.evaluate(logs).isEmpty());
    }

    @Test
    void receiptDefensivelyCopiesItsEvidenceIds() {
        List<String> evidenceIds = new ArrayList<>(List.of("a-1", "a-2", "a-3"));
        AlarmReceipt receipt = new AlarmReceipt(
                "alarm-1",
                Http5xxBurstRule.RULE_ID,
                "scenario-a",
                "PAYMENT_ADAPTER",
                STARTED_AT.plusSeconds(20),
                STARTED_AT,
                3,
                3,
                30,
                evidenceIds
        );

        evidenceIds.clear();
        assertEquals(List.of("a-1", "a-2", "a-3"), receipt.evidenceIds());
        assertThrows(UnsupportedOperationException.class, () ->
                receipt.evidenceIds().clear()
        );
    }

    private LogEvidence log(
            String scenarioId,
            String service,
            String evidenceId,
            Instant observedAt,
            String status
    ) {
        Map<String, String> attributes = status == null
                ? Map.of()
                : Map.of(Http5xxBurstRule.HTTP_STATUS_ATTRIBUTE, status);
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "Synthetic HTTP request.",
                "synthetic/" + evidenceId,
                new LogEvidence.LogContent(
                        service,
                        "INFO",
                        "Synthetic HTTP request completed.",
                        attributes
                )
        );
    }
}
