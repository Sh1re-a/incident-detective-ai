package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.ExpectedClaim;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.InitialSymptom;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.scenario.TimeWindow;
import dev.shirwac.incidentdetective.investigation.InvestigationData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Generates three bounded Nordly incident families without external I/O.
 *
 * <p>The families share the same evidence shape so the investigation loop can
 * remain fixed, while every family supplies its own scenario, metrics, logs,
 * trace, taxonomy values and hidden GroundTruth.</p>
 */
public final class NordlyIncidentGeneratedCaseGenerator {

    private static final Instant GENERATION_ANCHOR = Instant.parse(
            "2026-09-01T08:00:00Z"
    );

    public GeneratedCase generate(GeneratedCaseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Blueprint blueprint = blueprint(request.incidentFamily());
        Random random = new Random(request.seed() ^ blueprint.seedSalt());
        String namespace = "%016x".formatted(random.nextLong());
        String scenarioId = "generated-" + blueprint.slug() + "-" + namespace;
        Instant windowStart = GENERATION_ANCHOR.plusSeconds(
                random.nextInt(30 * 24 * 60) * 60L
        );
        Instant windowEnd = windowStart.plusSeconds(20 * 60L);
        Instant incidentStartedAt = windowStart.plusSeconds(7 * 60L);

        int attempts = blueprint.minimumAttempts()
                + random.nextInt(blueprint.attemptRange());
        int affected = Math.max(
                1,
                (int) Math.round(attempts * (
                        blueprint.minimumImpactRatio()
                                + random.nextDouble() * blueprint.impactRatioRange()
                ))
        );
        double impactRatio = roundThreeDecimals((double) affected / attempts);
        int symptomValue = blueprint.symptomUsesAffectedCount()
                ? affected
                : blueprint.minimumSymptomValue()
                + random.nextInt(blueprint.symptomValueRange());
        String release = blueprint.releasePrefix() + namespace.substring(0, 8);
        String traceId = "generated-trace-" + "%016x".formatted(random.nextLong());

        Scenario scenario = scenario(
                blueprint,
                scenarioId,
                incidentStartedAt,
                windowStart,
                windowEnd,
                affected,
                attempts
        );
        EvidenceIds ids = new EvidenceIds(scenarioId);
        List<Evidence> evidence = evidence(
                request,
                blueprint,
                scenarioId,
                windowStart,
                ids,
                release,
                traceId,
                attempts,
                affected,
                impactRatio,
                symptomValue,
                random
        );
        return new GeneratedCase(
                scenario,
                new InvestigationData(scenario, evidence),
                groundTruth(request.evidenceMode(), blueprint, scenarioId, ids)
        );
    }

    private Scenario scenario(
            Blueprint blueprint,
            String scenarioId,
            Instant incidentStartedAt,
            Instant windowStart,
            Instant windowEnd,
            int affected,
            int attempts
    ) {
        return new Scenario(
                scenarioId,
                blueprint.title(),
                blueprint.description(),
                incidentStartedAt,
                new TimeWindow(windowStart, windowEnd),
                blueprint.affectedServices(),
                "Generated estimate: " + affected + " of " + attempts
                        + " synthetic " + blueprint.businessImpactSentence(),
                List.of(
                        new InitialSymptom(
                                blueprint.impactSymptomCode(),
                                blueprint.impactSymptomSummary(),
                                incidentStartedAt.plusSeconds(2 * 60L)
                        ),
                        new InitialSymptom(
                                blueprint.technicalSymptomCode(),
                                blueprint.technicalSymptomSummary(),
                                incidentStartedAt.plusSeconds(3 * 60L)
                        )
                ),
                1
        );
    }

