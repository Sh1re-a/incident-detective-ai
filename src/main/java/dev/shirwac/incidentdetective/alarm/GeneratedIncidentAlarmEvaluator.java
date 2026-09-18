package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.investigation.InvestigationData;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Evaluates the observable alarm signal for each bounded generated family. */
public final class GeneratedIncidentAlarmEvaluator {

    static final String CATALOG_RULE_ID = "CATALOG_VERSION_DIVERGENCE_V1";
    static final String BACKLOG_RULE_ID = "ORDER_CONSUMER_LAG_V1";
    static final String IDEMPOTENCY_RULE_ID = "DUPLICATE_ORDER_CREATION_V1";

    static final String PAYMENT_SIGNAL = "http_5xx_response_count";
    static final String CATALOG_SIGNAL = "catalog_version_divergence_count";
    static final String BACKLOG_SIGNAL = "order_consumer_lag_seconds";
    static final String IDEMPOTENCY_SIGNAL = "duplicate_order_creation_count";

    private static final MetricRule CATALOG_RULE = new MetricRule(
            CATALOG_RULE_ID,
            CATALOG_SIGNAL,
            "CATALOG_SERVICE",
            1,
            "versions"
    );
    private static final MetricRule BACKLOG_RULE = new MetricRule(
            BACKLOG_RULE_ID,
            BACKLOG_SIGNAL,
            "ORDER_EVENT_CONSUMER",
            600,
            "seconds"
    );
    private static final MetricRule IDEMPOTENCY_RULE = new MetricRule(
            IDEMPOTENCY_RULE_ID,
            IDEMPOTENCY_SIGNAL,
            "ORDER_SERVICE",
            1,
            "count"
    );
    private static final Comparator<MetricEvidence> METRIC_ORDER = Comparator
            .comparing(MetricEvidence::observedAt)
            .thenComparing(MetricEvidence::evidenceId);

    private final Http5xxBurstRule paymentRule;

    public GeneratedIncidentAlarmEvaluator() {
        paymentRule = new Http5xxBurstRule();
    }

    public Optional<SignalAlarmReceipt> evaluate(
            GeneratedIncidentFamily family,
            InvestigationData data
    ) {
        Objects.requireNonNull(family, "family must not be null");
        Objects.requireNonNull(data, "data must not be null");

        return switch (family) {
            case PAYMENT_TIMEOUT -> evaluatePayment(data);
            case CATALOG_CACHE_INVALIDATION -> evaluateMetric(
                    family,
                    data,
                    CATALOG_RULE
            );
            case ORDER_EVENT_BACKLOG -> evaluateMetric(
                    family,
                    data,
                    BACKLOG_RULE
            );
            case ORDER_IDEMPOTENCY_FAILURE -> evaluateMetric(
                    family,
                    data,
                    IDEMPOTENCY_RULE
            );
        };
    }

    private Optional<SignalAlarmReceipt> evaluatePayment(
            InvestigationData data
    ) {
        String scenarioId = data.scenario().scenarioId();
        List<LogEvidence> logs = data.evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .filter(log -> scenarioId.equals(log.scenarioId()))
                .toList();

        return paymentRule.evaluate(logs).map(receipt ->
                new SignalAlarmReceipt(
                        receipt.alarmId(),
                        receipt.ruleId(),
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        receipt.scenarioId(),
                        receipt.service(),
                        receipt.triggeredAt(),
                        new SignalAlarmReceipt.SignalObservation(
                                PAYMENT_SIGNAL,
                                SignalAlarmReceipt.Comparison.AT_LEAST,
                                receipt.threshold(),
                                receipt.observedFailures(),
                                "count",
                                receipt.windowSeconds()
                        ),
                        receipt.evidenceIds()
                )
        );
    }

    private Optional<SignalAlarmReceipt> evaluateMetric(
            GeneratedIncidentFamily family,
            InvestigationData data,
            MetricRule rule
    ) {
        String scenarioId = data.scenario().scenarioId();
        return data.evidenceInventory().stream()
                .filter(MetricEvidence.class::isInstance)
                .map(MetricEvidence.class::cast)
                .filter(metric -> scenarioId.equals(metric.scenarioId()))
                .filter(metric -> rule.metricName().equals(
                        metric.content().metricName()
                ))
                .filter(metric -> rule.unit().equals(metric.content().unit()))
                .filter(metric -> rule.service().equals(
                        metric.content().labels().get("service")
                ))
                .filter(metric -> metric.content().value() >= rule.threshold())
                .min(METRIC_ORDER)
                .map(metric -> metricReceipt(family, metric, rule));
    }

    private SignalAlarmReceipt metricReceipt(
            GeneratedIncidentFamily family,
            MetricEvidence metric,
            MetricRule rule
    ) {
        return new SignalAlarmReceipt(
                alarmId(metric.scenarioId(), rule.ruleId(), metric),
                rule.ruleId(),
                family,
                metric.scenarioId(),
                rule.service(),
                metric.observedAt(),
                new SignalAlarmReceipt.SignalObservation(
                        rule.metricName(),
                        SignalAlarmReceipt.Comparison.AT_LEAST,
                        rule.threshold(),
                        metric.content().value(),
                        rule.unit(),
                        null
                ),
                List.of(metric.evidenceId())
        );
    }

    private String alarmId(
            String scenarioId,
            String ruleId,
            MetricEvidence metric
    ) {
        return "%s-%s-%d".formatted(
                scenarioId,
                ruleId.toLowerCase(Locale.ROOT).replace('_', '-'),
                metric.observedAt().toEpochMilli()
        );
    }

    private record MetricRule(
            String ruleId,
            String metricName,
            String service,
            double threshold,
            String unit
    ) {
    }
}
