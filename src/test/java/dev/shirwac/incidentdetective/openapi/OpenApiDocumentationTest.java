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

    private static final String REPLAY_PATH =
            "/api/v1/scenarios/{scenarioId}/runs/recorded-replay";
    private static final String REPLAY_POST =
            "$.paths['" + REPLAY_PATH + "'].post";

    @Autowired
    private MockMvc mockMvc;

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
                ).value("#/components/schemas/ProblemDetail"))
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
