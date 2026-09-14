package dev.shirwac.incidentdetective.adk;

import com.google.genai.errors.ApiException;
import com.google.adk.models.BaseLlm;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiDiagnosisDecoder;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.ExpectedClaim;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.InitialSymptom;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.scenario.TimeWindow;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.tools.InvalidToolArgumentsException;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingException;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingFailure;
import dev.shirwac.incidentdetective.rag.RunbookIndexNotReadyException;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdkAgentTurnServiceTest {

    private static final String MESSAGE =
            "Undersök det syntetiska larmet med read-only evidence.";
    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");
    private static final GeminiAiProperties LIVE_AI = new GeminiAiProperties(
            "test-only-key",
            true,
            "gemini-3.1-flash-lite",
            GeminiThinkingLevel.MINIMAL,
            GeminiPromptContracts.LIVE_PROMPT_VERSION
    );

    private final GeneratedCaseFactory cases = mock(GeneratedCaseFactory.class);
    private final LiveAiRunGuard liveRunGuard = mock(LiveAiRunGuard.class);
    private final AdkGeminiModelFactory models = mock(
            AdkGeminiModelFactory.class
    );
    private final AdkAgentRuntime runtime = mock(AdkAgentRuntime.class);
    private final GeminiDiagnosisDecoder diagnosisDecoder = mock(
            GeminiDiagnosisDecoder.class
    );
    private final GroundTruthInvestigationVerifier verifier = mock(
            GroundTruthInvestigationVerifier.class
    );
    private final BaseLlm model = mock(BaseLlm.class);
    private final AtomicBoolean insideAdmission = new AtomicBoolean();
    private final AdkAgentTurnService service = new AdkAgentTurnService(
            new AdkProperties(true),
            LIVE_AI,
            cases,
            new KnowledgeRagSafetyGate(),
            liveRunGuard,
            models,
            runtime,
            diagnosisDecoder,
            verifier,
            new GeminiCostEstimator(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @BeforeEach
    void admitExactlyTheSuppliedAction() {
        when(liveRunGuard.runConfirmed(anyBoolean(), any())).thenAnswer(
                invocation -> {
                    insideAdmission.set(true);
                    try {
                        return invocation
                                .<Supplier<AdkAgentTurnResponse>>getArgument(1)
                                .get();
                    } finally {
                        insideAdmission.set(false);
                    }
                }
        );
    }

    @Test
    void generatedCasePathUsesTheExactScenarioAndEvidenceWithoutRegeneration() {
        GeneratedCase generated = generatedCase("prebuilt-payment-timeout");
        stubRejectedRun(generated);

        AdkAgentTurnResponse response = service.runGeneratedCase(
                generated,
                MESSAGE,
                true
        );

        Evidence evidence = generated.investigationData()
                .evidenceInventory()
                .getFirst();
        assertSame(generated.scenario(), response.scenario());
        assertSame(
                evidence,
                response.toolEvents().getFirst().evidence().getFirst()
        );
        assertEquals("verification_failed", response.outcome());
        verify(runtime).run(same(generated), eq(MESSAGE), same(model));
        verify(cases, never()).create(any());
        verify(liveRunGuard, times(1)).runConfirmed(eq(true), any());
    }

    @Test
    void existingRequestPathStillGeneratesInsideItsSingleAdmission() {
        GeneratedCase generated = generatedCase("factory-payment-timeout");
        GeneratedCaseRequest generatedRequest = new GeneratedCaseRequest(
                42L,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        );
        AdkAgentTurnRequest request = new AdkAgentTurnRequest(
                42L,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW,
                MESSAGE,
                true
        );
        when(cases.create(generatedRequest)).thenAnswer(invocation -> {
            assertTrue(insideAdmission.get());
            return generated;
        });
        stubRejectedRun(generated);

        AdkAgentTurnResponse response = service.run(request);

        assertSame(generated.scenario(), response.scenario());
        verify(cases, times(1)).create(generatedRequest);
        verify(runtime).run(same(generated), eq(MESSAGE), same(model));
        verify(liveRunGuard, times(1)).runConfirmed(eq(true), any());
    }

    @Test
    void preservesRunbookIndexFailureForTheRagExceptionHandler() {
        RunbookIndexNotReadyException failure = new RunbookIndexNotReadyException(
                new RunbookIndexStatus(2, 2, 4)
        );
        stubRuntimeFailure(failure);

        RunbookIndexNotReadyException thrown = assertThrows(
                RunbookIndexNotReadyException.class,
                () -> service.runGeneratedCase(
                        generatedCase("index-not-ready"),
                        MESSAGE,
                        true
                )
        );

        assertSame(failure, thrown);
    }

    @Test
    void preservesEmbeddingFailureForTheRagExceptionHandler() {
        RunbookEmbeddingException failure = new RunbookEmbeddingException(
                RunbookEmbeddingFailure.UPSTREAM,
                "Test-only embedding provider failure"
        );
        stubRuntimeFailure(new IllegalStateException("ADK wrapper", failure));

        RunbookEmbeddingException thrown = assertThrows(
                RunbookEmbeddingException.class,
                () -> service.runGeneratedCase(
                        generatedCase("embedding-failure"),
                        MESSAGE,
                        true
                )
        );

        assertSame(failure, thrown);
    }

    @Test
    void preservesDatabaseFailureForTheRagExceptionHandler() {
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException(
                        "Test-only pgvector failure"
                );
        stubRuntimeFailure(new IllegalStateException("ADK wrapper", failure));

        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> service.runGeneratedCase(
                        generatedCase("database-failure"),
                        MESSAGE,
                        true
                )
        );

        assertSame(failure, thrown);
    }

    @Test
    void preservesInvalidToolArgumentsForTheControlExceptionHandler() {
        InvalidToolArgumentsException failure = mock(
                InvalidToolArgumentsException.class
        );
        stubRuntimeFailure(new IllegalStateException("ADK wrapper", failure));

        InvalidToolArgumentsException thrown = assertThrows(
                InvalidToolArgumentsException.class,
                () -> service.runGeneratedCase(
                        generatedCase("invalid-tool-arguments"),
                        MESSAGE,
                        true
                )
        );

        assertSame(failure, thrown);
    }

    @Test
    void classifiesInterruptedIoAsTimeoutInsteadOfGenericUpstreamFailure() {
        stubRuntimeFailure(new IllegalStateException(
                "ADK wrapper",
                new InterruptedIOException("Test-only read timeout")
        ));

        ModelProviderException thrown = assertThrows(
                ModelProviderException.class,
                () -> service.runGeneratedCase(
                        generatedCase("interrupted-io"),
                        MESSAGE,
                        true
                )
        );

        assertEquals(ModelProviderFailure.TIMEOUT, thrown.failure());
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void classifiesGatewayTimeoutStatusAsProviderTimeout() {
        ApiException failure = mock(ApiException.class);
        when(failure.code()).thenReturn(504);
        stubRuntimeFailure(failure);

        ModelProviderException thrown = assertThrows(
                ModelProviderException.class,
                () -> service.runGeneratedCase(
                        generatedCase("provider-timeout"),
                        MESSAGE,
                        true
                )
        );

        assertEquals(ModelProviderFailure.TIMEOUT, thrown.failure());
    }

    @Test
    void restoresInterruptFlagAndClassifiesInterruptedRunAsTimeout() {
        assertFalse(Thread.currentThread().isInterrupted());
        stubRuntimeFailure(new IllegalStateException(
                "ADK wrapper",
                new InterruptedException("Test-only interruption")
        ));

        try {
            ModelProviderException thrown = assertThrows(
                    ModelProviderException.class,
                    () -> service.runGeneratedCase(
                            generatedCase("interrupted-run"),
                            MESSAGE,
                            true
                    )
            );

            assertEquals(ModelProviderFailure.TIMEOUT, thrown.failure());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private void stubRuntimeFailure(RuntimeException failure) {
        AdkGeminiModelFactory.ModelLease lease = mock(
                AdkGeminiModelFactory.ModelLease.class
        );
        when(lease.model()).thenReturn(model);
        when(models.create()).thenReturn(lease);
        when(runtime.run(any(), eq(MESSAGE), eq(model))).thenThrow(failure);
    }

    private void stubRejectedRun(GeneratedCase generated) {
        AdkGeminiModelFactory.ModelLease lease = mock(
                AdkGeminiModelFactory.ModelLease.class
        );
        when(lease.model()).thenReturn(model);
        when(models.create()).thenReturn(lease);

        ToolExecution execution = new ToolExecution(
                "prebuilt-log-search",
                ToolName.SEARCH_LOGS,
                Map.of("query", "504"),
                "Returned the prebuilt synthetic timeout evidence.",
                List.of(generated.investigationData()
                        .evidenceInventory()
                        .getFirst()),
                null
        );
        List<com.google.adk.events.Event> events = List.of();
        when(runtime.run(any(), eq(MESSAGE), eq(model))).thenReturn(
                new AdkAgentRuntime.RunResult(
                        "session-prebuilt",
                        "turn-prebuilt",
                        events,
                        List.of(execution),
                        2,
                        1
                )
        );
        when(runtime.validateTrajectory(events)).thenReturn(validTrajectory());
        when(runtime.finalText(events)).thenReturn("{}");
        when(runtime.projectEvents(events, false)).thenReturn(List.of());
        when(diagnosisDecoder.decode("{}")).thenThrow(
                new ModelProviderException(
                        ModelProviderFailure.MALFORMED_RESPONSE,
                        "Test-only rejected Diagnosis contract"
                )
        );
    }

    private AdkAgentRuntime.TrajectoryValidation validTrajectory() {
        List<String> agents = List.of(
                AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                AdkAgentRuntime.DIAGNOSIS_AGENT_NAME
        );
        return new AdkAgentRuntime.TrajectoryValidation(
                AdkAgentRuntime.WORKFLOW_TYPE,
                agents,
                agents,
                AdkAgentRuntime.EVIDENCE_HANDOFF,
                AdkAgentRuntime.DIAGNOSIS_AGENT_NAME,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of()
        );
    }

    private GeneratedCase generatedCase(String scenarioId) {
        Scenario scenario = new Scenario(
                scenarioId,
                "Synthetic payment timeout",
                "Synthetic payment attempts return HTTP 504 responses.",
                NOW,
                new TimeWindow(NOW.minusSeconds(300), NOW.plusSeconds(300)),
                List.of("PAYMENT_ADAPTER"),
                "Some synthetic checkouts cannot complete.",
                List.of(new InitialSymptom(
                        "CHECKOUT_ERROR_RATE_HIGH",
                        "Synthetic checkout errors increased.",
                        NOW.plusSeconds(30)
                )),
                1
        );
        LogEvidence evidence = new LogEvidence(
                scenarioId + "-log-504",
                scenarioId,
                NOW.plusSeconds(60),
                "Synthetic payment adapter returned HTTP 504.",
                "synthetic/logs/" + scenarioId,
                new LogEvidence.LogContent(
                        "PAYMENT_ADAPTER",
                        "ERROR",
                        "Synthetic upstream payment timeout.",
                        Map.of(
                                "event_kind", "payment_attempt",
                                "http.status_code", "504"
                        )
                )
        );
        ExpectedClaim symptom = new ExpectedClaim(
                ClaimCode.OBSERVED_SYMPTOM,
                "PAYMENT_LATENCY_SPIKE"
        );
        GroundTruth groundTruth = new GroundTruth(
                scenarioId,
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(symptom),
                List.of(new ClaimSupport(
                        symptom.claimCode(),
                        symptom.claimValueCode(),
                        List.of(evidence.evidenceId())
                )),
                List.of()
        );
        return new GeneratedCase(
                scenario,
                new InvestigationData(scenario, List.of(evidence)),
                groundTruth
        );
    }
}
