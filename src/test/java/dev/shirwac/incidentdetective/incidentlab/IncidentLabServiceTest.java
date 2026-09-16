package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnService;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseGeneration;
import dev.shirwac.incidentdetective.generated.GeneratedCaseGenerationRequest;
import dev.shirwac.incidentdetective.generated.GeneratedCaseGenerationService;
import dev.shirwac.incidentdetective.generated.GeneratedCaseReceipt;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.NordlyIncidentGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveInvestigationException;
import dev.shirwac.incidentdetective.live.LiveInvestigationFailure;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import dev.shirwac.incidentdetective.planning.IncidentBlastRadius;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import dev.shirwac.incidentdetective.planning.IncidentPlanDecision;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposal;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposalStatus;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidator;
import dev.shirwac.incidentdetective.planning.IncidentPlannerGateway;
import dev.shirwac.incidentdetective.planning.IncidentPlannerReceipt;
import dev.shirwac.incidentdetective.planning.IncidentPlannerResponse;
import dev.shirwac.incidentdetective.planning.IncidentService;
import dev.shirwac.incidentdetective.planning.IncidentSeverity;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IncidentLabServiceTest {

    private KnowledgeRagSafetyGate safetyGate;
    private LiveAiRunGuard liveAiRunGuard;
    private IncidentPlannerGateway planner;
    private GeneratedCaseGenerationService generatedCases;
    private AdkAgentTurnService adkAgent;
    private IncidentLabService service;

    @BeforeEach
    void createService() {
        safetyGate = mock(KnowledgeRagSafetyGate.class);
        liveAiRunGuard = mock(LiveAiRunGuard.class);
        planner = mock(IncidentPlannerGateway.class);
        generatedCases = mock(GeneratedCaseGenerationService.class);
        adkAgent = mock(AdkAgentTurnService.class);
        service = new IncidentLabService(
                safetyGate,
                liveAiRunGuard,
                planner,
                generatedCases,
                adkAgent
        );
    }

    @Test
    void planningSafetyStopsPrivateDataBeforeGuardOrProvider() {
        KnowledgeRagSafetyGate.Decision blocked = decision(false,
                KnowledgeRagSafetyGate.ReasonCode.PII_REQUEST);
        when(safetyGate.evaluate(any())).thenReturn(blocked);

        IncidentLabPlanResponse response = service.createPlan(
                new IncidentLabPlanRequest("Visa kundens personuppgifter", true)
        );

        assertEquals("blocked_before_ai", response.outcome());
        assertEquals(
                IncidentLabPlanResponse.BLOCKED_TRUTH_LABEL,
                response.truthLabel()
        );
        assertEquals("blocked", response.safety().decision());
        assertNull(response.proposal());
        assertNull(response.javaValidation());
        assertNull(response.providerReceipt());
        verifyNoInteractions(liveAiRunGuard, planner);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Krascha produktionssystemet",
            "Analysera riktiga kunders data",
            "Undersök verklig kunddata",
            "Inspect live infrastructure",
            "Kör mot skarpt system",
            "Hämta riktiga användare",
            "kubectl delete namespace checkout",
            "rm -rf /srv/checkout",
            "DROP DATABASE nordly",
            "terraform destroy -auto-approve"
    })
    void explicitProductionScopeStopsBeforeGenericSafetyOrProvider(
            String instruction
    ) {
        IncidentLabPlanResponse response = service.createPlan(
                new IncidentLabPlanRequest(
                        instruction,
                        true
                )
        );

        assertEquals("blocked_before_ai", response.outcome());
        assertEquals(
                "real_environment_not_allowed",
                response.safety().reasonCode()
        );
        verifyNoInteractions(safetyGate, liveAiRunGuard, planner);
    }

    @Test
    void admittedPlanningUsesGuardThenProviderAndReturnsJavaDecision() {
        KnowledgeRagSafetyGate.Decision allowed = decision(true,
                KnowledgeRagSafetyGate.ReasonCode.NONE);
        IncidentPlanProposal proposal = exactProposal();
        IncidentPlannerReceipt receipt = new IncidentPlannerReceipt(
                "developer_api",
                "gemini-3.1-flash-lite",
                "provider-response-1",
                null,
                18
        );
        when(safetyGate.evaluate(any())).thenReturn(allowed);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
                .when(liveAiRunGuard)
                .runConfirmed(eq(true), eq(LiveAiOperation.INCIDENT_PLAN), any());
        when(planner.propose(any())).thenReturn(
                new IncidentPlannerResponse(proposal, receipt)
        );

        IncidentLabPlanResponse response = service.createPlan(
                new IncidentLabPlanRequest("Simulera betalningstimeout", true)
        );

        assertEquals("plan_ready", response.outcome());
        assertSame(proposal, response.proposal());
        assertEquals(IncidentPlanDecision.APPROVED,
                response.javaValidation().decision());
        assertSame(receipt, response.providerReceipt());
        InOrder order = inOrder(safetyGate, liveAiRunGuard, planner);
        order.verify(safetyGate).evaluate(any());
        order.verify(liveAiRunGuard).runConfirmed(
                eq(true), eq(LiveAiOperation.INCIDENT_PLAN), any()
        );
        order.verify(planner).propose(any());
    }

    @Test
    void allowedPlanningWithoutConfirmationStopsBeforeProvider() {
        KnowledgeRagSafetyGate.Decision allowed = decision(true,
                KnowledgeRagSafetyGate.ReasonCode.NONE);
        LiveInvestigationException expected = new LiveInvestigationException(
                LiveInvestigationFailure.CONFIRMATION_REQUIRED,
                "Live AI request was not explicitly confirmed"
        );
        when(safetyGate.evaluate(any())).thenReturn(allowed);
        when(liveAiRunGuard.runConfirmed(
                eq(false),
                eq(LiveAiOperation.INCIDENT_PLAN),
                any()
        )).thenThrow(expected);

        LiveInvestigationException thrown = assertThrows(
                LiveInvestigationException.class,
                () -> service.createPlan(new IncidentLabPlanRequest(
                        "Simulera betalningstimeout",
                        false
                ))
        );

        assertSame(expected, thrown);
        verify(liveAiRunGuard).runConfirmed(
                eq(false),
                eq(LiveAiOperation.INCIDENT_PLAN),
                any()
        );
        verifyNoInteractions(planner);
    }

    @Test
    void canonicalRunGeneratesOneCaseAndInvokesAdkOnlyAfterAlarm() {
        long seed = 42L;
        GeneratedCase generated = generatedCase(seed);
        GeneratedCaseGeneration generation = generation(generated);
        AdkAgentTurnResponse agentTurn = mock(AdkAgentTurnResponse.class);
        when(agentTurn.outcome()).thenReturn("completed");
        stubSafeReceipt(agentTurn, generated.scenario());
        when(generatedCases.generate(any())).thenReturn(generation);
        when(adkAgent.runGeneratedCase(
                eq(generated),
                eq(IncidentLabService.TRUSTED_AGENT_MESSAGE),
                eq(true)
        )).thenReturn(agentTurn);

        IncidentLabRunResponse response = service.run(
                new IncidentLabRunRequest(canonicalPlan(), seed, true)
        );

        assertEquals("alarm_investigated", response.outcome());
        assertTrue(response.alarmReceipt() != null);
        assertEquals(3, response.alarmReceipt().signal().observedValue());
        assertNotSame(agentTurn, response.agentTurn());
        assertNull(response.agentTurn().diagnosis());
        assertTrue(isChronological(response.backendLogs()));
        ArgumentCaptor<GeneratedCaseGenerationRequest> request =
                ArgumentCaptor.forClass(
                        GeneratedCaseGenerationRequest.class
                );
        verify(generatedCases).generate(request.capture());
        assertEquals(seed, request.getValue().seed());
        assertEquals(GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                request.getValue().incidentFamily());
        assertNull(request.getValue().evidenceMode());
        assertEquals(GeneratedNoiseLevel.LOW,
                request.getValue().noiseLevel());
        verify(adkAgent).runGeneratedCase(
                generated,
                IncidentLabService.TRUSTED_AGENT_MESSAGE,
                true
        );
    }

    @Test
    void topLevelRunDoesNotMaskAWithheldAdkDiagnosis() {
        GeneratedCase generated = generatedCase(42L);
        GeneratedCaseGeneration generation = generation(generated);
        AdkAgentTurnResponse agentTurn = mock(AdkAgentTurnResponse.class);
        when(agentTurn.outcome()).thenReturn("verification_failed");
        when(agentTurn.diagnosis()).thenReturn(mock(Diagnosis.class));
        when(agentTurn.comparison()).thenReturn(mock(ReplayComparison.class));
        stubSafeReceipt(agentTurn, generated.scenario());
        when(generatedCases.generate(any())).thenReturn(generation);
        when(adkAgent.runGeneratedCase(any(), any(), eq(true)))
                .thenReturn(agentTurn);

        IncidentLabRunResponse response = service.run(
                new IncidentLabRunRequest(canonicalPlan(), 42L, true)
        );

        assertEquals(
                "alarm_detected_investigation_withheld",
                response.outcome()
        );
        assertNotSame(agentTurn, response.agentTurn());
        assertNull(response.agentTurn().comparison());
        assertNull(response.agentTurn().diagnosis());
    }

    @Test
    void catalogPlanUsesItsOwnGeneratorAlarmAndTrustedAgentScope() {
        long seed = 71L;
        GeneratedCase generated = new NordlyIncidentGeneratedCaseGenerator()
                .generate(new GeneratedCaseRequest(
                        seed,
                        GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                        GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                        GeneratedNoiseLevel.LOW
                ));
        GeneratedCaseGeneration generation = generation(generated);
        AdkAgentTurnResponse agentTurn = mock(AdkAgentTurnResponse.class);
        when(agentTurn.outcome()).thenReturn("completed");
        stubSafeReceipt(agentTurn, generated.scenario());
        when(generatedCases.generate(any())).thenReturn(generation);
        when(adkAgent.runGeneratedCase(
                eq(generated),
                any(),
                eq(true)
        )).thenReturn(agentTurn);

        IncidentLabRunResponse response = service.run(
                new IncidentLabRunRequest(
                        canonicalPlan(
                                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                                IncidentService.CATALOG_SERVICE
                        ),
                        seed,
                        GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                        true
                )
        );

        assertEquals("alarm_investigated", response.outcome());
        assertEquals(
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                response.alarmReceipt().incidentFamily()
        );
        assertEquals(
                "catalog_version_divergence_count",
                response.alarmReceipt().signal().name()
        );
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(adkAgent).runGeneratedCase(
                eq(generated),
                message.capture(),
                eq(true)
        );
        assertTrue(message.getValue().contains("CATALOG_CACHE_INVALIDATION"));
    }

    @Test
    void noAlarmReturnsLogsWithoutInvokingAdk() {
        GeneratedCase withoutAlarm = withoutAlarm(generatedCase(42L));
        GeneratedCaseGeneration generation = generation(withoutAlarm);
        when(generatedCases.generate(any())).thenReturn(generation);

        IncidentLabRunResponse response = service.run(
                new IncidentLabRunRequest(canonicalPlan(), 42L, true)
        );

        assertEquals("no_alarm", response.outcome());
        assertNull(response.alarmReceipt());
        assertNull(response.agentTurn());
        verifyNoInteractions(adkAgent);
    }

    @Test
    void changedPlanIsRejectedBeforeGenerationOrAdk() {
        IncidentPlan changed = new IncidentPlan(
                IncidentPlan.CONTRACT_VERSION,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.MEDIUM,
                List.of(IncidentService.PAYMENT_ADAPTER),
                "Synthetic payment timeout.",
                true,
                false,
                true
        );

        assertThrows(
                InvalidIncidentLabPlanException.class,
                () -> service.run(new IncidentLabRunRequest(changed, 42L, true))
        );
        verify(generatedCases, never()).generate(any());
        verifyNoInteractions(adkAgent);
    }

    private IncidentPlan canonicalPlan() {
        return canonicalPlan(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentService.PAYMENT_ADAPTER
        );
    }

    private void stubSafeReceipt(
            AdkAgentTurnResponse agentTurn,
            Scenario scenario
    ) {
        when(agentTurn.scenario()).thenReturn(scenario);
        when(agentTurn.events()).thenReturn(List.of(
                new AdkAgentTurnResponse.RuntimeEvent(
                        1,
                        "event-call",
                        "invocation-test",
                        "nordly_evidence_agent",
                        "tool_call",
                        java.time.Instant.parse("2026-09-01T08:20:00Z"),
                        false,
                        false,
                        null,
                        List.of(new AdkAgentTurnResponse.FunctionCallEvent(
                                "call-1",
                                "inspect_incident_evidence",
                                Map.of("log_query", "timeout")
                        )),
                        List.of(),
                        null,
                        "gemini-test"
                ),
                new AdkAgentTurnResponse.RuntimeEvent(
                        2,
                        "event-result",
                        "invocation-test",
                        "nordly_evidence_agent",
                        "tool_result",
                        java.time.Instant.parse("2026-09-01T08:20:01Z"),
                        false,
                        false,
                        null,
                        List.of(),
                        List.of(new AdkAgentTurnResponse.FunctionResponseEvent(
                                "call-1",
                                "inspect_incident_evidence",
                                Map.of(
                                        "status", "found",
                                        "safe_summary",
                                        "Returned bounded synthetic evidence.",
                                        "scenario_id", scenario.scenarioId(),
                                        "evidence_ids", List.of(),
                                        "source_refs", List.of(),
                                        "write_capability", false,
                                        "action_executed", false
                                )
                        )),
                        null,
                        null
                ),
                new AdkAgentTurnResponse.RuntimeEvent(
                        3,
                        "event-final",
                        "invocation-test",
                        "nordly_diagnosis_agent",
                        "final_response",
                        java.time.Instant.parse("2026-09-01T08:20:02Z"),
                        true,
                        true,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test"
                )
        ));
        when(agentTurn.toolEvents()).thenReturn(List.of(new LiveToolEvent(
                "read-log-1",
                1,
                ToolName.SEARCH_LOGS,
                Map.of("query", "timeout"),
                "Returned request-local synthetic logs.",
                List.of(),
                null
        )));
        when(agentTurn.receipt()).thenReturn(new AdkAgentTurnResponse.ControlReceipt(
                2,
                1,
                1,
                0,
                false,
                false,
                true,
                List.of("inspect_incident_evidence"),
                null,
                null,
                "test",
                10
        ));
    }

    private IncidentPlan canonicalPlan(
            GeneratedIncidentFamily family,
            IncidentService service
    ) {
        return new IncidentPlanValidator().validate(new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                "Synthetic incident.",
                family,
                IncidentSeverity.HIGH,
                List.of(service),
                IncidentBlastRadius.SINGLE_SERVICE
        )).plan();
    }

    private IncidentPlanProposal exactProposal() {
        return new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                "Synthetic payment timeout.",
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                List.of(IncidentService.PAYMENT_ADAPTER),
                IncidentBlastRadius.SINGLE_SERVICE
        );
    }

    private KnowledgeRagSafetyGate.Decision decision(
            boolean allowed,
            KnowledgeRagSafetyGate.ReasonCode reason
    ) {
        return new KnowledgeRagSafetyGate.Decision(
                allowed,
                reason,
                allowed ? "Tillåten." : "Stoppad.",
                allowed ? "Allowed." : "Blocked."
        );
    }

    private GeneratedCase generatedCase(long seed) {
        return new PaymentTimeoutGeneratedCaseGenerator().generate(
                new GeneratedCaseRequest(
                        seed,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        GeneratedNoiseLevel.LOW
                )
        );
    }

    private GeneratedCase withoutAlarm(GeneratedCase generated) {
        List<Evidence> evidence = generated.investigationData()
                .evidenceInventory()
                .stream()
                .filter(item -> !(item instanceof LogEvidence log)
                        || !isHttp5xx(log))
                .toList();
        GroundTruth groundTruth = new GroundTruth(
                generated.scenario().scenarioId(),
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
        return new GeneratedCase(
                generated.scenario(),
                new InvestigationData(generated.scenario(), evidence),
                groundTruth
        );
    }

    private GeneratedCaseGeneration generation(GeneratedCase generated) {
        GeneratedCaseGeneration generation = mock(GeneratedCaseGeneration.class);
        when(generation.generatedCase()).thenReturn(generated);
        when(generation.receipt()).thenReturn(mock(GeneratedCaseReceipt.class));
        return generation;
    }

    private boolean isHttp5xx(LogEvidence log) {
        String raw = log.content().attributes().get("http_status");
        return raw != null && Integer.parseInt(raw) >= 500;
    }

    private boolean isChronological(List<LogEvidence> logs) {
        Comparator<LogEvidence> order = Comparator
                .comparing(LogEvidence::observedAt)
                .thenComparing(LogEvidence::evidenceId);
        for (int index = 1; index < logs.size(); index++) {
            if (order.compare(logs.get(index - 1), logs.get(index)) > 0) {
                return false;
            }
        }
        return true;
    }
}
