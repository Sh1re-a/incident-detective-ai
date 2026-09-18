package dev.shirwac.incidentdetective.capabilities;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=openapi-capabilities-secret"
})
@AutoConfigureMockMvc
class CapabilitiesOpenApiTest {

    private static final String CAPABILITIES_GET =
            "$.paths['/api/v1/capabilities'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsTheVersionedTypedContractWithoutCredentialSchemas()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(CAPABILITIES_GET + ".summary")
                        .value("Describe backend capabilities"))
                .andExpect(jsonPath(
                        CAPABILITIES_GET
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/CapabilitiesResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.CapabilitiesResponse.required"
                ).value(containsInAnyOrder(
                        "contract_version",
                        "synthetic_only",
                        "remediation_enabled",
                        "provider",
                        "deployment",
                        "knowledge_corpus",
                        "modes",
                        "tools",
                        "diagnostic_probe",
                        "incident_lab",
                        "live_ai",
                        "generated_cases",
                        "retrieval",
                        "prompt_cache"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.CapabilitiesResponse"
                                + ".properties.contract_version.enum"
                ).value(contains("capabilities-v5")))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderCapability.required"
                ).value(containsInAnyOrder(
                        "transport",
                        "authentication_mode",
                        "location",
                        "routing_configuration_complete",
                        "credential_status"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderCapability"
                                + ".properties.transport.enum"
                ).value(contains("developer_api", "vertex_ai")))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderCapability"
                                + ".properties.authentication_mode.enum"
                ).value(contains("api_key", "adc")))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderCapability"
                                + ".properties.credential_status.enum"
                ).value(contains("configured", "missing", "not_checked")))
                .andExpect(jsonPath(
                        "$.components.schemas.DeploymentCapability.required"
                ).value(containsInAnyOrder(
                        "platform",
                        "revision",
                        "build_git_sha"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.DeploymentCapability"
                                + ".properties.platform.enum"
                ).value(contains("local", "cloud_run")))
                .andExpect(jsonPath(
                        "$.components.schemas.KnowledgeCorpusCapability.required"
                ).value(containsInAnyOrder(
                        "manifest_version",
                        "corpus_version",
                        "corpus_content_sha256",
                        "eligible_document_count",
                        "eligible_chunk_count"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.ToolCapability.properties.name.enum"
                ).value(contains(
                        "get_metrics",
                        "search_logs",
                        "get_trace",
                        "retrieve_runbooks"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.DiagnosticProbeCapability.required"
                ).value(containsInAnyOrder(
                        "function_name",
                        "allowed_probe_ids",
                        "case_bound",
                        "read_only",
                        "action_executed"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.DiagnosticProbeCapability"
                                + ".properties.function_name.enum"
                ).value(contains("run_diagnostic_probe")))
                .andExpect(jsonPath(
                        "$.components.schemas.DiagnosticProbeCapability"
                                + ".properties.allowed_probe_ids.items.enum"
                ).value(contains(
                        "service_health",
                        "dependency_status",
                        "release_metadata",
                        "config_fingerprint_diff"
                        )))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentLabCapability.required"
                ).value(containsInAnyOrder(
                        "enabled",
                        "availability_reason",
                        "required_profiles",
                        "plan_contract_version",
                        "run_contract_version",
                        "orchestration",
                        "expected_agent_order",
                        "delivery",
                        "streaming",
                        "alarm_required",
                        "answer_states",
                        "explicit_confirmation_required",
                        "synthetic_only",
                        "write_tools_available",
                        "action_executed",
                        "human_approval_required",
                        "registered_tool",
                        "supported_locales",
                        "incident_families"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentLabCapability"
                                + ".properties.orchestration.enum"
                ).value(contains("sequential_agent")))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentLabCapability"
                                + ".properties.delivery.enum"
                ).value(contains("synchronous_post_run")))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentLabToolCapability"
                                + ".properties.function_name.enum"
                ).value(contains("inspect_incident_evidence")))
                .andExpect(jsonPath(
                        "$.components.schemas.IncidentFamilyCapability.required"
                ).value(containsInAnyOrder(
                        "id",
                        "label",
                        "description",
                        "customer_impact",
                        "service",
                        "alarm_signal",
                        "alarm_signal_concept"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.LocalizedCopy.required"
                ).value(containsInAnyOrder("sv", "en")))
                .andExpect(jsonPath(
                        "$.components.schemas.RetrievalCapability"
                                + ".properties.backend.enum"
                ).value(contains(
                        "deterministic_fixture",
                        "pgvector_exact_cosine"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.RetrievalCapability.required"
                ).value(containsInAnyOrder(
                        "backend",
                        "active_profiles",
                        "mode_description",
                        "limitation",
                        "vector_database_backend_active",
                        "active_embedding_profile",
                        "index_status"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.VectorIndexCapability.required"
                ).value(containsInAnyOrder(
                        "ready",
                        "corpus_version",
                        "indexed_chunks",
                        "current_chunks",
                        "expected_chunks"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.EmbeddingCapability.required"
                ).value(containsInAnyOrder(
                        "provider_transport",
                        "model_id",
                        "dimensions",
                        "format_version",
                        "minimum_similarity"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.PromptCacheCapability"
                                + ".properties.strategy.enum"
                ).value(contains("provider_implicit")))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveBudgetCapability"
                                + ".properties.hard_deadline_ms"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveBudgetCapability"
                                + ".properties.daily_live_run_limit"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveBudgetCapability"
                                + ".properties.daily_quota_scope.enum"
                ).value(contains("process_local", "database_global")))
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratedCasesCapability"
                                + ".properties.truth_label"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratedCasesCapability"
                                + ".properties.incident_families"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveAiCapability"
                                + ".properties.request_routing_configured.description"
                ).value(org.hamcrest.Matchers.containsString(
                        "does not claim successful authentication"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveAiCapability"
                                + ".properties.available"
                ).doesNotExist())
                .andReturn();

        String openApi = result.getResponse().getContentAsString();
        assertFalse(openApi.contains("openapi-capabilities-secret"));
        assertFalse(openApi.contains("geminiApiKey"));
        assertFalse(openApi.contains("gemini_api_key"));
        assertFalse(openApi.contains("vertexProject"));
        assertFalse(openApi.contains("vertex_project"));
        assertFalse(openApi.contains("databasePassword"));
        assertFalse(openApi.contains("database_password"));
    }
}