    private List<Evidence> evidence(
            GeneratedCaseRequest request,
            Blueprint blueprint,
            String scenarioId,
            Instant windowStart,
            EvidenceIds ids,
            String release,
            String traceId,
            int attempts,
            int affected,
            double impactRatio,
            int symptomValue,
            Random random
    ) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(metric(
                scenarioId,
                ids.impactRatio(),
                windowStart.plusSeconds(17 * 60L),
                String.format(
                        Locale.ROOT,
                        "%s reached %.1f percent.",
                        blueprint.impactRatioDisplayName(),
                        impactRatio * 100
                ),
                "metrics/" + blueprint.slug() + "/impact-ratio",
                blueprint.impactRatioMetricName(),
                impactRatio,
                "ratio",
                Map.of(
                        "service", blueprint.affectedService(),
                        "observations", Integer.toString(attempts)
                )
        ));
        evidence.add(metric(
                scenarioId,
                ids.impactCount(),
                windowStart.plusSeconds(17 * 60L),
                affected + " generated " + blueprint.impactCountDisplayName() + ".",
                "metrics/" + blueprint.slug() + "/impact-count",
                blueprint.impactCountMetricName(),
                affected,
                "count",
                Map.of("service", blueprint.affectedService())
        ));
        evidence.add(metric(
                scenarioId,
                ids.symptomMetric(),
                windowStart.plusSeconds(10 * 60L),
                "Generated " + blueprint.symptomDisplayName() + " reached "
                        + symptomValue + " " + blueprint.symptomUnit() + ".",
                "metrics/" + blueprint.slug() + "/technical-symptom",
                blueprint.symptomMetricName(),
                symptomValue,
                blueprint.symptomUnit(),
                Map.of("service", blueprint.affectedService(), "release", release)
        ));
        evidence.add(log(
                scenarioId,
                ids.changeEvent(),
                windowStart.plusSeconds(5 * 60L + 12),
                blueprint.changeDisplaySummary(),
                "logs/" + blueprint.slug() + "/change-event",
                blueprint.affectedService(),
                "INFO",
                blueprint.changeMessage(),
                Map.of(
                        "release", release,
                        "change", blueprint.trigger(),
                        "result", "success"
                )
        ));

        if (request.evidenceMode() == GeneratedEvidenceMode.DIAGNOSTIC) {
            evidence.add(log(
                    scenarioId,
                    ids.causalConfig(),
                    windowStart.plusSeconds(5 * 60L + 14),
                    blueprint.configDisplaySummary(),
                    "logs/" + blueprint.slug() + "/config-audit",
                    blueprint.affectedService(),
                    "INFO",
                    "Configuration changed for " + blueprint.configKey() + ".",
                    Map.of(
                            "previous_value", blueprint.previousConfigValue(),
                            "new_value", blueprint.newConfigValue(),
                            "release", release
                    )
            ));
            evidence.add(log(
                    scenarioId,
                    ids.failureLog(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    blueprint.failureDisplaySummary(),
                    "logs/" + blueprint.slug() + "/failure",
                    blueprint.affectedService(),
                    "ERROR",
                    blueprint.failureMessage(),
                    diagnosticFailureAttributes(
                            blueprint,
                            traceId,
                            symptomValue
                    )
            ));
            evidence.add(trace(
                    scenarioId,
                    ids.failureTrace(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    blueprint.traceDisplaySummary(),
                    "traces/" + blueprint.slug() + "/failure",
                    traceId,
                    blueprint.traceSpans(),
                    random
            ));
        } else {
            evidence.add(log(
                    scenarioId,
                    ids.failureLog(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    blueprint.insufficientFailureDisplaySummary(),
                    "logs/" + blueprint.slug() + "/symptom-only",
                    blueprint.affectedService(),
                    "ERROR",
                    blueprint.insufficientFailureMessage(),
                    Map.of(
                            "measured_value", Integer.toString(symptomValue),
                            "cause", "not_available_in_window"
                    )
            ));
            evidence.add(log(
                    scenarioId,
                    ids.missingAudit(),
                    windowStart.plusSeconds(9 * 60L + 20),
                    blueprint.missingEvidenceDisplaySummary(),
                    "logs/" + blueprint.slug() + "/missing-audit",
                    blueprint.affectedService(),
                    "WARN",
                    blueprint.missingEvidenceMessage(),
                    Map.of("missing_evidence", blueprint.missingEvidence())
            ));
        }

        if (request.noiseLevel() == GeneratedNoiseLevel.LOW) {
            evidence.add(log(
                    scenarioId,
                    ids.noise(),
                    windowStart.plusSeconds(8 * 60L + 20),
                    blueprint.noiseDisplaySummary(),
                    "logs/" + blueprint.slug() + "/unrelated-noise",
                    blueprint.noiseService(),
                    "WARN",
                    blueprint.noiseMessage(),
                    Map.of("duration_ms", "120", "result", "completed")
            ));
        }
        return List.copyOf(evidence);
    }

    private Map<String, String> diagnosticFailureAttributes(
            Blueprint blueprint,
            String traceId,
            int symptomValue
    ) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("trace_id", traceId);
        attributes.put("measured_value", Integer.toString(symptomValue));
        attributes.putAll(blueprint.failureAttributes());
        return Map.copyOf(attributes);
    }

    private MetricEvidence metric(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String displaySummary,
            String path,
            String metricName,
            double value,
            String unit,
            Map<String, String> labels
    ) {
        return new MetricEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                displaySummary,
                source(scenarioId, path),
                new MetricEvidence.MetricContent(metricName, value, unit, labels)
        );
    }

