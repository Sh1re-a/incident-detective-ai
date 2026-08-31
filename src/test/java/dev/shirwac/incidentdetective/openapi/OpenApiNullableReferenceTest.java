package dev.shirwac.incidentdetective.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiNullableReferenceTest {

    @Autowired
    private MockMvc mockMvc;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void representsNullableObjectReferencesAsObjectOrNullUnions()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(nullableReference(
                        "RetrievalCapability",
                        "active_embedding_profile",
                        "EmbeddingCapability"
                ))
                .andExpect(nullableReference(
                        "LiveInvestigationResult",
                        "token_usage",
                        "ModelTokenUsage"
                ))
                .andExpect(nullableReference(
                        "LiveInvestigationResult",
                        "model_cost_breakdown",
                        "ModelCostBreakdown"
                ))
                .andExpect(nullableReference(
                        "ModelCallMetadata",
                        "token_usage",
                        "ModelTokenUsage"
                ));
    }

    @Test
    void marksConditionalNullsAsRequiredAndNullable() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode schemas = jsonMapper.readTree(
                result.getResponse().getContentAsString()
        ).at("/components/schemas");

        String[][] nullableFields = {
                {"ModelCallMetadata", "provider_response_id"},
                {"ModelTokenUsage", "input_tokens"},
                {"ModelTokenUsage", "cached_input_tokens"},
                {"ModelTokenUsage", "uncached_input_tokens"},
                {"ModelTokenUsage", "candidate_output_tokens"},
                {"ModelTokenUsage", "thinking_output_tokens"},
                {"ModelTokenUsage", "output_tokens"},
                {"ModelTokenUsage", "tool_use_prompt_tokens"},
                {"ModelTokenUsage", "total_tokens"},
                {"LiveToolEvent", "runbook_retrieval"},
                {"RunbookRetrievalMetadata", "corpus_version"},
                {"RunbookRetrievalMetadata", "embedding_profile"},
                {"RunbookRetrievalMetadata", "query_embedding"},
                {"QueryEmbeddingUsage", "provider_billable_characters"},
                {"QueryEmbeddingUsage", "provider_input_tokens"},
                {"Match", "cosine_similarity"},
                {"Match", "content_sha256"},
                {"Diagnosis", "root_cause_code"},
                {"Diagnosis", "affected_service"},
                {"EvidencePrecision", "score"},
                {"ClaimCoverage", "score"},
                {"ReplayComparison", "expected_root_cause_code"},
                {"ReplayComparison", "expected_affected_service"},
                {"PromptCacheTelemetry", "cached_input_tokens"},
                {"ModelCostBreakdown", "observed_cache_savings_usd"},
                {"ProviderUsage", "estimated_list_price_cost_usd"}
        };
        for (String[] field : nullableFields) {
            assertNullable(schemas, field[0], field[1]);
        }
    }

    @Test
    void marksEverySerializedObjectPropertyAsRequired() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode schemas = jsonMapper.readTree(
                result.getResponse().getContentAsString()
        ).at("/components/schemas");

        for (String schemaName : schemas.propertyNames()) {
            JsonNode schema = schemas.get(schemaName);
            JsonNode properties = schema.get("properties");
            if (properties == null || properties.isEmpty()) {
                continue;
            }
            Set<String> propertyNames = new HashSet<>(
                    properties.propertyNames()
            );
            Set<String> required = new HashSet<>();
            schema.get("required").forEach(
                    node -> required.add(node.asText())
            );
            assertEquals(
                    propertyNames,
                    required,
                    schemaName + " must distinguish required fields from null values"
            );
        }
    }

    private static void assertNullable(
            JsonNode schemas,
            String ownerSchema,
            String propertyName
    ) {
        JsonNode property = schemas.get(ownerSchema)
                .get("properties")
                .get(propertyName);
        boolean nullableType = property.has("type")
                && property.get("type").isArray()
                && containsText(property.get("type"), "null");
        boolean nullableUnion = property.has("oneOf")
                && property.get("oneOf").isArray()
                && property.get("oneOf").values().stream().anyMatch(
                node -> node.has("type")
                        && "null".equals(node.get("type").asText())
        );
        assertTrue(
                nullableType || nullableUnion,
                ownerSchema + "." + propertyName + " must allow null"
        );
        assertTrue(
                containsText(schemas.get(ownerSchema).get("required"), propertyName),
                ownerSchema + "." + propertyName + " must always be present"
        );
    }

    private static boolean containsText(JsonNode values, String expected) {
        return values.values().stream().anyMatch(
                value -> expected.equals(value.asText())
        );
    }

    private static org.springframework.test.web.servlet.ResultMatcher
    nullableReference(
            String ownerSchema,
            String property,
            String referencedSchema
    ) {
        String path = "$.components.schemas." + ownerSchema
                + ".properties." + property;
        return result -> {
            jsonPath(path + ".oneOf[0]['$ref']")
                    .value("#/components/schemas/" + referencedSchema)
                    .match(result);
            jsonPath(path + ".oneOf[1].type")
                    .value("null")
                    .match(result);
            jsonPath(path + ".type").doesNotExist().match(result);
            jsonPath(path + "['$ref']").doesNotExist().match(result);
        };
    }
}
