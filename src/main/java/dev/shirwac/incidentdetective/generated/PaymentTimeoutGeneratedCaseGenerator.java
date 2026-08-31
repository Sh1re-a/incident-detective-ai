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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Generates one bounded Payment Timeout incident family without external I/O. */
public final class PaymentTimeoutGeneratedCaseGenerator {

    private static final Instant GENERATION_ANCHOR = Instant.parse(
            "2026-09-01T08:00:00Z"
    );
    private static final String ROOT_CAUSE = "PAYMENT_TIMEOUT_CONFIG";
    private static final String AFFECTED_SERVICE = "PAYMENT_ADAPTER";

    public GeneratedCase generate(GeneratedCaseRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Random random = new Random(request.seed());
        String namespace = "%016x".formatted(random.nextLong());
        String scenarioId = "generated-payment-timeout-" + namespace;
        Instant windowStart = GENERATION_ANCHOR.plusSeconds(
                random.nextInt(30 * 24 * 60) * 60L
        );
        Instant windowEnd = windowStart.plusSeconds(20 * 60L);
        Instant incidentStartedAt = windowStart.plusSeconds(7 * 60L);

        int previousTimeoutMs = 5_000;
        int configuredTimeoutMs = 1_800 + random.nextInt(5) * 100;
        int elapsedMs = configuredTimeoutMs + 8 + random.nextInt(80);
        int paymentP95Ms = configuredTimeoutMs + 25 + random.nextInt(90);
        int checkoutAttempts = 600 + random.nextInt(401);
        int failedAttempts = Math.max(
                1,
                (int) Math.round(checkoutAttempts * (0.14 + random.nextDouble() * 0.08))
        );
        double failureRatio = roundThreeDecimals(
                (double) failedAttempts / checkoutAttempts
        );
        String release = "payment-adapter-generated-" + namespace.substring(0, 8);
        String traceId = "generated-trace-" + "%016x".formatted(random.nextLong());

        Scenario scenario = scenario(
                scenarioId,
                incidentStartedAt,
                windowStart,
                windowEnd,
                failedAttempts,
                checkoutAttempts
        );
        EvidenceIds ids = new EvidenceIds(scenarioId);
        List<Evidence> evidence = evidence(
                request,
                scenarioId,
                windowStart,
                ids,
                release,
                traceId,
                previousTimeoutMs,
                configuredTimeoutMs,
                elapsedMs,
                paymentP95Ms,
                checkoutAttempts,
                failedAttempts,
                failureRatio
        );
        InvestigationData investigationData = new InvestigationData(
                scenario,
                evidence
        );
        GroundTruth hiddenGroundTruth = groundTruth(
                request.evidenceMode(),
                scenarioId,
                ids
        );
        return new GeneratedCase(
                scenario,
                investigationData,
                hiddenGroundTruth
        );
    }

    private Scenario scenario(
            String scenarioId,
            Instant incidentStartedAt,
            Instant windowStart,
            Instant windowEnd,
            int failedAttempts,
            int checkoutAttempts
    ) {
        return new Scenario(
                scenarioId,
                "Generated checkout payment timeout",
                "A request-local synthetic checkout incident generated from one bounded template.",
                incidentStartedAt,
                new TimeWindow(windowStart, windowEnd),
                List.of("STOREFRONT", "CHECKOUT_API", AFFECTED_SERVICE),
                "Generated estimate: " + failedAttempts + " of " + checkoutAttempts
                        + " synthetic checkout attempts failed.",
                List.of(
                        new InitialSymptom(
                                "CHECKOUT_ERROR_RATE_HIGH",
                                "Checkout failures are above the generated baseline.",
                                incidentStartedAt.plusSeconds(2 * 60L)
                        ),
                        new InitialSymptom(
                                "PAYMENT_STEP_SLOW",
                                "The payment step is slower than the generated baseline.",
                                incidentStartedAt.plusSeconds(3 * 60L)
                        )
                ),
                1
        );
    }

