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
        "incident-detective.ai.provider=vertex_ai",
        "incident-detective.ai.vertex-project=capabilities-test-project-secret",
        "incident-detective.ai.vertex-location=europe-west1",
        "incident-detective.ai.model-id=gemini-3.1-flash-lite",
        "incident-detective.ai.thinking-level=MINIMAL",
        "incident-detective.ai.prompt-version=gemini-live-v7"
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
                        .value("capabilities-v4"))
                .andExpect(jsonPath("$.synthetic_only").value(true))
                .andExpect(jsonPath("$.remediation_enabled").value(false))
                .andExpect(jsonPath("$.provider.transport").value("vertex_ai"))
                .andExpect(jsonPath("$.provider.authentication_mode")
                        .value("adc"))
                .andExpect(jsonPath("$.provider.location")
                        .value("europe-west1"))
                .andExpect(jsonPath("$.provider.routing_configuration_complete")
                        .value(true))
                .andExpect(jsonPath("$.provider.credential_status")
                        .value("not_checked"))
                .andExpect(jsonPath("$.deployment.platform").value("local"))
                .andExpect(jsonPath("$.deployment.revision")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.deployment.build_git_sha")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.knowledge_corpus.manifest_version")
                        .value("nordly-knowledge-manifest-v2"))
                .andExpect(jsonPath("$.knowledge_corpus.corpus_version")
                        .value("nordly-knowledge-corpus-v2"))
                .andExpect(jsonPath("$.knowledge_corpus.corpus_content_sha256")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f]{64}"
                        )))
                .andExpect(jsonPath(
                        "$.knowledge_corpus.eligible_document_count"
                ).value(13))
                .andExpect(jsonPath(
                        "$.knowledge_corpus.eligible_chunk_count"
                ).value(27))
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
                .andExpect(jsonPath("$.diagnostic_probe.function_name")
                        .value("run_diagnostic_probe"))
                .andExpect(jsonPath("$.diagnostic_probe.allowed_probe_ids")
                        .value(contains(
                                "service_health",
                                "dependency_status",
                                "release_metadata",
                                "config_fingerprint_diff"
                        )))
                .andExpect(jsonPath("$.diagnostic_probe.case_bound")
                        .value(true))
                .andExpect(jsonPath("$.diagnostic_probe.read_only")
                        .value(true))
                .andExpect(jsonPath("$.diagnostic_probe.action_executed")
                        .value(false))
                .andExpect(jsonPath("$.live_ai.request_routing_configured")
                        .value(true))
                .andExpect(jsonPath("$.live_ai.explicit_confirmation_required")
                        .value(true))
                .andExpect(jsonPath("$.live_ai.model_id")
                        .value("gemini-3.1-flash-lite"))
                .andExpect(jsonPath("$.live_ai.thinking_level").value("MINIMAL"))
                .andExpect(jsonPath("$.live_ai.prompt_version")
                        .value("gemini-live-v7"))
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
                .andExpect(jsonPath("$.generated_cases.incident_families")
                        .value(contains(
                                "payment_timeout",
                                "catalog_cache_invalidation",
                                "order_event_backlog",
                                "order_idempotency_failure"
                        )))
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
                .andExpect(jsonPath("$.retrieval.index_status")
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
        assertFalse(json.contains("capabilities-test-project-secret"));
        assertFalse(json.contains("gemini_api_key"));
        assertFalse(json.contains("vertex_project"));
        assertFalse(json.contains("database_password"));
        assertFalse(json.contains("database_username"));
        assertFalse(json.contains("database_url"));
    }
}