    private LogEvidence log(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String displaySummary,
            String path,
            String service,
            String level,
            String message,
            Map<String, String> attributes
    ) {
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                displaySummary,
                source(scenarioId, path),
                new LogEvidence.LogContent(service, level, message, attributes)
        );
    }

    private TraceEvidence trace(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String displaySummary,
            String path,
            String traceId,
            List<SpanBlueprint> spanBlueprints,
            Random random
    ) {
        List<TraceEvidence.TraceSpan> spans = java.util.stream.IntStream
                .range(0, spanBlueprints.size())
                .mapToObj(index -> {
                    SpanBlueprint span = spanBlueprints.get(index);
                    long duration = span.minimumDurationMs()
                            + random.nextInt(span.durationRangeMs());
                    return new TraceEvidence.TraceSpan(
                            traceId + "-span-" + (index + 1),
                            span.service(),
                            span.operation(),
                            duration,
                            span.status()
                    );
                })
                .toList();
        return new TraceEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                displaySummary,
                source(scenarioId, path),
                new TraceEvidence.TraceContent(traceId, spans)
        );
    }

    private GroundTruth groundTruth(
            GeneratedEvidenceMode evidenceMode,
            Blueprint blueprint,
            String scenarioId,
            EvidenceIds ids
    ) {
        if (evidenceMode == GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE) {
            return new GroundTruth(
                    scenarioId,
                    DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                    null,
                    null,
                    List.of(
                            expected(ClaimCode.OBSERVED_SYMPTOM, blueprint.symptom()),
                            expected(ClaimCode.MISSING_EVIDENCE, blueprint.missingEvidence())
                    ),
                    List.of(
                            support(
                                    ClaimCode.OBSERVED_SYMPTOM,
                                    blueprint.symptom(),
                                    ids.symptomMetric(),
                                    ids.failureLog()
                            ),
                            support(
                                    ClaimCode.MISSING_EVIDENCE,
                                    blueprint.missingEvidence(),
                                    ids.missingAudit()
                            )
                    ),
                    List.of()
            );
        }

        return new GroundTruth(
                scenarioId,
                DiagnosisStatus.DIAGNOSED,
                blueprint.rootCause(),
                blueprint.affectedService(),
                List.of(
                        expected(ClaimCode.ROOT_CAUSE, blueprint.rootCause()),
                        expected(ClaimCode.AFFECTED_SERVICE, blueprint.affectedService()),
                        expected(ClaimCode.TRIGGER, blueprint.trigger()),
                        expected(ClaimCode.CUSTOMER_IMPACT, blueprint.impact()),
                        expected(ClaimCode.OBSERVED_SYMPTOM, blueprint.symptom())
                ),
                List.of(
                        support(
                                ClaimCode.ROOT_CAUSE,
                                blueprint.rootCause(),
                                ids.causalConfig(),
                                ids.failureLog(),
                                ids.failureTrace()
                        ),
                        support(
                                ClaimCode.AFFECTED_SERVICE,
                                blueprint.affectedService(),
                                ids.failureLog(),
                                ids.failureTrace()
                        ),
                        support(
                                ClaimCode.TRIGGER,
                                blueprint.trigger(),
                                ids.changeEvent(),
                                ids.causalConfig()
                        ),
                        support(
                                ClaimCode.CUSTOMER_IMPACT,
                                blueprint.impact(),
                                ids.impactRatio(),
                                ids.impactCount()
                        ),
                        support(
                                ClaimCode.OBSERVED_SYMPTOM,
                                blueprint.symptom(),
                                ids.symptomMetric(),
                                ids.failureLog(),
                                ids.failureTrace()
                        )
                ),
                List.of()
        );
    }

    private ExpectedClaim expected(ClaimCode code, String value) {
        return new ExpectedClaim(code, value);
    }

    private ClaimSupport support(
            ClaimCode code,
            String value,
            String... evidenceIds
    ) {
        return new ClaimSupport(code, value, List.of(evidenceIds));
    }

    private String source(String scenarioId, String path) {
        return "generated/" + scenarioId + "/" + path;
    }

    private double roundThreeDecimals(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }

    private Blueprint blueprint(GeneratedIncidentFamily family) {
        return switch (family) {
            case CATALOG_CACHE_INVALIDATION -> catalogCacheBlueprint();
            case ORDER_EVENT_BACKLOG -> orderEventBacklogBlueprint();
            case ORDER_IDEMPOTENCY_FAILURE -> orderIdempotencyBlueprint();
            case PAYMENT_TIMEOUT -> throw new IllegalArgumentException(
                    "Payment timeout belongs to PaymentTimeoutGeneratedCaseGenerator"
            );
        };
    }

    private Blueprint catalogCacheBlueprint() {
        return new Blueprint(
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                0x4E4F52444C594341L,
                "catalog-cache-invalidation",
                "Generated storefront shows stale product information",
                "A request-local synthetic Nordly incident where product views return old prices and stock after a catalog cache change.",
                List.of("STOREFRONT", "CATALOG_SERVICE"),
                "product views returned stale catalog data after the update.",
                "CATALOG_RESULTS_STALE",
                "Customers see an older price or availability value in the synthetic storefront.",
                "CATALOG_VERSION_DIVERGENCE",
                "The served catalog version differs from the generated source version.",
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "CATALOG_SERVICE",
                "CATALOG_INVALIDATION_CONFIG_CHANGE",
                "STALE_CATALOG_RESULTS",
                "CATALOG_VERSION_DIVERGENCE",
                "CATALOG_SOURCE_OF_TRUTH_VERSION",
                900,
                601,
                0.08,
                0.06,
                "Generated stale catalog response ratio",
                "catalog_stale_response_ratio",
                "stale catalog responses",
                "stale_catalog_responses",
                "catalog version divergence",
                "catalog_version_divergence_count",
                "versions",
                1,
                3,
                false,
                "catalog-service-generated-",
                "The generated catalog invalidation configuration rollout completed before stale results appeared.",
                "Generated catalog invalidation configuration rollout completed.",
                "The rollout disabled invalidation for the product-list cache.",
                "CATALOG_INVALIDATION_ENABLED",
                "true",
                "false",
                "A generated catalog response served an older version after invalidation was skipped.",
                "CatalogCacheVersionMismatch: invalidation skipped and cached product data is stale.",
                Map.of("served_version", "catalog-v41", "source_version", "catalog-v42"),
                "A generated catalog response is stale, but the source version and invalidation audit are absent.",
                "CatalogVersionUnknown: stale product data detected without the causal audit.",
                "The generated case intentionally omits the source-of-truth catalog version.",
                "No source-of-truth catalog version is available in this generated window.",
                "ORDER_SERVICE",
                "A generated order archive warning was brief and unrelated.",
                "Generated order archive check exceeded its warning threshold.",
                List.of(
                        new SpanBlueprint("STOREFRONT", "browse-products", 62, 18, "ERROR"),
                        new SpanBlueprint("CATALOG_SERVICE", "list-products", 48, 14, "ERROR"),
                        new SpanBlueprint("CATALOG_SERVICE", "read-product-cache", 7, 5, "STALE")
                )
        );
    }

    private Blueprint orderEventBacklogBlueprint() {
        return new Blueprint(
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                0x4E4F52444C594F52L,
                "order-event-backlog",
                "Generated orders wait after successful purchase",
                "A request-local synthetic Nordly incident where checkout succeeds but downstream order events and confirmations are delayed.",
                List.of("ORDER_SERVICE", "ORDER_EVENT_CONSUMER", "INVENTORY_SERVICE"),
                "confirmed orders waited more than ten minutes for downstream processing.",
                "ORDER_CONFIRMATIONS_DELAYED",
                "Customers complete checkout but their synthetic order confirmation is delayed.",
                "ORDER_CONSUMER_LAG",
                "The generated order-event consumer lag rises above its baseline.",
                "ORDER_EVENT_CONSUMER_BACKLOG",
                "ORDER_EVENT_CONSUMER",
                "ORDER_CONSUMER_CONFIG_CHANGE",
                "ORDER_PROCESSING_DELAYS",
                "ORDER_CONSUMER_LAG",
                "ORDER_CONSUMER_CONFIG_AUDIT",
                480,
                321,
                0.10,
                0.08,
                "Generated delayed-order ratio",
                "delayed_order_ratio",
                "delayed orders",
                "delayed_orders",
                "order consumer lag",
                "order_consumer_lag_seconds",
                "seconds",
                620,
                281,
                false,
                "order-consumer-generated-",
                "The generated order consumer configuration rollout completed before lag increased.",
                "Generated order-event consumer configuration rollout completed.",
                "The rollout reduced the order consumer worker count from eight to one.",
                "ORDER_CONSUMER_WORKERS",
                "8",
                "1",
                "The generated consumer processes order events slower than they arrive with only one worker active.",
                "OrderConsumerBacklog: one active worker cannot keep up with generated order events.",
                Map.of("active_workers", "1", "consumer_group", "nordly-orders"),
                "Generated order-event lag is visible, but the consumer configuration audit is absent.",
                "OrderConsumerLag: downstream processing is delayed without a causal audit.",
                "The generated case intentionally omits the order consumer configuration audit.",
                "No order consumer configuration audit is available in this generated window.",
                "CATALOG_SERVICE",
                "A generated catalog reindex warning was brief and unrelated.",
                "Generated catalog reindex exceeded its warning threshold and then completed.",
                List.of(
                        new SpanBlueprint("ORDER_SERVICE", "publish-order-created", 22, 10, "OK"),
                        new SpanBlueprint("ORDER_EVENT_CONSUMER", "consume-order-created", 620_000, 45_000, "DEADLINE_EXCEEDED"),
                        new SpanBlueprint("INVENTORY_SERVICE", "confirm-reservation", 8, 5, "PENDING")
                )
        );
    }

    private Blueprint orderIdempotencyBlueprint() {
        return new Blueprint(
                GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
                0x4E4F52444C594944L,
                "order-idempotency-failure",
                "Generated payment retries create duplicate orders",
                "A request-local synthetic Nordly incident where retrying the same completed checkout creates more than one order.",
                List.of("STOREFRONT", "CHECKOUT_API", "ORDER_SERVICE"),
                "retried order submissions created duplicate orders.",
                "DUPLICATE_ORDERS_REPORTED",
                "Customers see two synthetic order numbers after retrying one purchase.",
                "DUPLICATE_ORDER_CREATION",
                "The same generated idempotency key is accepted more than once.",
                "ORDER_IDEMPOTENCY_FAILURE",
                "ORDER_SERVICE",
                "ORDER_IDEMPOTENCY_STORAGE_CHANGE",
                "DUPLICATE_ORDERS",
                "DUPLICATE_ORDER_CREATION",
                "ORDER_IDEMPOTENCY_STORAGE_AUDIT",
                160,
                181,
                0.04,
                0.05,
                "Generated duplicate-order ratio",
                "duplicate_order_ratio",
                "duplicate orders",
                "duplicate_orders",
                "duplicate order creation count",
                "duplicate_order_creation_count",
                "count",
                1,
                2,
                true,
                "order-service-generated-",
                "The generated idempotency storage rollout completed before duplicate orders appeared.",
                "Generated order-service idempotency storage rollout completed.",
                "The rollout moved idempotency records from shared storage to local memory.",
                "ORDER_IDEMPOTENCY_STORE",
                "POSTGRES",
                "LOCAL_MEMORY",
                "A generated retry reused one idempotency key but created two order IDs after a local-memory miss.",
                "DuplicateOrderDetected: repeated idempotency key created two generated orders.",
                Map.of("idempotency_key", "idem-generated-42", "orders_created", "2"),
                "Generated duplicate orders are visible, but the idempotency storage audit is absent.",
                "DuplicateOrderDetected: repeated submission created two orders without a causal audit.",
                "The generated case intentionally omits the idempotency storage audit.",
                "No idempotency storage audit is available in this generated window.",
                "PAYMENT_ADAPTER",
                "A generated payment health warning was brief and unrelated.",
                "Generated payment health check exceeded its warning threshold and recovered.",
                List.of(
                        new SpanBlueprint("STOREFRONT", "retry-order", 132, 24, "ERROR"),
                        new SpanBlueprint("CHECKOUT_API", "confirm-checkout", 105, 20, "ERROR"),
                        new SpanBlueprint("ORDER_SERVICE", "lookup-idempotency-key", 4, 4, "MISS"),
                        new SpanBlueprint("ORDER_SERVICE", "create-order", 61, 15, "DUPLICATE")
                )
        );
    }

    private record EvidenceIds(String scenarioId) {
        private String id(String suffix) {
            return scenarioId + "-" + suffix;
        }

        private String impactRatio() {
            return id("metric-impact-ratio");
        }

        private String impactCount() {
            return id("metric-impact-count");
        }

        private String symptomMetric() {
            return id("metric-symptom");
        }

        private String changeEvent() {
            return id("log-change-event");
        }

        private String causalConfig() {
            return id("log-causal-config");
        }

        private String failureLog() {
            return id("log-failure");
        }

        private String failureTrace() {
            return id("trace-failure");
        }

        private String missingAudit() {
            return id("log-missing-audit");
        }

        private String noise() {
            return id("log-unrelated-noise");
        }
    }

    private record SpanBlueprint(
            String service,
            String operation,
            int minimumDurationMs,
            int durationRangeMs,
            String status
    ) {
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private record Blueprint(
            GeneratedIncidentFamily family,
            long seedSalt,
            String slug,
            String title,
            String description,
            List<String> affectedServices,
            String businessImpactSentence,
            String impactSymptomCode,
            String impactSymptomSummary,
            String technicalSymptomCode,
            String technicalSymptomSummary,
            String rootCause,
            String affectedService,
            String trigger,
            String impact,
            String symptom,
            String missingEvidence,
            int minimumAttempts,
            int attemptRange,
            double minimumImpactRatio,
            double impactRatioRange,
            String impactRatioDisplayName,
            String impactRatioMetricName,
            String impactCountDisplayName,
            String impactCountMetricName,
            String symptomDisplayName,
            String symptomMetricName,
            String symptomUnit,
            int minimumSymptomValue,
            int symptomValueRange,
            boolean symptomUsesAffectedCount,
            String releasePrefix,
            String changeDisplaySummary,
            String changeMessage,
            String configDisplaySummary,
            String configKey,
            String previousConfigValue,
            String newConfigValue,
            String failureDisplaySummary,
            String failureMessage,
            Map<String, String> failureAttributes,
            String insufficientFailureDisplaySummary,
            String insufficientFailureMessage,
            String missingEvidenceDisplaySummary,
            String missingEvidenceMessage,
            String noiseService,
            String noiseDisplaySummary,
            String noiseMessage,
            List<SpanBlueprint> traceSpans
    ) {
        private Blueprint {
            affectedServices = List.copyOf(affectedServices);
            failureAttributes = Map.copyOf(failureAttributes);
            traceSpans = List.copyOf(traceSpans);
        }

        private String traceDisplaySummary() {
            return switch (family) {
                case CATALOG_CACHE_INVALIDATION ->
                        "A generated storefront trace returns stale catalog data from the cache path.";
                case ORDER_EVENT_BACKLOG ->
                        "A generated order trace publishes successfully but waits in the consumer path.";
                case ORDER_IDEMPOTENCY_FAILURE ->
                        "A generated retry trace misses the idempotency record and creates a duplicate order.";
                case PAYMENT_TIMEOUT -> throw new IllegalStateException(
                        "Payment timeout does not use this blueprint"
                );
            };
        }
    }
}
