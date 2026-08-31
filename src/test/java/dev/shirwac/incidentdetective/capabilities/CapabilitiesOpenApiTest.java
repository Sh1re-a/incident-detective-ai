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
                        "modes",
                        "tools",
                        "live_ai",
                        "generated_cases",
                        "retrieval",
                        "prompt_cache"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.CapabilitiesResponse"
                                + ".properties.contract_version.enum"
                ).value(contains("capabilities-v2")))
                .andExpect(jsonPath(
                        "$.components.schemas.ToolCapability.properties.name.enum"
                ).value(contains(
                        "get_metrics",
                        "search_logs",
                        "get_trace",
                        "retrieve_runbooks"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.RetrievalCapability"
                                + ".properties.backend.enum"
                ).value(contains(
                        "deterministic_fixture",
                        "pgvector_exact_cosine"
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
                        "$.components.schemas.LiveAiCapability"
                                + ".properties.credentials_configured"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveAiCapability"
                                + ".properties.request_configured.description"
                ).value(org.hamcrest.Matchers.containsString(
                        "does not claim provider reachability"
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
        assertFalse(openApi.contains("databasePassword"));
        assertFalse(openApi.contains("database_password"));
    }
}
