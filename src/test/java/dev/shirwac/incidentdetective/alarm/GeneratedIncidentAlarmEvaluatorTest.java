package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.NordlyIncidentGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedIncidentAlarmEvaluatorTest {

    private static final Map<GeneratedIncidentFamily, ExpectedAlarm> EXPECTED = Map.of(
            GeneratedIncidentFamily.PAYMENT_TIMEOUT,
            new ExpectedAlarm(
                    Http5xxBurstRule.RULE_ID,
                    GeneratedIncidentAlarmEvaluator.PAYMENT_SIGNAL,
                    PaymentTimeoutTelemetry.SERVICE,
                    3,
                    "count",
                    30L
            ),
            GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
            new ExpectedAlarm(
                    GeneratedIncidentAlarmEvaluator.CATALOG_RULE_ID,
                    GeneratedIncidentAlarmEvaluator.CATALOG_SIGNAL,
                    "CATALOG_SERVICE",
                    1,
                    "versions",
                    null
            ),
            GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
            new ExpectedAlarm(
                    GeneratedIncidentAlarmEvaluator.BACKLOG_RULE_ID,
                    GeneratedIncidentAlarmEvaluator.BACKLOG_SIGNAL,
                    "ORDER_EVENT_CONSUMER",
                    600,
                    "seconds",
                    null
            ),
            GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
            new ExpectedAlarm(
                    GeneratedIncidentAlarmEvaluator.IDEMPOTENCY_RULE_ID,
                    GeneratedIncidentAlarmEvaluator.IDEMPOTENCY_SIGNAL,
                    "ORDER_SERVICE",
                    1,
                    "count",
                    null
            )
    );

    private final GeneratedIncidentAlarmEvaluator evaluator =
            new GeneratedIncidentAlarmEvaluator();
    private final PaymentTimeoutGeneratedCaseGenerator paymentGenerator =
            new PaymentTimeoutGeneratedCaseGenerator();
    private final NordlyIncidentGeneratedCaseGenerator nordlyGenerator =
            new NordlyIncidentGeneratedCaseGenerator();

    @ParameterizedTest
    @EnumSource(GeneratedIncidentFamily.class)
    void everyFamilyRaisesAnEvidenceLinkedAlarm(
            GeneratedIncidentFamily family
    ) {
        for (GeneratedEvidenceMode evidenceMode : GeneratedEvidenceMode.values()) {
            GeneratedCase generated = generate(family, evidenceMode);
            ExpectedAlarm expected = EXPECTED.get(family);

            SignalAlarmReceipt receipt = evaluator.evaluate(
                    family,
                    generated.investigationData()
            ).orElseThrow();

            assertEquals(family, receipt.incidentFamily());
            assertEquals(generated.scenario().scenarioId(), receipt.scenarioId());
            assertEquals(expected.ruleId(), receipt.ruleId());
            assertEquals(expected.service(), receipt.service());
            assertEquals(expected.signalName(), receipt.signal().name());
            assertEquals(
                    SignalAlarmReceipt.Comparison.AT_LEAST,
                    receipt.signal().comparison()
            );
            assertEquals(expected.threshold(), receipt.signal().thresholdValue());
            assertTrue(
                    receipt.signal().observedValue()
                            >= receipt.signal().thresholdValue()
            );
            assertEquals(expected.unit(), receipt.signal().unit());
            assertEquals(expected.lookbackSeconds(), receipt.signal().lookbackSeconds());
            assertTrue(evidenceIds(generated).containsAll(receipt.evidenceIds()));
        }
    }

    @ParameterizedTest
    @EnumSource(GeneratedIncidentFamily.class)
    void evaluationIsIndependentOfEvidenceOrder(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = generate(
                family,
                GeneratedEvidenceMode.DIAGNOSTIC
        );
        List<Evidence> reversed = new ArrayList<>(
                generated.investigationData().evidenceInventory()
        );
        Collections.reverse(reversed);
        InvestigationData reordered = new InvestigationData(
                generated.scenario(),
                reversed
        );

        assertEquals(
                evaluator.evaluate(family, generated.investigationData()),
                evaluator.evaluate(family, reordered)
        );
    }

    @ParameterizedTest
    @EnumSource(GeneratedIncidentFamily.class)
    void missingTriggerSignalDoesNotRaiseAnAlarm(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = generate(
                family,
                GeneratedEvidenceMode.DIAGNOSTIC
        );
        String signalName = EXPECTED.get(family).signalName();
        List<Evidence> withoutTrigger = generated.investigationData()
                .evidenceInventory()
                .stream()
                .filter(evidence -> !isTriggerEvidence(evidence, signalName))
                .toList();

        assertTrue(evaluator.evaluate(
                family,
                new InvestigationData(generated.scenario(), withoutTrigger)
        ).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = GeneratedIncidentFamily.class, names = "PAYMENT_TIMEOUT")
    void paymentFamilyKeepsTheLegacyHttpBurstReceiptValues(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = generate(
                family,
                GeneratedEvidenceMode.DIAGNOSTIC
        );
        List<LogEvidence> logs = generated.investigationData()
                .evidenceInventory()
                .stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .toList();
        AlarmReceipt legacy = new Http5xxBurstRule().evaluate(logs).orElseThrow();
        SignalAlarmReceipt generalized = evaluator.evaluate(
                family,
                generated.investigationData()
        ).orElseThrow();

        assertEquals(legacy.alarmId(), generalized.alarmId());
        assertEquals(legacy.triggeredAt(), generalized.triggeredAt());
        assertEquals(legacy.evidenceIds(), generalized.evidenceIds());
        assertEquals(legacy.threshold(), generalized.signal().thresholdValue());
        assertEquals(
                legacy.observedFailures(),
                generalized.signal().observedValue()
        );
        assertEquals(legacy.windowSeconds(), generalized.signal().lookbackSeconds());
    }

    @ParameterizedTest
    @EnumSource(
            value = GeneratedIncidentFamily.class,
            names = "PAYMENT_TIMEOUT",
            mode = EnumSource.Mode.EXCLUDE
    )
    void metricAlarmsUseOneExactTriggeringObservation(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = generate(
                family,
                GeneratedEvidenceMode.DIAGNOSTIC
        );
        SignalAlarmReceipt receipt = evaluator.evaluate(
                family,
                generated.investigationData()
        ).orElseThrow();

        assertEquals(1, receipt.evidenceIds().size());
        MetricEvidence metric = generated.investigationData()
                .evidenceInventory()
                .stream()
                .filter(MetricEvidence.class::isInstance)
                .map(MetricEvidence.class::cast)
                .filter(candidate -> receipt.evidenceIds().contains(
                        candidate.evidenceId()
                ))
                .findFirst()
                .orElseThrow();
        assertEquals(metric.observedAt(), receipt.triggeredAt());
        assertEquals(metric.content().value(), receipt.signal().observedValue());
        assertNull(receipt.signal().lookbackSeconds());
    }

    private GeneratedCase generate(
            GeneratedIncidentFamily family,
            GeneratedEvidenceMode evidenceMode
    ) {
        GeneratedCaseRequest request = new GeneratedCaseRequest(
                42L,
                family,
                evidenceMode,
                GeneratedNoiseLevel.LOW
        );
        return family == GeneratedIncidentFamily.PAYMENT_TIMEOUT
                ? paymentGenerator.generate(request)
                : nordlyGenerator.generate(request);
    }

    private List<String> evidenceIds(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .toList();
    }

    private boolean isTriggerEvidence(Evidence evidence, String signalName) {
        if (evidence instanceof MetricEvidence metric) {
            return signalName.equals(metric.content().metricName());
        }
        if (evidence instanceof LogEvidence log) {
            String rawStatus = log.content().attributes().get(
                    Http5xxBurstRule.HTTP_STATUS_ATTRIBUTE
            );
            if (rawStatus == null) {
                return false;
            }
            try {
                int status = Integer.parseInt(rawStatus);
                return status >= 500 && status <= 599;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private record ExpectedAlarm(
            String ruleId,
            String signalName,
            String service,
            double threshold,
            String unit,
            Long lookbackSeconds
    ) {
    }
}
