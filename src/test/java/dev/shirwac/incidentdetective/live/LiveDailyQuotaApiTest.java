package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key"
})
@AutoConfigureMockMvc
class LiveDailyQuotaApiTest {

    private static final String PATH =
            "/api/v1/scenarios/{scenarioId}/runs/live-ai";
    private static final String SCENARIO_ID = "checkout-orders-at-risk-v1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationModelGateway model;

    @MockitoBean
    private LiveInvestigationAdmissionGuard admissionGuard;

    @MockitoBean
    private GlobalDailyLiveQuota dailyQuota;

    @BeforeEach
    void executeAdmittedActions() {
        reset(model, admissionGuard, dailyQuota);
        when(admissionGuard.admit(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0);
            return action.get();
        });
    }

    @Test
    void rejectsAnExhaustedDailyQuotaBeforeCallingGemini() throws Exception {
        Instant resetsAt = Instant.now().plusSeconds(3_600);
        when(dailyQuota.tryConsume(
                LiveInvestigationService.DAILY_LIVE_RUN_LIMIT
        )).thenReturn(new GlobalDailyLiveQuota.Decision(
                false,
                LiveInvestigationService.DAILY_LIVE_RUN_LIMIT,
                LiveInvestigationService.DAILY_LIVE_RUN_LIMIT,
                resetsAt
        ));

        MvcResult result = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_DAILY_LIMIT_REACHED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn();

        long retryAfterSeconds = Long.parseLong(
                result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)
        );
        assertTrue(retryAfterSeconds > 0);
        verify(dailyQuota).tryConsume(
                LiveInvestigationService.DAILY_LIVE_RUN_LIMIT
        );
        verifyNoInteractions(model);
    }
}
