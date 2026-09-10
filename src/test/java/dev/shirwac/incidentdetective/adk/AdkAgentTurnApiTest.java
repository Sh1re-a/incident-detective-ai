package dev.shirwac.incidentdetective.adk;

import com.google.adk.models.BaseLlm;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiDiagnosisDecoder;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.verification.CitationValidity;
import dev.shirwac.incidentdetective.domain.verification.ClaimCoverage;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.EvidencePrecision;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.adk.enabled=true",
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key"
})
@AutoConfigureMockMvc
class AdkAgentTurnApiTest {

    private static final String PATH = "/api/v1/agent/turns";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdkAgentRuntime runtime;

    @MockitoBean
    private AdkGeminiModelFactory models;

    @MockitoBean
    private GlobalDailyLiveQuota quota;

    @MockitoBean
    private GeminiDiagnosisDecoder diagnosisDecoder;

    @MockitoBean
    private GroundTruthInvestigationVerifier verifier;

    @MockitoBean
    private GeminiCostEstimator costEstimator;

    @BeforeEach
    void resetMocks() {
        reset(
                runtime,
                models,
                quota,
                diagnosisDecoder,
                verifier,
                costEstimator
        );
    }

    @Test
    void privateSalaryQuestionIsBlockedBeforeAdkProviderToolsAndEmbeddings()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Kan jag få reda på någon anställds lön?",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("nordly-adk-turn-v3"))
                .andExpect(jsonPath("$.outcome").value("blocked_before_ai"))
                .andExpect(jsonPath("$.safety.decision").value("blocked"))
                .andExpect(jsonPath("$.safety.reason_code")
                        .value("employee_compensation_request"))
                .andExpect(jsonPath("$.runtime.runner_invoked").value(false))
                .andExpect(jsonPath("$.runtime.framework").value("not_invoked"))
                .andExpect(jsonPath("$.provider_route").value((Object) null))
                .andExpect(jsonPath("$.workflow").value((Object) null))
                .andExpect(jsonPath("$.events.length()").value(0))
                .andExpect(jsonPath("$.tool_events.length()").value(0))
                .andExpect(jsonPath("$.receipt.model_calls").value(0))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(0))
                .andExpect(jsonPath("$.receipt.embedding_calls").value(0))
                .andExpect(jsonPath("$.receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.receipt.action_executed").value(false));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt());
    }

    @Test
    void completedTurnPublishesTheObservedSequentialWorkflowReceipt()
            throws Exception {
        stubAdmittedRun(validTrajectory(), 2);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("nordly-adk-turn-v3"))
                .andExpect(jsonPath("$.outcome").value("completed"))
                .andExpect(jsonPath("$.provider_route.transport")
                        .value("developer_api"))
                .andExpect(jsonPath("$.provider_route.authentication_mode")
                        .value("api_key"))
                .andExpect(jsonPath("$.provider_route.location")
                        .value((Object) null))
                .andExpect(jsonPath("$.provider_route.project").doesNotExist())
                .andExpect(jsonPath("$.provider_route.credentials")
                        .doesNotExist())
                .andExpect(jsonPath("$.runtime.prompt_version")
                        .value("nordly-adk-sequential-v2"))
                .andExpect(jsonPath("$.workflow.type")
                        .value("sequential_agent"))
                .andExpect(jsonPath("$.workflow.expected_agent_order[0]")
                        .value("nordly_evidence_agent"))
                .andExpect(jsonPath("$.workflow.expected_agent_order[1]")
                        .value("nordly_diagnosis_agent"))
                .andExpect(jsonPath("$.workflow.observed_agent_order[0]")
                        .value("nordly_evidence_agent"))
                .andExpect(jsonPath("$.workflow.observed_agent_order[1]")
                        .value("nordly_diagnosis_agent"))
                .andExpect(jsonPath("$.workflow.evidence_handoff")
                        .value("adk_function_response"))
                .andExpect(jsonPath("$.workflow.final_response_author")
                        .value("nordly_diagnosis_agent"))
                .andExpect(jsonPath("$.workflow.completed_in_order")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.agent_sequence_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.evidence_handoff_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.tool_boundary_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.final_author_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(true))
                .andExpect(jsonPath("$.receipt.model_calls").value(2))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(1))
                .andExpect(jsonPath("$.diagnosis").isMap());
    }

    @Test
    void invalidFinalAuthorFailsClosedAndWithholdsTheDiagnosis()
            throws Exception {
        AdkAgentRuntime.TrajectoryValidation invalid = new AdkAgentRuntime
                .TrajectoryValidation(
                AdkAgentRuntime.WORKFLOW_TYPE,
                expectedAgentOrder(),
                expectedAgentOrder(),
                AdkAgentRuntime.EVIDENCE_HANDOFF,
                AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                false,
                true,
                true,
                true,
                false,
                true,
                List.of("invalid_final_response_author")
        );
        stubAdmittedRun(invalid, 2);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome")
                        .value("verification_failed"))
                .andExpect(jsonPath("$.diagnosis").value((Object) null))
                .andExpect(jsonPath("$.workflow.completed_in_order")
                        .value(false))
                .andExpect(jsonPath("$.verification_event.final_author_valid")
                        .value(false))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(false));
    }

    @Test
    void extraModelCallFailsClosedEvenWhenTheTrajectoryLooksValid()
            throws Exception {
        stubAdmittedRun(validTrajectory(), 3);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome")
                        .value("verification_failed"))
                .andExpect(jsonPath("$.diagnosis").value((Object) null))
                .andExpect(jsonPath("$.receipt.model_calls").value(3))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(false));
    }

    @Test
    void explicitConfirmationIsRequiredBeforeQuotaOrProviderUse()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                false
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt());
    }

    @Test
    void unknownRequestFieldsAreRejectedBeforeTheAgentRuns() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed": 42,
                                  "incident_family": "catalog_cache_invalidation",
                                  "evidence_mode": "diagnostic",
                                  "noise_level": "low",
                                  "message": "Undersök larmet.",
                                  "confirm_live_ai": true,
                                  "production_logs": ["not accepted"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt());
    }

    private void stubAdmittedRun(
            AdkAgentRuntime.TrajectoryValidation trajectory,
            int modelCalls
    ) {
        AdkGeminiModelFactory.ModelLease lease = mock(
                AdkGeminiModelFactory.ModelLease.class
        );
        BaseLlm model = mock(BaseLlm.class);
        when(lease.model()).thenReturn(model);
        when(models.create()).thenReturn(lease);
        when(quota.tryConsume(anyInt())).thenReturn(
                new GlobalDailyLiveQuota.Decision(
                        true,
                        1,
                        20,
                        Instant.parse("2026-09-11T00:00:00Z")
                )
        );

        ToolExecution execution = new ToolExecution(
                "operation-metrics",
                ToolName.GET_METRICS,
                Map.of(),
                "Returned bounded synthetic evidence.",
                List.of(),
                null
        );
        List<com.google.adk.events.Event> events = List.of();
        when(runtime.run(any(), anyString(), any())).thenReturn(
                new AdkAgentRuntime.RunResult(
                        "session-42",
                        "turn-42",
                        events,
                        List.of(execution),
                        modelCalls,
                        1
                )
        );
        when(runtime.validateTrajectory(events)).thenReturn(trajectory);
        when(runtime.finalText(events)).thenReturn("{}");
        when(runtime.projectEvents(events, false)).thenReturn(List.of());

        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "CATALOG_SERVICE",
                "Kunder såg gamla produktuppgifter.",
                "Cache-invalideringen hade slutat fungera.",
                List.of(),
                new SafeNextStep(
                        "Låt en människa granska konfigurationen.",
                        true
                )
        );
        when(diagnosisDecoder.decode("{}")).thenReturn(diagnosis);
        when(verifier.verify(any(), any(), any())).thenReturn(
                successfulVerification()
        );
        when(costEstimator.estimate(
                anyString(),
                nullable(dev.shirwac.incidentdetective.replay.ModelTokenUsage.class)
        )).thenReturn(
                new ModelCostEstimate(null, null, "Not reported by test provider.")
        );
    }

    private AdkAgentRuntime.TrajectoryValidation validTrajectory() {
        return new AdkAgentRuntime.TrajectoryValidation(
                AdkAgentRuntime.WORKFLOW_TYPE,
                expectedAgentOrder(),
                expectedAgentOrder(),
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

    private List<String> expectedAgentOrder() {
        return List.of(
                AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                AdkAgentRuntime.DIAGNOSIS_AGENT_NAME
        );
    }

    private CompletedInvestigationVerification successfulVerification() {
        VerificationReport report = new VerificationReport(
                true,
                true,
                new CitationValidity(true, List.of()),
                EvidencePrecision.notApplicable(),
                ClaimCoverage.notApplicable(),
                DiagnosisCorrectness.diagnosis(true, true),
                List.of()
        );
        ReplayComparison comparison = new ReplayComparison(
                DiagnosisStatus.DIAGNOSED,
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "CATALOG_SERVICE",
                true,
                true,
                false
        );
        return new CompletedInvestigationVerification(report, comparison);
    }

    private String request(String message, boolean confirmed) {
        return """
                {
                  "seed": 42,
                  "incident_family": "catalog_cache_invalidation",
                  "evidence_mode": "diagnostic",
                  "noise_level": "low",
                  "message": "%s",
                  "confirm_live_ai": %s
                }
                """.formatted(message, confirmed);
    }
}
