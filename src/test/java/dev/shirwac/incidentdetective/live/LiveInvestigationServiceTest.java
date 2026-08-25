package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.verification.VerificationErrorCode;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.call;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.correctDiagnosis;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.diagnosisWithUnknownCitation;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.metadata;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.stubCheckoutCollections;
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
        "incident-detective.ai.model-id=gemini-3.5-flash-lite",
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
        stubCheckoutCollections(model);
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
        assertTrue(result.estimatedCostBasis().contains(
                "actual free-tier charge may be USD 0"
        ));
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
        stubCheckoutCollections(model);
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        diagnosisWithUnknownCitation(),
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

    @Test
    void rejectsATraceIdThatWasNotPreviouslyReturnedToTheModel() {
        when(model.collect(any(), anyList(), anyList(), eq(1), any())).thenReturn(
                new CollectionModelResult(
                        List.of(
                                call("call-logs", ToolName.SEARCH_LOGS, Map.of(
                                        "services", List.of("PAYMENT_ADAPTER"),
                                        "levels", List.of(),
                                        "query", "timeout",
                                        "start", "2026-08-25T09:55:00Z",
                                        "end", "2026-08-25T10:15:00Z"
                                )),
                                call("call-guessed-trace", ToolName.GET_TRACE, Map.of(
                                        "trace_id", "cpt-trace-4821"
                                ))
                        ),
                        metadata(ModelPhase.COLLECT, 1, 600, 100)
                )
        );

        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> service.investigate(
                        SCENARIO_ID,
                        new LiveInvestigationRequest(true)
                )
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, exception.failure());
        verify(model, never()).synthesize(any(), anyList(), any());
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
