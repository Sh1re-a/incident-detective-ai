package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.function.Supplier;

import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.correctDiagnosis;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.diagnosisWithUnknownCitation;
import static dev.shirwac.incidentdetective.live.LiveInvestigationTestFixtures.stubCheckoutCollections;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key",
        "incident-detective.ai.model-id=gemini-test",
        "incident-detective.ai.prompt-version=test-prompt-v1"
})
@AutoConfigureMockMvc
class LiveInvestigationApiTest {

    private static final String PATH =
            "/api/v1/scenarios/{scenarioId}/runs/live-ai";
    private static final String SCENARIO_ID = "checkout-orders-at-risk-v1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationModelGateway model;

    @MockitoBean
    private LiveInvestigationAdmissionGuard admissionGuard;

    @BeforeEach
    void resetModel() {
        reset(model, admissionGuard);
        when(admissionGuard.admit(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0);
            return action.get();
        });
    }

    @Test
    void returnsATruthfulLiveResponseWithoutGroundTruthLeakage() throws Exception {
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        )).thenReturn(
                new CollectionModelResult(
                        List.of(),
                        metadata(ModelPhase.COLLECT, 1)
                )
        );
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        insufficientEvidence(),
                        metadata(ModelPhase.SYNTHESIZE, 1)
                )
        );

        MvcResult result = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("live_ai"))
                .andExpect(jsonPath("$.truth_label").value(
                        LiveInvestigationService.TRUTH_LABEL
                ))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.diagnosis.status")
                        .value("insufficient_evidence"))
                .andExpect(jsonPath("$.model_id").value("gemini-test"))
                .andExpect(jsonPath("$.model_call_count").value(2))
                .andExpect(jsonPath("$.tool_call_count").value(0))
                .andExpect(jsonPath(
                        "$.verification.claim_coverage.matched_claim_count"
                ).value(0))
                .andExpect(jsonPath(
                        "$.verification.claim_coverage.reference_claim_count"
                ).value(5))
                .andExpect(jsonPath("$.verification.claim_coverage.score")
                        .value(0.0))
                .andExpect(jsonPath("$.estimated_cost_usd").doesNotExist())
                .andExpect(jsonPath("$.estimated_cost_basis").value(
                        "No paid list-price estimate is configured for this model."
                ))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains("test-only-key"));
        assertFalse(json.contains("\"ground_truth\":"));
        assertFalse(json.contains("claim_support"));
        assertFalse(json.contains("allowed_evidence_ids"));
        assertFalse(json.contains("\"expected_claims\""));
        assertFalse(json.contains("cpt-v1-log-inventory-noise"));
    }

    @Test
    void returnsACompleteDiagnosedResponseWithToolsEvidenceAndVerification()
            throws Exception {
        stubCheckoutCollections(model);
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        correctDiagnosis(),
                        metadata(ModelPhase.SYNTHESIZE, 1)
                )
        );

        MvcResult result = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.tool_events.length()").value(4))
                .andExpect(jsonPath("$.tool_events[0].collection_round").value(1))
                .andExpect(jsonPath("$.tool_events[0].tool_name")
                        .value("get_metrics"))
                .andExpect(jsonPath("$.tool_events[1].tool_name")
                        .value("search_logs"))
                .andExpect(jsonPath("$.tool_events[2].tool_name")
                        .value("retrieve_runbooks"))
                .andExpect(jsonPath("$.tool_events[2].runbook_retrieval.backend")
                        .value("deterministic_fixture"))
                .andExpect(jsonPath(
                        "$.tool_events[2].runbook_retrieval.matches[0].rank"
                ).value(1))
                .andExpect(jsonPath(
                        "$.tool_events[2].runbook_retrieval.matches[0].cosine_similarity"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.tool_events[2].runbook_retrieval.embedding_profile"
                ).doesNotExist())
                .andExpect(jsonPath("$.tool_events[0].runbook_retrieval")
                        .doesNotExist())
                .andExpect(jsonPath("$.tool_events[3].collection_round").value(2))
                .andExpect(jsonPath("$.tool_events[3].tool_name")
                        .value("get_trace"))
                .andExpect(jsonPath("$.tool_events[3].evidence[0].evidence_id")
                        .value("cpt-v1-trace-failed-checkout"))
                .andExpect(jsonPath("$.diagnosis.status").value("diagnosed"))
                .andExpect(jsonPath("$.diagnosis.root_cause_code")
                        .value("PAYMENT_TIMEOUT_CONFIG"))
                .andExpect(jsonPath("$.diagnosis.affected_service")
                        .value("PAYMENT_ADAPTER"))
                .andExpect(jsonPath(
                        "$.diagnosis.safe_next_step.requires_human_approval"
                ).value(true))
                .andExpect(jsonPath("$.verification.citation_validity.valid")
                        .value(true))
                .andExpect(jsonPath("$.verification.evidence_precision.score")
                        .value(1.0))
                .andExpect(jsonPath(
                        "$.verification.claim_coverage.matched_claim_count"
                ).value(4))
                .andExpect(jsonPath(
                        "$.verification.claim_coverage.reference_claim_count"
                ).value(5))
                .andExpect(jsonPath("$.verification.claim_coverage.score")
                        .value(0.8))
                .andExpect(jsonPath("$.verification.hard_errors.length()")
                        .value(0))
                .andExpect(jsonPath("$.comparison.root_cause_correct")
                        .value(true))
                .andExpect(jsonPath("$.comparison.affected_service_correct")
                        .value(true))
                .andExpect(jsonPath("$.model_calls.length()").value(3))
                .andExpect(jsonPath("$.model_calls[0].phase").value("collect"))
                .andExpect(jsonPath("$.model_calls[2].phase")
                        .value("synthesize"))
                .andExpect(jsonPath("$.token_usage.total_tokens").value(1_315))
                .andExpect(jsonPath("$.token_usage.input_tokens").value(1_110))
                .andExpect(jsonPath("$.token_usage.cached_input_tokens").value(210))
                .andExpect(jsonPath("$.token_usage.uncached_input_tokens").value(900))
                .andExpect(jsonPath("$.token_usage.candidate_output_tokens").value(205))
                .andExpect(jsonPath("$.token_usage.thinking_output_tokens").value(0))
                .andExpect(jsonPath("$.prompt_cache.strategy")
                        .value("provider_implicit"))
                .andExpect(jsonPath("$.prompt_cache.provider_reported_model_calls")
                        .value(3))
                .andExpect(jsonPath("$.prompt_cache.cached_input_tokens").value(210))
                .andExpect(jsonPath("$.prompt_cache.cache_hit_observed").value(true))
                .andExpect(jsonPath("$.tool_call_count").value(4))
                .andExpect(jsonPath("$.model_call_count").value(3))
                .andExpect(jsonPath("$.limitations.length()").value(3))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains("test-only-key"));
        assertFalse(json.contains("\"ground_truth\":"));
        assertFalse(json.contains("allowed_evidence_ids"));
        assertFalse(json.contains("\"expected_claims\""));
        assertFalse(json.contains("cpt-v1-log-inventory-noise"));
    }

    @Test
    void returnsInspectableVerificationFailureForAnInventedEvidenceId()
            throws Exception {
        stubCheckoutCollections(model);
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        diagnosisWithUnknownCitation(),
                        metadata(ModelPhase.SYNTHESIZE, 1)
                )
        );

        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification_failed"))
                .andExpect(jsonPath("$.verification.citation_validity.valid")
                        .value(false))
                .andExpect(jsonPath(
                        "$.verification.citation_validity.unknown_evidence_ids[0]"
                ).value("model-invented-evidence-id"))
                .andExpect(jsonPath("$.verification.hard_errors[0]")
                        .value("unknown_evidence_id"))
                .andExpect(jsonPath("$.comparison.root_cause_correct")
                        .value(true))
                .andExpect(jsonPath("$.comparison.affected_service_correct")
                        .value(true));
    }

    @Test
    void requiresExplicitConfirmationBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));

        verify(model, never()).collect(
                any(), anyList(), anyList(), any(), anyInt(), any()
        );
        verify(model, never()).synthesize(any(), anyList(), any());
    }

    @Test
    void rejectsMalformedAndUnexpectedRequestFieldsBeforeCallingTheModel()
            throws Exception {
        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true,\"surprise\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(model, never()).collect(
                any(), anyList(), anyList(), any(), anyInt(), any()
        );
        verify(model, never()).synthesize(any(), anyList(), any());
    }

    @Test
    void mapsProviderContractAndTimeoutFailuresWithoutRawDetails() throws Exception {
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        ))
                .thenThrow(new ModelProviderException(
                        ModelProviderFailure.MALFORMED_RESPONSE,
                        "raw provider response must stay private"
                ));

        MvcResult malformed = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code")
                        .value("MALFORMED_MODEL_RESPONSE"))
                .andReturn();
        assertFalse(malformed.getResponse().getContentAsString()
                .contains("raw provider response"));

        reset(model);
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        ))
                .thenThrow(new ModelProviderException(
                        ModelProviderFailure.TIMEOUT,
                        "private timeout details"
                ));

        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("MODEL_PROVIDER_TIMEOUT"));
    }

    @Test
    void rejectsAnInvalidDiagnosisWithoutReturningModelContent() throws Exception {
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        )).thenReturn(
                new CollectionModelResult(
                        List.of(),
                        metadata(ModelPhase.COLLECT, 1)
                )
        );
        when(model.synthesize(any(), anyList(), any())).thenReturn(
                new SynthesisModelResult(
                        new Diagnosis(
                                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                                null,
                                null,
                                "private invalid model summary",
                                "private invalid technical detail",
                                List.of(),
                                new SafeNextStep(
                                        "Execute an unapproved change.",
                                        false
                                )
                        ),
                        metadata(ModelPhase.SYNTHESIZE, 1)
                )
        );

        MvcResult result = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("MALFORMED_MODEL_RESPONSE"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString()
                .contains("private invalid"));
        assertFalse(result.getResponse().getContentAsString()
                .contains("unapproved change"));
    }

    @Test
    void returnsNotFoundBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post(PATH, "unknown-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCENARIO_NOT_FOUND"));

        verify(model, never()).collect(
                any(), anyList(), anyList(), any(), anyInt(), any()
        );
    }

    private ModelCallMetadata metadata(ModelPhase phase, int round) {
        int input = 10;
        int output = 5;
        int cachedInput = 10;
        return new ModelCallMetadata(
                phase,
                round,
                "test-response-id",
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
                1
        );
    }

    private Diagnosis insufficientEvidence() {
        return new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "More evidence is required.",
                "No read-only evidence was returned by the model-selected tools.",
                List.of(),
                new SafeNextStep(
                        "Collect more evidence after human approval.",
                        true
                )
        );
    }
}
