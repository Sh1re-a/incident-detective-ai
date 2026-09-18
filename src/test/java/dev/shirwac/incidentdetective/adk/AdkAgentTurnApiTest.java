package dev.shirwac.incidentdetective.adk;

import com.google.adk.models.BaseLlm;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiDiagnosisDecoder;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.ModelResponseFailureMetadata;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.verification.CitationSupportResult;
import dev.shirwac.incidentdetective.domain.verification.CitationValidity;
import dev.shirwac.incidentdetective.domain.verification.ClaimCoverage;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.EvidencePrecision;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeFinding;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeId;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeOutcome;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeReceipt;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.adk.enabled=true",
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
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
                        .value("nordly-adk-turn-v4"))
                .andExpect(jsonPath("$.mode")
                        .value(AdkAgentTurnResponse.BLOCKED_MODE))
                .andExpect(jsonPath("$.truth_label")
                        .value(AdkAgentTurnResponse.BLOCKED_TRUTH_LABEL))
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
                .andExpect(jsonPath("$.diagnostic_probe")
                        .value((Object) null))
                .andExpect(jsonPath("$.receipt.model_calls").value(0))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(0))
                .andExpect(jsonPath("$.receipt.embedding_calls").value(0))
                .andExpect(jsonPath("$.receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.receipt.action_executed").value(false));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt(), anyLong(), anyLong());
    }

    @Test
    void completedTurnPublishesTheObservedSequentialWorkflowReceipt()
            throws Exception {
        stubAdmittedRun(validTrajectory(), 2, true);
        when(runtime.projectEvents(any(), eq(false))).thenReturn(List.of(
                eventWithRawFunctionResponse()
        ));
        String sentinel = "MODEL_PROSE_MUST_NOT_ESCAPE_9F2C";
        when(diagnosisDecoder.decode("{}")).thenReturn(new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "CATALOG_SERVICE",
                sentinel + " business",
                sentinel + " technical",
                List.of(new Claim(
                        ClaimCode.ROOT_CAUSE,
                        "CATALOG_CACHE_INVALIDATION_FAILURE",
                        sentinel + " claim",
                        List.of("catalog-log-1")
                )),
                new SafeNextStep(sentinel + " next step", true)
        ));

        String response = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("nordly-adk-turn-v4"))
                .andExpect(jsonPath("$.mode")
                        .value(AdkAgentTurnResponse.MODE))
                .andExpect(jsonPath("$.truth_label")
                        .value(AdkAgentTurnResponse.TRUTH_LABEL))
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
                        .value("nordly-adk-sequential-v3"))
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
                .andExpect(jsonPath("$.verification_event.direct_evidence_support_valid")
                        .value(true))
                .andExpect(jsonPath("$.diagnostic_probe.probe_id")
                        .value("service_health"))
                .andExpect(jsonPath("$.diagnostic_probe.outcome")
                        .value("observed"))
                .andExpect(jsonPath("$.diagnostic_probe.read_only")
                        .value(true))
                .andExpect(jsonPath("$.diagnostic_probe.action_executed")
                        .value(false))
                .andExpect(jsonPath("$.diagnostic_probe.duration_ms")
                        .value(7))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.status"
                ).value("found"))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.safe_summary"
                ).value("Returned bounded synthetic evidence."))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.evidence_ids[0]"
                ).value("catalog-log-1"))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.source_refs[0]"
                ).value("synthetic/catalog-log-1"))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.write_capability"
                ).value(false))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.action_executed"
                ).value(false))
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.operations"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.diagnostic_probe"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.events[0].function_responses[0].response.private_marker"
                ).doesNotExist())
                .andExpect(jsonPath("$.receipt.model_calls").value(2))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(1))
                .andExpect(jsonPath("$.receipt.read_operations").value(2))
                .andExpect(jsonPath("$.diagnosis").isMap())
                .andExpect(jsonPath("$.diagnosis.business_summary").value(
                        "Java released a diagnosis after deterministic "
                                + "verification of this generated synthetic case."
                ))
                .andExpect(jsonPath("$.diagnosis.technical_summary").value(
                        "Verified root-cause code "
                                + "CATALOG_CACHE_INVALIDATION_FAILURE and "
                                + "affected-service code CATALOG_SERVICE."
                ))
                .andExpect(jsonPath("$.diagnosis.claims[0].display_text").value(
                        "Verified root cause code: "
                                + "CATALOG_CACHE_INVALIDATION_FAILURE."
                ))
                .andExpect(jsonPath("$.diagnosis.safe_next_step.summary").value(
                        "Have a human review the cited read-only evidence "
                                + "before approving any change."
                ))
                .andExpect(jsonPath("$.diagnosis.safe_next_step.requires_human_approval")
                        .value(true))
                .andExpect(jsonPath("$.comparison").value((Object) null))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains(sentinel));
        assertFalse(response.contains("expected_root_cause_code"));
        assertFalse(response.contains("expected_affected_service"));
        assertFalse(response.contains("RAW_FUNCTION_RESPONSE_MUST_NOT_ESCAPE"));
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
                .andExpect(jsonPath("$.diagnostic_probe")
                        .value((Object) null))
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
                .andExpect(jsonPath("$.diagnostic_probe")
                        .value((Object) null))
                .andExpect(jsonPath("$.receipt.model_calls").value(3))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(false));
    }

    @Test
    void unsupportedClaimEvidenceLinkIsNamedInTheWithheldReceipt()
            throws Exception {
        stubAdmittedRun(validTrajectory(), 2);
        when(verifier.verify(any(), any(), any())).thenReturn(
                unsupportedEvidenceVerification()
        );

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
                .andExpect(jsonPath("$.diagnostic_probe")
                        .value((Object) null))
                .andExpect(jsonPath("$.comparison").value((Object) null))
                .andExpect(jsonPath("$.verification_event.schema_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.citations_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.direct_evidence_support_valid")
                        .value(false))
                .andExpect(jsonPath("$.verification_event.factual_result_matches_ground_truth")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(false))
                .andExpect(jsonPath("$.verification_event.summary")
                        .value("Java withheld the diagnosis: direct evidence support failed for 1 of 1 claim-to-evidence links."));
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
        verify(quota, never()).tryConsume(anyInt(), anyLong(), anyLong());
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
        verify(quota, never()).tryConsume(anyInt(), anyLong(), anyLong());
    }

    @Test
    void returnsARealReceiptAndLogsOnlySafeMetadataWhenDiagnosisIsRejected(
            CapturedOutput output
    ) throws Exception {
        stubAdmittedRun(validTrajectory(), 2);
        String rawModelValue = "DO-NOT-LOG-model-secret";
        String finalText = "{\"business_summary\":\""
                + rawModelValue
                + "\"}";
        when(runtime.finalText(any())).thenReturn(finalText);
        when(diagnosisDecoder.decode(finalText)).thenThrow(
                new ModelProviderException(
                        ModelProviderFailure.MALFORMED_RESPONSE,
                        "Gemini returned a response that did not match Diagnosis",
                        new ModelResponseFailureMetadata(
                                ModelResponseFailureMetadata.Category.BEAN_VALIDATION,
                                ModelResponseFailureMetadata.Reason.CONSTRAINT_VIOLATION,
                                List.of(
                                        "safeNextStep.requiresHumanApproval",
                                        "unsafe\n" + rawModelValue
                                )
                        )
                )
        );
        when(runtime.projectEvents(any(), eq(false))).thenReturn(List.of(
                new AdkAgentTurnResponse.RuntimeEvent(
                        3,
                        "event-final",
                        "invocation-42",
                        AdkAgentRuntime.DIAGNOSIS_AGENT_NAME,
                        "final_response",
                        Instant.parse("2026-09-11T10:00:00Z"),
                        true,
                        true,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test-version"
                )
        ));

        String response = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                "Undersök larmet med endast read-only verktyg.",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("nordly-adk-turn-v4"))
                .andExpect(jsonPath("$.outcome")
                        .value("verification_failed"))
                .andExpect(jsonPath("$.diagnosis").value((Object) null))
                .andExpect(jsonPath("$.diagnostic_probe")
                        .value((Object) null))
                .andExpect(jsonPath("$.verification").value((Object) null))
                .andExpect(jsonPath("$.comparison").value((Object) null))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].content_withheld").value(true))
                .andExpect(jsonPath("$.tool_events.length()").value(1))
                .andExpect(jsonPath("$.workflow.completed_in_order")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.schema_valid")
                        .value(false))
                .andExpect(jsonPath("$.verification_event.final_author_valid")
                        .value(true))
                .andExpect(jsonPath("$.verification_event.answer_released")
                        .value(false))
                .andExpect(jsonPath("$.receipt.model_calls").value(2))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(1))
                .andExpect(jsonPath("$.receipt.read_operations").value(1))
                .andExpect(jsonPath("$.receipt.embedding_calls").value(0))
                .andExpect(jsonPath("$.receipt.action_executed").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String logs = output.getAll();
        assertTrue(logs.contains("category=BEAN_VALIDATION"));
        assertTrue(logs.contains("reason=CONSTRAINT_VIOLATION"));
        assertTrue(logs.contains(
                "property_paths=[safeNextStep.requiresHumanApproval]"
        ));
        assertFalse(logs.contains(rawModelValue));
        assertFalse(response.contains(rawModelValue));
        verify(verifier, never()).verify(any(), any(), any());
    }

    private void stubAdmittedRun(
            AdkAgentRuntime.TrajectoryValidation trajectory,
            int modelCalls
    ) {
        stubAdmittedRun(trajectory, modelCalls, false);
    }

    private void stubAdmittedRun(
            AdkAgentRuntime.TrajectoryValidation trajectory,
            int modelCalls,
            boolean includeDiagnosticProbe
    ) {
        AdkGeminiModelFactory.ModelLease lease = mock(
                AdkGeminiModelFactory.ModelLease.class
        );
        BaseLlm model = mock(BaseLlm.class);
        when(lease.model()).thenReturn(model);
        when(models.create()).thenReturn(lease);
        when(quota.tryConsume(anyInt(), anyLong(), anyLong())).thenReturn(
                new GlobalDailyLiveQuota.Decision(
                        true,
                        1,
                        20,
                        20_000,
                        200_000,
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
        when(runtime.run(any(), anyString(), any())).thenAnswer(invocation -> {
            GeneratedCase generated = invocation.getArgument(0);
            return new AdkAgentRuntime.RunResult(
                        "session-42",
                        "turn-42",
                        events,
                        List.of(execution),
                        includeDiagnosticProbe
                                ? diagnosticProbeReceipt(generated)
                                : null,
                        modelCalls,
                        1
                );
        });
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

    private DiagnosticProbeReceipt diagnosticProbeReceipt(
            GeneratedCase generated
    ) {
        String evidenceId = generated.investigationData()
                .evidenceInventory().getFirst().evidenceId();
        return new DiagnosticProbeReceipt(
                generated.scenario().scenarioId(),
                DiagnosticProbeId.SERVICE_HEALTH,
                DiagnosticProbeOutcome.OBSERVED,
                "Inspected request-local service health signals.",
                List.of(new DiagnosticProbeFinding(
                        "service_health",
                        "CATALOG_SERVICE",
                        "degraded",
                        "Observed one bounded failure signal.",
                        List.of(evidenceId)
                )),
                false,
                7,
                true,
                false
        );
    }

    private AdkAgentTurnResponse.RuntimeEvent eventWithRawFunctionResponse() {
        return new AdkAgentTurnResponse.RuntimeEvent(
                2,
                "event-tool-response",
                "invocation-42",
                AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                "function_response",
                Instant.parse("2026-09-11T10:00:00Z"),
                false,
                true,
                null,
                List.of(),
                List.of(new AdkAgentTurnResponse.FunctionResponseEvent(
                        "inspect-call-1",
                        AdkAgentRuntime.TOOL_NAME,
                        Map.of(
                                "status", "found",
                                "safe_summary", "Returned bounded synthetic evidence.",
                                "scenario_id", "raw-scenario-must-not-escape",
                                "evidence_ids", List.of("catalog-log-1"),
                                "source_refs", List.of("synthetic/catalog-log-1"),
                                "operations", List.of(Map.of(
                                        "private_marker",
                                        "RAW_FUNCTION_RESPONSE_MUST_NOT_ESCAPE"
                                )),
                                "diagnostic_probe", Map.of(
                                        "private_marker",
                                        "RAW_FUNCTION_RESPONSE_MUST_NOT_ESCAPE"
                                ),
                                "private_marker",
                                "RAW_FUNCTION_RESPONSE_MUST_NOT_ESCAPE"
                        )
                )),
                null,
                "gemini-test-version"
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

    private CompletedInvestigationVerification unsupportedEvidenceVerification() {
        VerificationReport report = new VerificationReport(
                true,
                true,
                new CitationValidity(true, List.of()),
                EvidencePrecision.scored(List.of(new CitationSupportResult(
                        ClaimCode.ROOT_CAUSE,
                        "CATALOG_CACHE_INVALIDATION_FAILURE",
                        "evidence-1",
                        false
                ))),
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
