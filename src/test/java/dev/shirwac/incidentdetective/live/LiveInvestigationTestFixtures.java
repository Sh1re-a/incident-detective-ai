package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

final class LiveInvestigationTestFixtures {

    static final String WITHHELD_DIAGNOSIS_SENTINEL =
            "MODEL_PROSE_MUST_NOT_ESCAPE_FAILED_VERIFICATION_74E2";

    private LiveInvestigationTestFixtures() {
    }

    static void stubCheckoutCollections(InvestigationModelGateway model) {
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        )).thenReturn(
                new CollectionModelResult(
                        List.of(
                                call("call-metrics", ToolName.GET_METRICS, Map.of(
                                        "metric_names", List.of(
                                                "checkout_failure_ratio",
                                                "failed_checkout_attempts",
                                                "payment_authorization_duration_p95"
                                        ),
                                        "start", "2026-08-25T09:55:00Z",
                                        "end", "2026-08-25T10:15:00Z"
                                )),
                                call("call-logs", ToolName.SEARCH_LOGS, Map.of(
                                        "services", List.of("PAYMENT_ADAPTER"),
                                        "levels", List.of(),
                                        "query", "timeout",
                                        "start", "2026-08-25T09:55:00Z",
                                        "end", "2026-08-25T10:15:00Z"
                                )),
                                call(
                                        "call-runbook",
                                        ToolName.RETRIEVE_RUNBOOKS,
                                        Map.of(
                                                "query", "payment timeout trace",
                                                "max_results", 4
                                        )
                                )
                        ),
                        metadata(ModelPhase.COLLECT, 1, 600, 100)
                )
        );
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(2), any()
        )).thenReturn(
                new CollectionModelResult(
                        List.of(call("call-trace", ToolName.GET_TRACE, Map.of(
                                "trace_id", "cpt-trace-4821"
                        ))),
                        metadata(ModelPhase.COLLECT, 2, 500, 100)
                )
        );
    }

    static CollectionToolCall call(
            String callId,
            ToolName toolName,
            Map<String, Object> arguments
    ) {
        return new CollectionToolCall(callId, toolName, arguments);
    }

    static ModelCallMetadata metadata(
            ModelPhase phase,
            int round,
            int input,
            int output
    ) {
        int cachedInput = Math.min(100, input);
        return new ModelCallMetadata(
                phase,
                round,
                "response-" + phase.wireValue() + "-" + round,
                "gemini-test-version",
                new ModelTokenUsage(
                        input,
                        cachedInput,
                        input - cachedInput,
                        output,
                        0,
                        output,
                        0,
                        input + output
                ),
                25
        );
    }

    static Diagnosis correctDiagnosis() {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Synthetic checkout attempts fail during payment authorization.",
                "The configured timeout is below the observed authorization duration.",
                List.of(
                        new Claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "The payment timeout is shorter than observed duration.",
                                List.of(
                                        "cpt-v1-log-timeout-config",
                                        "cpt-v1-trace-failed-checkout"
                                )
                        ),
                        new Claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "The failure occurs inside payment authorization.",
                                List.of(
                                        "cpt-v1-log-timeout-error",
                                        "cpt-v1-trace-failed-checkout"
                                )
                        ),
                        new Claim(
                                ClaimCode.CUSTOMER_IMPACT,
                                "CHECKOUT_PAYMENT_FAILURES",
                                "Synthetic checkout attempts are failing.",
                                List.of(
                                        "cpt-v1-metric-checkout-failure-rate",
                                        "cpt-v1-metric-failed-checkouts"
                                )
                        ),
                        new Claim(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "PAYMENT_LATENCY_SPIKE",
                                "Payment latency reaches the timeout boundary.",
                                List.of(
                                        "cpt-v1-metric-payment-p95",
                                        "cpt-v1-trace-failed-checkout"
                                )
                        )
                ),
                new SafeNextStep(
                        "Review and approve restoring the previous timeout.",
                        true
                )
        );
    }

    static Diagnosis diagnosisWithUnknownCitation() {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Synthetic checkout attempts fail during payment authorization.",
                "The configured timeout is below the observed authorization duration.",
                List.of(
                        new Claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "The payment timeout is shorter than observed duration.",
                                List.of("model-invented-evidence-id")
                        ),
                        new Claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "The failure occurs inside payment authorization.",
                                List.of("cpt-v1-log-timeout-error")
                        )
                ),
                new SafeNextStep(
                        "Review the timeout configuration with a human.",
                        true
                )
        );
    }

    static Diagnosis diagnosisWithUnsupportedCitation() {
        Diagnosis supported = correctDiagnosis();
        Claim unsupportedRootCause = new Claim(
                ClaimCode.ROOT_CAUSE,
                "PAYMENT_TIMEOUT_CONFIG",
                "The payment timeout is shorter than observed duration.",
                List.of("cpt-v1-metric-failed-checkouts")
        );
        return new Diagnosis(
                supported.status(),
                supported.rootCauseCode(),
                supported.affectedService(),
                supported.businessSummary(),
                supported.technicalSummary(),
                List.of(
                        unsupportedRootCause,
                        supported.claims().get(1),
                        supported.claims().get(2),
                        supported.claims().get(3)
                ),
                supported.safeNextStep()
        );
    }

    static Diagnosis incorrectAbstentionWithSentinel() {
        return new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                WITHHELD_DIAGNOSIS_SENTINEL,
                WITHHELD_DIAGNOSIS_SENTINEL,
                List.of(),
                new SafeNextStep(
                        WITHHELD_DIAGNOSIS_SENTINEL,
                        true
                )
        );
    }

    static Diagnosis incorrectAbstentionWithSupportedCitation() {
        return new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "More evidence is required.",
                "Payment latency reached the timeout boundary.",
                List.of(new Claim(
                        ClaimCode.OBSERVED_SYMPTOM,
                        "PAYMENT_LATENCY_SPIKE",
                        "Payment latency reached the timeout boundary.",
                        List.of("cpt-v1-metric-payment-p95")
                )),
                new SafeNextStep(
                        "Collect more evidence after human approval.",
                        true
                )
        );
    }
}
