package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @BeforeEach
    void resetMocks() {
        reset(runtime, models, quota);
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
                .andExpect(jsonPath("$.outcome").value("blocked_before_ai"))
                .andExpect(jsonPath("$.safety.decision").value("blocked"))
                .andExpect(jsonPath("$.safety.reason_code")
                        .value("employee_compensation_request"))
                .andExpect(jsonPath("$.runtime.runner_invoked").value(false))
                .andExpect(jsonPath("$.runtime.framework").value("not_invoked"))
                .andExpect(jsonPath("$.events.length()").value(0))
                .andExpect(jsonPath("$.tool_events.length()").value(0))
                .andExpect(jsonPath("$.receipt.model_calls").value(0))
                .andExpect(jsonPath("$.receipt.adk_tool_calls").value(0))
                .andExpect(jsonPath("$.receipt.embedding_calls").value(0))
                .andExpect(jsonPath("$.receipt.action_executed").value(false));

        verifyNoInteractions(runtime, models);
        verify(quota, never()).tryConsume(anyInt());
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
