package dev.shirwac.incidentdetective.openapi;

import com.zaxxer.hikari.HikariDataSource;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("rag")
class IncidentLabOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentLabService service;

    @MockitoBean(name = "ragDataSource")
    private HikariDataSource ragDataSource;

    @MockitoBean(name = "ragFlyway")
    private Flyway ragFlyway;

    @MockitoBean(name = "ragJdbcClient")
    private JdbcClient ragJdbcClient;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void documentsOptionalGenerationControlsAndNullableAlarmLookback()
            throws Exception {
        JsonNode document = jsonMapper.readTree(
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        assertEquals(
                "#/components/schemas/IncidentLabRunRequest",
                document.at(
                        "/paths/~1api~1v1~1incident-lab~1runs/post/"
                                + "requestBody/content/application~1json/schema/$ref"
                ).asText()
        );
        assertEquals(
                "#/components/schemas/IncidentLabRunResponse",
                document.at(
                        "/paths/~1api~1v1~1incident-lab~1runs/post/"
                                + "responses/200/content/application~1json/schema/$ref"
                ).asText()
        );

        JsonNode schemas = document.at("/components/schemas");
        JsonNode request = schemas.get("IncidentLabRunRequest");
        assertEquals(
                Set.of("plan", "confirm_live_ai"),
                textValues(request.get("required"))
        );

        JsonNode seed = request.get("properties").get("seed");
        JsonNode evidenceMode = request.get("properties").get("evidence_mode");
        assertTrue(allowsNull(seed), "seed must allow explicit null");
        assertTrue(
                allowsNull(evidenceMode),
                "evidence_mode must allow explicit null"
        );
        assertFalse(
                textValues(request.get("required")).contains("seed"),
                "seed must be omittable"
        );
        assertFalse(
                textValues(request.get("required")).contains("evidence_mode"),
                "evidence_mode must be omittable"
        );
        assertTrue(
                evidenceMode.get("description").asText().contains(
                        "literal value 'auto' is not accepted"
                )
        );
        assertEquals(
                Set.of("diagnostic", "insufficient_evidence"),
                textValues(evidenceMode.get("enum"))
        );

        JsonNode observation = schemas.get("SignalObservation");
        assertTrue(
                textValues(observation.get("required"))
                        .contains("lookback_seconds"),
                "lookback_seconds must remain present in alarm receipts"
        );
        assertTrue(
                allowsNull(observation.get("properties").get("lookback_seconds")),
                "lookback_seconds must allow null when a rule has no time window"
        );

        JsonNode runResponse = schemas.get("IncidentLabRunResponse");
        assertTrue(
                textValues(runResponse.get("required"))
                        .contains("localized_presentations")
        );
        assertEquals(
                "#/components/schemas/LocalizedPresentations",
                runResponse.get("properties")
                        .get("localized_presentations")
                        .get("$ref")
                        .asText()
        );
        assertTrue(
                runResponse.get("properties")
                        .get("localized_presentations")
                        .get("description")
                        .asText()
                        .contains("never model translations")
        );

        JsonNode localized = schemas.get("LocalizedPresentations");
        assertEquals(Set.of("sv", "en"), textValues(localized.get("required")));
        assertEquals(
                "#/components/schemas/LocalizedPresentation",
                localized.get("properties").get("sv").get("$ref").asText()
        );
        assertEquals(
                "#/components/schemas/LocalizedPresentation",
                localized.get("properties").get("en").get("$ref").asText()
        );
        assertEquals(
                Set.of(
                        "business_response",
                        "developer_response",
                        "action_receipt"
                ),
                textValues(schemas.get("LocalizedPresentation").get("required"))
        );
    }

    @Test
    void documentsConditionalPlannerFieldsAsPresentAndNullable()
            throws Exception {
        JsonNode schemas = jsonMapper.readTree(
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        ).at("/components/schemas");

        assertRequiredNullable(
                schemas,
                "IncidentPlanProposal",
                "incident_family"
        );
        assertRequiredNullable(
                schemas,
                "IncidentPlanProposal",
                "requested_severity"
        );
        assertRequiredNullable(
                schemas,
                "IncidentPlanValidationResult",
                "plan"
        );
        assertRequiredNullable(
                schemas,
                "IncidentPlanValidationResult",
                "rejection"
        );
        assertRequiredNullable(
                schemas,
                "IncidentPlannerReceipt",
                "provider_response_id"
        );
        assertRequiredNullable(
                schemas,
                "IncidentPlannerReceipt",
                "token_usage"
        );
    }

    private static Set<String> textValues(JsonNode values) {
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static boolean allowsNull(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type != null && type.isArray()
                && textValues(type).contains("null")) {
            return true;
        }
        JsonNode oneOf = schema.get("oneOf");
        return oneOf != null && oneOf.isArray()
                && oneOf.values().stream().anyMatch(candidate ->
                "null".equals(candidate.path("type").asText())
        );
    }

    private static void assertRequiredNullable(
            JsonNode schemas,
            String ownerSchema,
            String propertyName
    ) {
        JsonNode owner = schemas.get(ownerSchema);
        assertTrue(
                textValues(owner.get("required")).contains(propertyName),
                ownerSchema + "." + propertyName + " must always be present"
        );
        assertTrue(
                allowsNull(owner.get("properties").get(propertyName)),
                ownerSchema + "." + propertyName + " must allow null"
        );
        assertTrue(
                allowsNonNull(owner.get("properties").get(propertyName)),
                ownerSchema + "." + propertyName
                        + " must retain its non-null schema"
        );
    }

    private static boolean allowsNonNull(JsonNode schema) {
        JsonNode type = schema.get("type");
        if (type != null) {
            if (!type.isArray()) {
                return !"null".equals(type.asText());
            }
            return type.values().stream().anyMatch(candidate ->
                    !"null".equals(candidate.asText())
            );
        }
        JsonNode oneOf = schema.get("oneOf");
        if (oneOf != null && oneOf.isArray()) {
            return oneOf.values().stream().anyMatch(candidate ->
                    !"null".equals(candidate.path("type").asText())
            );
        }
        return schema.has("$ref");
    }
}
