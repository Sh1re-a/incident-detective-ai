package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key="
})
@AutoConfigureMockMvc
class LiveInvestigationMissingKeyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationModelGateway model;

    @Test
    void missingServerKeyReturnsASanitizedUnavailableResponse() throws Exception {
        mockMvc.perform(post(
                                "/api/v1/scenarios/{scenarioId}/runs/live-ai",
                                "checkout-orders-at-risk-v1"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_NOT_CONFIGURED"));

        verifyNoInteractions(model);
    }
}
