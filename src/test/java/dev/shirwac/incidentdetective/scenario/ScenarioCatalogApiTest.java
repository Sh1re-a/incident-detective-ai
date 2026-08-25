package dev.shirwac.incidentdetective.scenario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioCatalogApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsSafeScenarioSummariesInFixtureOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarios.length()").value(2))
                .andExpect(jsonPath("$.scenarios[*].scenario_id").value(contains(
                        "checkout-orders-at-risk-v1",
                        "checkout-cart-segment-failures-v1"
                )))
                .andExpect(jsonPath("$.scenarios[0].title")
                        .value("Checkout errors threaten orders"))
                .andExpect(jsonPath("$.scenarios[0].business_impact_summary").exists())
                .andExpect(jsonPath("$.scenarios[0].initial_symptoms.length()").value(2))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        assertFalse(responseJson.contains("ground_truth"));
        assertFalse(responseJson.contains("evidence_inventory"));
        assertFalse(responseJson.contains("recorded_diagnosis"));
        assertFalse(responseJson.contains("expected_root_cause_code"));
    }
}
