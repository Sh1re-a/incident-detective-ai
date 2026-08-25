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

    @BeforeEach
    void resetModel() {
        reset(model);
    }

    @Test
    void returnsATruthfulLiveResponseWithoutGroundTruthLeakage() throws Exception {
        when(model.collect(any(), anyList(), anyList(), eq(1))).thenReturn(
                new CollectionModelResult(
                        List.of(),
                        metadata(ModelPhase.COLLECT, 1)
                )
        );
        when(model.synthesize(any(), anyList())).thenReturn(
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
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains("test-only-key"));
        assertFalse(json.contains("\"ground_truth\":"));
        assertFalse(json.contains("claim_support"));
        assertFalse(json.contains("allowed_evidence_ids"));
        assertFalse(json.contains("cpt-v1-log-inventory-noise"));
    }

    @Test
    void requiresExplicitConfirmationBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));

        verify(model, never()).collect(any(), anyList(), anyList(), anyInt());
        verify(model, never()).synthesize(any(), anyList());
    }

    @Test
    void mapsProviderContractAndTimeoutFailuresWithoutRawDetails() throws Exception {
        when(model.collect(any(), anyList(), anyList(), eq(1)))
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
        when(model.collect(any(), anyList(), anyList(), eq(1)))
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
    void returnsNotFoundBeforeCallingTheModel() throws Exception {
        mockMvc.perform(post(PATH, "unknown-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCENARIO_NOT_FOUND"));

        verify(model, never()).collect(any(), anyList(), anyList(), anyInt());
    }

    private ModelCallMetadata metadata(ModelPhase phase, int round) {
        return new ModelCallMetadata(
                phase,
                round,
                "test-response-id",
                "gemini-test-version",
                new ModelTokenUsage(10, 5, 15),
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
