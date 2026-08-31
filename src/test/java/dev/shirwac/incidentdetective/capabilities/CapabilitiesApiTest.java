package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.live.LiveInvestigationService;
import dev.shirwac.incidentdetective.replay.RecordedReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=capabilities-test-secret",
        "incident-detective.ai.model-id=gemini-3.1-flash-lite",
        "incident-detective.ai.thinking-level=MINIMAL",
        "incident-detective.ai.prompt-version=gemini-live-v6"
})
@AutoConfigureMockMvc
class CapabilitiesApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsAStableTruthfulCapabilityContractWithoutSecrets() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version")
                        .value("capabilities-v2"))
                .andExpect(jsonPath("$.synthetic_only").value(true))
                .andExpect(jsonPath("$.remediation_enabled").value(false))
                .andExpect(jsonPath("$.modes[*].mode").value(contains(
                        "recorded_replay",
                        "live_ai"
                )))
                .andExpect(jsonPath("$.modes[0].truth_label")
                        .value(RecordedReplayService.TRUTH_LABEL))
                .andExpect(jsonPath("$.modes[1].truth_label")
                        .value(LiveInvestigationService.TRUTH_LABEL))
                .andExpect(jsonPath("$.modes[0].explicit_confirmation_required")
                        .value(false))
                .andExpect(jsonPath("$.modes[1].explicit_confirmation_required")
                        .value(true))
                .andExpect(jsonPath("$.tools[*].name").value(contains(
                        "get_metrics",
                        "search_logs",
                        "get_trace",
                        "retrieve_runbooks"
                )))
                .andExpect(jsonPath("$.tools[*].read_only")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is(true)
                        )))
                .andExpect(jsonPath("$.live_ai.credentials_configured")
                        .value(true))
                .andExpect(jsonPath("$.live_ai.request_configured")
                        .value(true))
                .andExpect(jsonPath("$.live_ai.explicit_confirmation_required")
                        .value(true))
                .andExpect(jsonPath("$.live_ai.model_id")
                        .value("gemini-3.1-flash-lite"))
                .andExpect(jsonPath("$.live_ai.thinking_level").value("MINIMAL"))
                .andExpect(jsonPath("$.live_ai.prompt_version")
                        .value("gemini-live-v6"))
                .andExpect(jsonPath("$.live_ai.budget.max_collection_rounds")
                        .value(2))
                .andExpect(jsonPath("$.live_ai.budget.max_tool_calls_total")
                        .value(8))
                .andExpect(jsonPath("$.live_ai.budget.max_tool_calls_per_round")
                        .value(3))
                .andExpect(jsonPath("$.live_ai.budget.hard_deadline_ms")
                        .value(45_000))
                .andExpect(jsonPath("$.live_ai.budget.provider_call_cap_ms")
                        .value(28_000))
                .andExpect(jsonPath(
                        "$.live_ai.budget.daily_live_run_limit"
                ).value(20))
                .andExpect(jsonPath("$.live_ai.budget.daily_quota_scope")
                        .value("process_local"))
                .andExpect(jsonPath("$.generated_cases.enabled").value(true))
                .andExpect(jsonPath("$.generated_cases.truth_label").value(
                        LiveInvestigationService.GENERATED_TRUTH_LABEL
                ))
                .andExpect(jsonPath(
                        "$.generated_cases.user_supplied_data_accepted"
                ).value(false))
                .andExpect(jsonPath("$.generated_cases.request_local_only")
                        .value(true))
                .andExpect(jsonPath("$.generated_cases.evidence_modes")
                        .value(contains("diagnostic", "insufficient_evidence")))
                .andExpect(jsonPath("$.generated_cases.noise_levels")
                        .value(contains("none", "low")))
                .andExpect(jsonPath("$.retrieval.backend")
                        .value("deterministic_fixture"))
                .andExpect(jsonPath(
                        "$.retrieval.vector_database_backend_active"
                ).value(false))
                .andExpect(jsonPath("$.retrieval.active_embedding_profile")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.prompt_cache.strategy")
                        .value("provider_implicit"))
                .andExpect(jsonPath("$.prompt_cache.explicit_caching_enabled")
                        .value(false))
                .andExpect(jsonPath(
                        "$.prompt_cache.cache_hit_claims_require_provider_metadata"
                ).value(true))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains("capabilities-test-secret"));
        assertFalse(json.contains("gemini_api_key"));
        assertFalse(json.contains("database_password"));
        assertFalse(json.contains("database_username"));
        assertFalse(json.contains("database_url"));
    }
}
