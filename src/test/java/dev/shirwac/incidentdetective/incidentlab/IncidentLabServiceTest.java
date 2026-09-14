package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnService;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private GeneratedCaseFactory generatedCases;
    private AdkAgentTurnService adkAgent;
    private IncidentLabService service;

    @BeforeEach
    void createService() {
        safetyGate = mock(KnowledgeRagSafetyGate.class);
        liveAiRunGuard = mock(LiveAiRunGuard.class);
        planner = mock(IncidentPlannerGateway.class);
        generatedCases = mock(GeneratedCaseFactory.class);
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

    @Test
    void explicitProductionScopeStopsBeforeGenericSafetyOrProvider() {
        IncidentLabPlanResponse response = service.createPlan(
                new IncidentLabPlanRequest(
                        "Krascha produktionssystemet",
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
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
                .when(liveAiRunGuard)
                .runConfirmed(eq(true), any());
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
        order.verify(liveAiRunGuard).runConfirmed(eq(true), any());
        order.verify(planner).propose(any());
    }

    @Test
    void canonicalRunGeneratesOneCaseAndInvokesAdkOnlyAfterAlarm() {
        long seed = 42L;
        GeneratedCase generated = generatedCase(seed);
        AdkAgentTurnResponse agentTurn = mock(AdkAgentTurnResponse.class);
        when(agentTurn.outcome()).thenReturn("completed");
        when(generatedCases.create(any())).thenReturn(generated);
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
        assertEquals(3, response.alarmReceipt().observedFailures());
        assertSame(agentTurn, response.agentTurn());
        assertTrue(isChronological(response.backendLogs()));
        ArgumentCaptor<GeneratedCaseRequest> request = ArgumentCaptor.forClass(
                GeneratedCaseRequest.class
        );
        verify(generatedCases).create(request.capture());
        assertEquals(seed, request.getValue().seed());
        assertEquals(GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                request.getValue().incidentFamily());
        assertEquals(GeneratedEvidenceMode.DIAGNOSTIC,
                request.getValue().evidenceMode());
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
        AdkAgentTurnResponse agentTurn = mock(AdkAgentTurnResponse.class);
        when(agentTurn.outcome()).thenReturn("verification_failed");
        when(generatedCases.create(any())).thenReturn(generated);
        when(adkAgent.runGeneratedCase(any(), any(), eq(true)))
                .thenReturn(agentTurn);

        IncidentLabRunResponse response = service.run(
                new IncidentLabRunRequest(canonicalPlan(), 42L, true)
        );

        assertEquals(
                "alarm_detected_investigation_withheld",
                response.outcome()
        );
        assertSame(agentTurn, response.agentTurn());
    }

    @Test
    void noAlarmReturnsLogsWithoutInvokingAdk() {
        GeneratedCase withoutAlarm = withoutAlarm(generatedCase(42L));
        when(generatedCases.create(any())).thenReturn(withoutAlarm);

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
        verify(generatedCases, never()).create(any());
        verifyNoInteractions(adkAgent);
    }

    private IncidentPlan canonicalPlan() {
        return new IncidentPlanValidator().validate(exactProposal()).plan();
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