    private List<Evidence> evidence(
            GeneratedCaseRequest request,
            String scenarioId,
            Instant windowStart,
            EvidenceIds ids,
            String release,
            String traceId,
            int previousTimeoutMs,
            int configuredTimeoutMs,
            int elapsedMs,
            int paymentP95Ms,
            int checkoutAttempts,
            int failedAttempts,
            double failureRatio
    ) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(failureRatioMetric(
                scenarioId,
                ids.failureRatio(),
                windowStart.plusSeconds(17 * 60L),
                failureRatio,
                checkoutAttempts
        ));
        evidence.add(failedAttemptsMetric(
                scenarioId,
                ids.failedAttempts(),
                windowStart.plusSeconds(17 * 60L),
                failedAttempts
        ));
        evidence.add(paymentP95Metric(
                scenarioId,
                ids.paymentP95(),
                windowStart.plusSeconds(10 * 60L),
                paymentP95Ms,
                release
        ));
        evidence.add(releaseLog(
                scenarioId,
                ids.release(),
                windowStart.plusSeconds(5 * 60L + 12),
                release
        ));

        if (request.evidenceMode() == GeneratedEvidenceMode.DIAGNOSTIC) {
            evidence.add(timeoutConfigLog(
                    scenarioId,
                    ids.timeoutConfig(),
                    windowStart.plusSeconds(5 * 60L + 14),
                    release,
                    previousTimeoutMs,
                    configuredTimeoutMs
            ));
            evidence.add(timeoutErrorLog(
                    scenarioId,
                    ids.timeoutError(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    traceId,
                    configuredTimeoutMs,
                    elapsedMs,
                    true
            ));
            evidence.add(failedTrace(
                    scenarioId,
                    ids.failedTrace(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    traceId,
                    configuredTimeoutMs,
                    elapsedMs
            ));
        } else {
            evidence.add(timeoutErrorLog(
                    scenarioId,
                    ids.timeoutError(),
                    windowStart.plusSeconds(9 * 60L + 8),
                    traceId,
                    configuredTimeoutMs,
                    elapsedMs,
                    false
            ));
            evidence.add(missingAuditLog(
                    scenarioId,
                    ids.missingConfigAudit(),
                    windowStart.plusSeconds(9 * 60L + 20)
            ));
        }

        if (request.noiseLevel() == GeneratedNoiseLevel.LOW) {
            evidence.add(noiseLog(
                    scenarioId,
                    ids.inventoryNoise(),
                    windowStart.plusSeconds(8 * 60L + 20)
            ));
        }
        return List.copyOf(evidence);
    }

    private MetricEvidence failureRatioMetric(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            double failureRatio,
            int checkoutAttempts
    ) {
        return new MetricEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                String.format(
                        Locale.ROOT,
                        "Generated checkout failure ratio reached %.1f percent.",
                        failureRatio * 100
                ),
                source(scenarioId, "metrics/checkout/failure-ratio"),
                new MetricEvidence.MetricContent(
                        "checkout_failure_ratio",
                        failureRatio,
                        "ratio",
                        Map.of(
                                "service", "CHECKOUT_API",
                                "attempts", Integer.toString(checkoutAttempts)
                        )
                )
        );
    }

