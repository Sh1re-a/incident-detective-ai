package dev.shirwac.incidentdetective.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final String SCENARIOS_PATH = "/api/v1/scenarios";
    private static final String SCENARIOS_GET =
            "$.paths['" + SCENARIOS_PATH + "'].get";
    private static final String REPLAY_PATH =
            "/api/v1/scenarios/{scenarioId}/runs/recorded-replay";
    private static final String REPLAY_POST =
            "$.paths['" + REPLAY_PATH + "'].post";
    private static final String LIVE_PATH =
            "/api/v1/scenarios/{scenarioId}/runs/live-ai";
    private static final String LIVE_POST =
            "$.paths['" + LIVE_PATH + "'].post";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsTheSafeScenarioCatalog() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(SCENARIOS_GET + ".summary")
                        .value("List synthetic incident scenarios"))
                .andExpect(jsonPath(
                        SCENARIOS_GET
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ScenarioCatalogResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.ScenarioCatalogResponse"
                                + ".properties.scenarios.items['$ref']"
                ).value("#/components/schemas/Scenario"));
    }

    @Test
    void documentsTheRecordedReplayContractWithoutGroundTruthSchemas()
            throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Incident Detective API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath(REPLAY_POST + ".summary")
                        .value("Run a recorded incident investigation"))
                .andExpect(jsonPath(
                        REPLAY_POST + ".parameters[0].name"
                ).value("scenarioId"))
                .andExpect(jsonPath(
                        REPLAY_POST + ".parameters[0].in"
                ).value("path"))
                .andExpect(jsonPath(
                        REPLAY_POST + ".parameters[0].required"
                ).value(true))
                .andExpect(jsonPath(
                        REPLAY_POST
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/RecordedReplayResult"))
                .andExpect(jsonPath(
                        REPLAY_POST
                                + ".responses['404'].content['application/problem+json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ApiProblemResponse"))
                .andExpect(jsonPath("$.components.schemas.RecordedReplayResult").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties.scenario_id"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties.tool_events"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties.model_id"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties.scenarioId"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties.mode.enum"
                ).value(contains("recorded_replay")))
                .andExpect(jsonPath(LIVE_POST + ".summary")
                        .value("Run a live Gemini incident investigation"))
                .andExpect(jsonPath(
                        LIVE_POST
                                + ".requestBody.content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/LiveInvestigationRequest"))
                .andExpect(jsonPath(
                        LIVE_POST
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/LiveInvestigationResult"))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationRequest"
                                + ".properties.confirm_live_ai"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationRequest.required"
                ).value(contains("confirm_live_ai")))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationResult.properties.mode.enum"
                ).value(contains("live_ai")))
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.required"
                ).value(containsInAnyOrder(
                        "run_id", "scenario_id", "mode", "truth_label", "status",
                        "started_at", "completed_at", "latency_ms", "scenario",
                        "tool_events", "diagnosis", "verification", "comparison",
                        "model_id", "prompt_version", "token_usage",
                        "estimated_cost_usd"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationResult.required.length()"
                ).value(22))
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties"
                                + ".token_usage.oneOf[0].type"
                ).value("null"))
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedReplayResult.properties"
                                + ".token_usage.oneOf[1]['$ref']"
                ).value("#/components/schemas/ModelTokenUsage"))
                .andExpect(jsonPath(
                        "$.components.schemas.ApiProblemResponse.properties.code"
                ).exists())
                .andExpect(jsonPath("$.paths.length()").value(3))
                .andExpect(jsonPath(LIVE_POST + ".responses['400']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['404']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['415']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['429']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['502']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['503']").exists())
                .andExpect(jsonPath(LIVE_POST + ".responses['504']").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationResult.properties.model_calls"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationResult.properties.token_usage"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.EvidencePrecision.properties.citation_support"
                                + ".items['$ref']"
                ).value("#/components/schemas/CitationSupportResult"))
                .andExpect(jsonPath(
                        "$.components.schemas.LiveInvestigationResult"
                                + ".properties.estimated_cost_basis"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.discriminator.propertyName"
                ).value("evidence_type"))
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.discriminator.mapping.metric"
                ).value("#/components/schemas/MetricEvidence"))
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.discriminator.mapping.log"
                ).value("#/components/schemas/LogEvidence"))
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.discriminator.mapping.trace"
                ).value("#/components/schemas/TraceEvidence"))
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.discriminator.mapping.runbook"
                ).value("#/components/schemas/RunbookEvidence"))
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedToolResult.properties.evidence"
                                + ".items['$ref']"
                ).value("#/components/schemas/Evidence"))
                .andExpect(jsonPath(
                        "$.components.schemas.Evidence.oneOf[*]['$ref']"
                ).value(containsInAnyOrder(
                        "#/components/schemas/MetricEvidence",
                        "#/components/schemas/LogEvidence",
                        "#/components/schemas/TraceEvidence",
                        "#/components/schemas/RunbookEvidence"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.MetricEvidence.type"
                ).value("object"))
                .andExpect(jsonPath(
                        "$.components.schemas.MetricEvidence.allOf"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.LogEvidence.allOf"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.TraceEvidence.allOf"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RunbookEvidence.allOf"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.MetricEvidence.properties.evidence_id"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MetricEvidence.properties.content"
                                + "['$ref']"
                ).value("#/components/schemas/MetricContent"))
                .andExpect(jsonPath("$.components.schemas.GroundTruth").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ClaimSupport").doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RecordedScenarioFixture"
                ).doesNotExist())
                .andReturn();

        String openApiJson = result.getResponse().getContentAsString();
        assertFalse(openApiJson.contains("GroundTruth"));
        assertFalse(openApiJson.contains("ClaimSupport"));
        assertFalse(openApiJson.contains("allowed_evidence_ids"));
        assertFalse(openApiJson.contains("relevant_runbooks"));
        assertFalse(openApiJson.contains("groundTruthResource"));
        assertFalse(openApiJson.contains("recordedResource"));
        assertFalse(openApiJson.contains("/fixtures/"));
        assertFalse(openApiJson.contains("geminiApiKey"));
        assertFalse(openApiJson.contains("gemini_api_key"));
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Swagger UI"
                )));
    }
}
