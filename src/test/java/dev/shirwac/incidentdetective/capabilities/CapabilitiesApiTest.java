package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
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
                        .value("capabilities-v5"))
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
                        .value("nordly-knowledge-manifest-v3"))
                .andExpect(jsonPath("$.knowledge_corpus.corpus_version")
                        .value("nordly-knowledge-corpus-v3"))
                .andExpect(jsonPath("$.knowledge_corpus.corpus_content_sha256")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "[0-9a-f]{64}"
                        )))
                .andExpect(jsonPath(
                        "$.knowledge_corpus.eligible_document_count"
                ).value(14))
                .andExpect(jsonPath(
                        "$.knowledge_corpus.eligible_chunk_count"
                ).value(32))
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
                .andExpect(jsonPath("$.incident_lab.enabled").value(false))
                .andExpect(jsonPath("$.incident_lab.availability_reason")
                        .value("rag_profile_required"))
                .andExpect(jsonPath("$.incident_lab.required_profiles")
                        .value(contains("rag")))
                .andExpect(jsonPath("$.incident_lab.plan_contract_version")
                        .value("incident-lab-plan-v1"))
                .andExpect(jsonPath("$.incident_lab.run_contract_version")
                        .value(IncidentLabRunResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.incident_lab.orchestration")
                        .value("sequential_agent"))
                .andExpect(jsonPath("$.incident_lab.expected_agent_order")
                        .value(contains(
                                "nordly_evidence_agent",
                                "nordly_diagnosis_agent"
                        )))
                .andExpect(jsonPath("$.incident_lab.delivery")
                        .value("synchronous_post_run"))
                .andExpect(jsonPath("$.incident_lab.streaming").value(false))
                .andExpect(jsonPath("$.incident_lab.alarm_required").value(true))
                .andExpect(jsonPath("$.incident_lab.answer_states").value(contains(
                        "diagnosed",
                        "insufficient_evidence",
                        "withheld",
                        "not_started"
                )))
                .andExpect(jsonPath("$.incident_lab.explicit_confirmation_required")
                        .value(true))
                .andExpect(jsonPath("$.incident_lab.synthetic_only").value(true))
                .andExpect(jsonPath("$.incident_lab.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.incident_lab.action_executed")
                        .value(false))
                .andExpect(jsonPath("$.incident_lab.human_approval_required")
                        .value(true))
                .andExpect(jsonPath("$.incident_lab.registered_tool.function_name")
                        .value("inspect_incident_evidence"))
                .andExpect(jsonPath("$.incident_lab.registered_tool.read_only")
                        .value(true))
                .andExpect(jsonPath("$.incident_lab.registered_tool.case_bound")
                        .value(true))
                .andExpect(jsonPath("$.incident_lab.registered_tool.read_operations")
                        .value(contains(
                                "get_metrics",
                                "search_logs",
                                "get_trace",
                                "retrieve_runbooks"
                        )))
                .andExpect(jsonPath(
                        "$.incident_lab.registered_tool.diagnostic_probe_reference"
                ).value("diagnostic_probe"))
                .andExpect(jsonPath(
                        "$.incident_lab.registered_tool.allowed_diagnostic_probe_ids"
                ).value(contains(
                        "service_health",
                        "dependency_status",
                        "release_metadata",
                        "config_fingerprint_diff"
                )))
                .andExpect(jsonPath("$.incident_lab.supported_locales")
                        .value(contains("sv", "en")))
                .andExpect(jsonPath("$.incident_lab.incident_families[*].id")
                        .value(contains(
                                "payment_timeout",
                                "catalog_cache_invalidation",
                                "order_event_backlog",
                                "order_idempotency_failure"
                        )))
                .andExpect(jsonPath("$.incident_lab.incident_families[0].label.sv")
                        .value("Betalningar svarar för sent"))
                .andExpect(jsonPath("$.incident_lab.incident_families[0].label.en")
                        .value("Payments time out"))
                .andExpect(jsonPath("$.incident_lab.incident_families[0].service")
                        .value("payment_adapter"))
                .andExpect(jsonPath("$.incident_lab.incident_families[0].alarm_signal")
                        .value("http_5xx_response_count"))
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
                ).value(240))
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