    private MetricEvidence failedAttemptsMetric(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            int failedAttempts
    ) {
        return new MetricEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                failedAttempts + " generated checkout attempts failed.",
                source(scenarioId, "metrics/checkout/failed-attempts"),
                new MetricEvidence.MetricContent(
                        "failed_checkout_attempts",
                        failedAttempts,
                        "count",
                        Map.of("service", "CHECKOUT_API")
                )
        );
    }

    private MetricEvidence paymentP95Metric(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            int paymentP95Ms,
            String release
    ) {
        return new MetricEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                String.format(
                        Locale.ROOT,
                        "Generated payment authorization p95 reached %.2f seconds.",
                        paymentP95Ms / 1_000.0
                ),
                source(scenarioId, "metrics/payment-adapter/authorization-p95"),
                new MetricEvidence.MetricContent(
                        "payment_authorization_duration_p95",
                        paymentP95Ms / 1_000.0,
                        "seconds",
                        Map.of(
                                "service", AFFECTED_SERVICE,
                                "release", release
                        )
                )
        );
    }

    private LogEvidence releaseLog(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String release
    ) {
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "The generated payment adapter release completed before failures began.",
                source(scenarioId, "logs/payment-adapter/release"),
                new LogEvidence.LogContent(
                        AFFECTED_SERVICE,
                        "INFO",
                        "Generated release " + release + " completed.",
                        Map.of("release", release, "result", "success")
                )
        );
    }

    private LogEvidence timeoutConfigLog(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String release,
            int previousTimeoutMs,
            int configuredTimeoutMs
    ) {
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "The generated release reduced the payment provider read timeout.",
                source(scenarioId, "logs/payment-adapter/config-audit"),
                new LogEvidence.LogContent(
                        AFFECTED_SERVICE,
                        "INFO",
                        "Configuration changed for PAYMENT_PROVIDER_READ_TIMEOUT_MS.",
                        Map.of(
                                "previous_value", Integer.toString(previousTimeoutMs),
                                "new_value", Integer.toString(configuredTimeoutMs),
                                "release", release
                        )
                )
        );
    }

    private LogEvidence timeoutErrorLog(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String traceId,
            int configuredTimeoutMs,
            int elapsedMs,
            boolean exposeCausalConfiguration
    ) {
        Map<String, String> attributes = exposeCausalConfiguration
                ? Map.of(
                        "trace_id", traceId,
                        "configured_timeout_ms", Integer.toString(configuredTimeoutMs),
                        "elapsed_ms", Integer.toString(elapsedMs),
                        "provider", "SYNTHETIC_PAY"
                )
                : Map.of(
                        "elapsed_ms", Integer.toString(elapsedMs),
                        "provider", "SYNTHETIC_PAY"
                );
        String summary = exposeCausalConfiguration
                ? "Generated payment authorization stopped at the configured timeout."
                : "Generated payment authorization ended after a local deadline.";
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                summary,
                source(scenarioId, "logs/payment-adapter/request-timeout"),
                new LogEvidence.LogContent(
                        AFFECTED_SERVICE,
                        "ERROR",
                        "SocketTimeoutException while awaiting synthetic payment provider.",
                        attributes
                )
        );
    }

    private TraceEvidence failedTrace(
            String scenarioId,
            String evidenceId,
            Instant observedAt,
            String traceId,
            int configuredTimeoutMs,
            int elapsedMs
    ) {
        return new TraceEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "A generated checkout trace spent almost all its time in payment authorization.",
                source(scenarioId, "traces/payment-timeout"),
                new TraceEvidence.TraceContent(
                        traceId,
                        List.of(
                                new TraceEvidence.TraceSpan(
                                        traceId + "-span-storefront",
                                        "STOREFRONT",
                                        "submit-checkout",
                                        elapsedMs + 45L,
                                        "ERROR"
                                ),
                                new TraceEvidence.TraceSpan(
                                        traceId + "-span-checkout",
                                        "CHECKOUT_API",
                                        "create-order",
                                        elapsedMs + 36L,
                                        "ERROR"
                                ),
                                new TraceEvidence.TraceSpan(
                                        traceId + "-span-payment",
                                        AFFECTED_SERVICE,
                                        "authorize-payment",
                                        Math.max(configuredTimeoutMs, elapsedMs - 1L),
                                        "DEADLINE_EXCEEDED"
                                )
                        )
                )
        );
    }

    private LogEvidence missingAuditLog(
            String scenarioId,
            String evidenceId,
            Instant observedAt
    ) {
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "The generated case intentionally omits the relevant timeout configuration audit.",
                source(scenarioId, "logs/payment-adapter/missing-config-audit"),
                new LogEvidence.LogContent(
                        AFFECTED_SERVICE,
                        "WARN",
                        "No timeout configuration audit record is available in this generated window.",
                        Map.of("missing_evidence", "PAYMENT_TIMEOUT_CONFIG_AUDIT")
                )
        );
    }

    private LogEvidence noiseLog(
            String scenarioId,
            String evidenceId,
            Instant observedAt
    ) {
        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "A generated inventory cache warning was brief and unrelated.",
                source(scenarioId, "logs/inventory-service/cache-refresh"),
                new LogEvidence.LogContent(
                        "INVENTORY_SERVICE",
                        "WARN",
                        "Generated inventory cache refresh exceeded its warning threshold.",
                        Map.of("duration_ms", "120", "result", "completed")
                )
        );
    }

    private GroundTruth groundTruth(
            GeneratedEvidenceMode evidenceMode,
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
                            expected(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE"),
                            expected(ClaimCode.MISSING_EVIDENCE, "PAYMENT_TIMEOUT_CONFIG_AUDIT")
                    ),
                    List.of(
                            support(
                                    ClaimCode.OBSERVED_SYMPTOM,
                                    "PAYMENT_LATENCY_SPIKE",
                                    ids.paymentP95(),
                                    ids.timeoutError()
                            ),
                            support(
                                    ClaimCode.MISSING_EVIDENCE,
                                    "PAYMENT_TIMEOUT_CONFIG_AUDIT",
                                    ids.missingConfigAudit()
                            )
                    ),
                    List.of()
            );
        }

        return new GroundTruth(
                scenarioId,
                DiagnosisStatus.DIAGNOSED,
                ROOT_CAUSE,
                AFFECTED_SERVICE,
                List.of(
                        expected(ClaimCode.ROOT_CAUSE, ROOT_CAUSE),
                        expected(ClaimCode.AFFECTED_SERVICE, AFFECTED_SERVICE),
                        expected(ClaimCode.TRIGGER, "PAYMENT_ADAPTER_RELEASE"),
                        expected(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES"),
                        expected(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE")
                ),
                List.of(
                        support(
                                ClaimCode.ROOT_CAUSE,
                                ROOT_CAUSE,
                                ids.timeoutConfig(),
                                ids.timeoutError(),
                                ids.failedTrace()
                        ),
                        support(
                                ClaimCode.AFFECTED_SERVICE,
                                AFFECTED_SERVICE,
                                ids.timeoutError(),
                                ids.failedTrace()
                        ),
                        support(
                                ClaimCode.TRIGGER,
                                "PAYMENT_ADAPTER_RELEASE",
                                ids.release(),
                                ids.timeoutConfig()
                        ),
                        support(
                                ClaimCode.CUSTOMER_IMPACT,
                                "CHECKOUT_PAYMENT_FAILURES",
                                ids.failureRatio(),
                                ids.failedAttempts()
                        ),
                        support(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "PAYMENT_LATENCY_SPIKE",
                                ids.paymentP95(),
                                ids.timeoutError(),
                                ids.failedTrace()
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

    private record EvidenceIds(String scenarioId) {
        private String id(String suffix) {
            return scenarioId + "-" + suffix;
        }

        private String failureRatio() {
            return id("metric-checkout-failure-ratio");
        }

        private String failedAttempts() {
            return id("metric-failed-checkouts");
        }

        private String paymentP95() {
            return id("metric-payment-p95");
        }

        private String release() {
            return id("log-release");
        }

        private String timeoutConfig() {
            return id("log-timeout-config");
        }

        private String timeoutError() {
            return id("log-timeout-error");
        }

        private String failedTrace() {
            return id("trace-failed-checkout");
        }

        private String missingConfigAudit() {
            return id("log-missing-config-audit");
        }

        private String inventoryNoise() {
            return id("log-inventory-noise");
        }
    }
}
