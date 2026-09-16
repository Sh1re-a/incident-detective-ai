package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.adk.enabled=false",
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key"
})
@AutoConfigureMockMvc
class AdkAgentTurnConfirmationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdkAgentRuntime runtime;

    @MockitoBean
    private AdkGeminiModelFactory models;

    @MockitoBean
    private GlobalDailyLiveQuota quota;

    @Test
    void missingConfirmationWinsBeforeDisabledAdkOrProviderUse()
            throws Exception {
        mockMvc.perform(post("/api/v1/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt(), anyLong(), anyLong());
    }

    @Test
    void confirmedRequestStillReportsDisabledAdkBeforeQuotaOrProviderUse()
            throws Exception {
        mockMvc.perform(post("/api/v1/agent/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(true)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LIVE_AI_DISABLED"));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt(), anyLong(), anyLong());
    }

    private String request(boolean confirmed) {
        return """
                {
                  "seed": 42,
                  "incident_family": "payment_timeout",
                  "evidence_mode": "diagnostic",
                  "noise_level": "low",
                  "message": "Undersök det syntetiska larmet med read-only evidence.",
                  "confirm_live_ai": %s
                }
                """.formatted(confirmed);
    }
}
