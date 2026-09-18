package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LiveAiStatusController.class)
@EnableConfigurationProperties(ApiCorsProperties.class)
class LiveAiStatusApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveAiStatusService service;

    @Test
    void exposesOnlyPresentationSafeAvailability() throws Exception {
        when(service.describe()).thenReturn(new LiveAiStatusResponse(
                LiveAiStatusResponse.CONTRACT_VERSION,
                LiveAiStatusResponse.LiveState.DAILY_BUDGET_EXHAUSTED,
                "daily_ai_allowance_exhausted",
                Instant.parse("2026-09-16T00:00:00Z"),
                3_600L,
                true,
                true,
                GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL
        ));

        mockMvc.perform(get("/api/v1/live-ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("live-ai-status-v1"))
                .andExpect(jsonPath("$.live_state")
                        .value("daily_budget_exhausted"))
                .andExpect(jsonPath("$.reason_code")
                        .value("daily_ai_allowance_exhausted"))
                .andExpect(jsonPath("$.retry_after_seconds").value(3_600))
                .andExpect(jsonPath("$.replay_available").value(true))
                .andExpect(jsonPath("$.daily_cost_guard_active").value(true))
                .andExpect(jsonPath("$.quota_scope")
                        .value("database_global"))
                .andExpect(jsonPath("$.daily_budget_micro_usd").doesNotExist())
                .andExpect(jsonPath("$.consumed_micro_usd").doesNotExist());
    }

    @Test
    void reportsBudgetStoreFailureWithoutCallingItAPgvectorFailure()
            throws Exception {
        when(service.describe()).thenThrow(
                new LiveAiBudgetStoreUnavailableException(
                        new DataAccessResourceFailureException(
                                "private database detail"
                        )
                )
        );

        mockMvc.perform(get("/api/v1/live-ai/status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(
                        "Live AI budget guard unavailable"
                ))
                .andExpect(jsonPath("$.detail").value(
                        "The shared live AI budget could not be verified, so no provider call was started."
                ))
                .andExpect(jsonPath("$.code").value(
                        "LIVE_AI_BUDGET_UNAVAILABLE"
                ));
    }
}
