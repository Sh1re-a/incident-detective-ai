package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.verification.VerificationErrorCode;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key",
        "incident-detective.ai.model-id=gemini-test",
        "incident-detective.ai.prompt-version=test-prompt-v1"
})
class LiveInvestigationServiceTest {

    private static final String SCENARIO_ID = "checkout-orders-at-risk-v1";

    @Autowired
    private LiveInvestigationService service;

    @MockitoBean
    private InvestigationModelGateway model;

    @BeforeEach
    void resetModel() {
        reset(model);
    }

    @Test
    void completesABoundedRunUsingOnlyToolReturnedEvidence() {
        stubCollections();
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(correctDiagnosis(), metadata(
                        ModelPhase.SYNTHESIZE,
                        1,
                        700,
                        300
                ))
        );

        LiveInvestigationResult result = service.investigate(
                SCENARIO_ID,
                new LiveInvestigationRequest(true)
        );

        assertEquals(LiveRunStatus.COMPLETED, result.status());
        assertEquals("live_ai", result.mode().wireValue());
        assertEquals(LiveInvestigationService.TRUTH_LABEL, result.truthLabel());
        assertEquals(4, result.toolCallCount());
        assertEquals(3, result.modelCallCount());
        assertEquals(2_300, result.tokenUsage().totalTokens());
        assertTrue(result.estimatedCostUsd().signum() > 0);
        assertTrue(result.verification().hardErrors().isEmpty());
        assertTrue(result.comparison().rootCauseCorrect());
        assertTrue(result.comparison().affectedServiceCorrect());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Evidence>> evidenceCaptor = ArgumentCaptor
                .forClass(List.class);
        verify(model).synthesize(any(), evidenceCaptor.capture(), any());
        List<String> evidenceIds = evidenceCaptor.getValue().stream()
                .map(Evidence::evidenceId)
                .toList();
        assertTrue(evidenceIds.contains("cpt-v1-log-timeout-config"));
        assertTrue(evidenceIds.contains("cpt-v1-trace-failed-checkout"));
        assertFalse(evidenceIds.contains("cpt-v1-log-inventory-noise"));
    }

    @Test
    void returnsAnInspectableVerificationFailureForAnUnknownCitation() {
        stubCollections();
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Checkout failures threaten synthetic orders.",
                "Payment authorization reaches the configured timeout.",
                List.of(
                        new Claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                "The timeout is too short.",
                                List.of("model-invented-evidence-id")
                        ),
                        new Claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                "The failure occurs in payment authorization.",
                                List.of("cpt-v1-log-timeout-error")
                        )
                ),
                new SafeNextStep(
                        "Review the timeout configuration with a human.",
                        true
                )
        );
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        diagnosis,
                        metadata(ModelPhase.SYNTHESIZE, 1, 500, 200)
                )
        );

        LiveInvestigationResult result = service.investigate(
                SCENARIO_ID,
                new LiveInvestigationRequest(true)
        );

        assertEquals(LiveRunStatus.VERIFICATION_FAILED, result.status());
        assertEquals(
                List.of(VerificationErrorCode.UNKNOWN_EVIDENCE_ID),
                result.verification().hardErrors()
        );
        assertFalse(result.verification().citationValidity().valid());
    }

    @Test
    void rejectsAnUnconfirmedRequestBeforeAnyModelCall() {
        assertThrows(
                LiveInvestigationException.class,
                () -> service.investigate(
                        SCENARIO_ID,
                        new LiveInvestigationRequest(false)
                )
        );

        verify(model, never()).collect(
                any(), anyList(), anyList(), anyInt(), any()
        );
        verify(model, never()).synthesize(any(), anyList(), any());
    }

    private void stubCollections() {
        when(model.collect(any(), anyList(), anyList(), eq(1), any())).thenReturn(
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
                                call("call-runbook", ToolName.RETRIEVE_RUNBOOKS, Map.of(
                                        "query", "payment timeout trace",
                                        "max_results", 4
                                ))
                        ),
                        metadata(ModelPhase.COLLECT, 1, 600, 100)
                )
        );
        when(model.collect(any(), anyList(), anyList(), eq(2), any())).thenReturn(
                new CollectionModelResult(
                        List.of(call("call-trace", ToolName.GET_TRACE, Map.of(
                                "trace_id", "cpt-trace-4821"
                        ))),
                        metadata(ModelPhase.COLLECT, 2, 500, 100)
                )
        );
    }

    private CollectionToolCall call(
            String callId,
            ToolName toolName,
            Map<String, Object> arguments
    ) {
        return new CollectionToolCall(callId, toolName, arguments);
    }

    private ModelCallMetadata metadata(
            ModelPhase phase,
            int round,
            int input,
            int output
    ) {
        return new ModelCallMetadata(
                phase,
                round,
                "response-" + phase.wireValue() + "-" + round,
                "gemini-test-version",
                new ModelTokenUsage(input, output, input + output),
                25
        );
    }

    private Diagnosis correctDiagnosis() {
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
                                        "cpt-v1-log-timeout-error",
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
                                        "cpt-v1-log-timeout-error",
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

    @Test
    void allocatesTheDeadlineWithoutStarvingSynthesis() {
        assertEquals(
                Duration.ofSeconds(28),
                LiveInvestigationService.collectionTimeoutFor(
                        Duration.ZERO,
                        1
                ).orElseThrow()
        );
        assertEquals(
                Duration.ofSeconds(8),
                LiveInvestigationService.collectionTimeoutFor(
                        Duration.ofSeconds(14),
                        2
                ).orElseThrow()
        );
        assertTrue(LiveInvestigationService.collectionTimeoutFor(
                Duration.ofSeconds(22),
                2
        ).isEmpty());
        assertEquals(
                Duration.ofSeconds(28),
                LiveInvestigationService.synthesisTimeoutFor(
                        Duration.ofSeconds(16)
                ).orElseThrow()
        );
        assertTrue(LiveInvestigationService.synthesisTimeoutFor(
                Duration.ofSeconds(44)
        ).isEmpty());
    }
}
