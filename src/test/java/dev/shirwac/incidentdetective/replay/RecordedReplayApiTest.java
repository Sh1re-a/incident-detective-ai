package dev.shirwac.incidentdetective.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecordedReplayApiTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "checkout-orders-at-risk-v1, PAYMENT_TIMEOUT_CONFIG, cpt-v1-log-inventory-noise",
            "checkout-cart-segment-failures-v1, INVENTORY_SCHEMA_MISMATCH, cic-v1-log-catalog-noise"
    })
    void returnsACompletedReplayWithoutGroundTruthLeakage(
            String scenarioId,
            String expectedRootCause,
            String unseenNoiseEvidenceId
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/v1/scenarios/{scenarioId}/runs/recorded-replay",
                        scenarioId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario_id").value(scenarioId))
                .andExpect(jsonPath("$.mode").value("recorded_replay"))
                .andExpect(jsonPath("$.truth_label").value(
                        RecordedReplayService.TRUTH_LABEL
                ))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.tool_events.length()").value(4))
                .andExpect(jsonPath("$.tool_events[0].evidence[0].evidence_type").exists())
                .andExpect(jsonPath("$.verification.diagnosis_schema_pass").value(true))
                .andExpect(jsonPath("$.verification.citation_validity.valid").value(true))
                .andExpect(jsonPath("$.verification.evidence_precision.score").value(1.0))
                .andExpect(jsonPath("$.comparison.expected_root_cause_code").value(
                        expectedRootCause
                ))
                .andExpect(jsonPath("$.model_id").value(nullValue()))
                .andExpect(jsonPath("$.prompt_version").value(nullValue()))
                .andExpect(jsonPath("$.token_usage").value(nullValue()))
                .andExpect(jsonPath("$.estimated_cost_usd").value(nullValue()))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        assertFalse(responseJson.contains(unseenNoiseEvidenceId));
        assertFalse(responseJson.contains("\"ground_truth\""));
        assertFalse(responseJson.contains("\"claim_support\""));
        assertFalse(responseJson.contains("\"allowed_evidence_ids\""));
        assertFalse(responseJson.contains("\"expected_claims\""));
        assertFalse(responseJson.contains("\"relevant_runbooks\""));
    }

    @Test
    void returnsProblemDetailsForAnUnknownScenario() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/scenarios/{scenarioId}/runs/recorded-replay",
                        "unknown-scenario"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recorded scenario not found"))
                .andExpect(jsonPath("$.detail").value(
                        "Recorded scenario not found: unknown-scenario"
                ));
    }
}
