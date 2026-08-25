package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiSchemaResourceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void diagnosisAndToolSchemasAreStrictObjects() throws Exception {
        List<String> resources = List.of(
                "ai/diagnosis-schema-v1.json",
                "ai/tool-schemas/get_metrics-v1.json",
                "ai/tool-schemas/search_logs-v1.json",
                "ai/tool-schemas/get_trace-v1.json",
                "ai/tool-schemas/retrieve_runbooks-v1.json"
        );

        for (String resourcePath : resources) {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream input = resource.getInputStream()) {
                JsonNode schema = jsonMapper.readTree(input);
                assertTrue(schema.isObject(), resourcePath);
                assertFalse(
                        schema.get("additionalProperties").asBoolean(),
                        resourcePath
                );
            }
        }
    }

    @Test
    void versionedPromptsDoNotMentionGroundTruth() throws Exception {
        for (String resourcePath : List.of(
                "ai/prompts/collect-gemini-live-v1.txt",
                "ai/prompts/synthesize-gemini-live-v1.txt"
        )) {
            String prompt = new ClassPathResource(resourcePath)
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(prompt.contains("ground_truth"), resourcePath);
            assertFalse(prompt.contains("allowed_evidence_ids"), resourcePath);
        }
    }

    @Test
    void diagnosisSchemaExposesTheSharedTaxonomyWithoutScenarioAnswers()
            throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/diagnosis-schema-v1.json"
        );
        JsonNode schema;
        try (InputStream input = resource.getInputStream()) {
            schema = jsonMapper.readTree(input);
        }

        JsonNode values = schema.get("properties")
                .get("root_cause_code")
                .get("enum");
        assertEquals(3, values.size());
        assertTrue(values.toString().contains("PAYMENT_TIMEOUT_CONFIG"));
        assertTrue(values.toString().contains("INVENTORY_SCHEMA_MISMATCH"));
        assertTrue(values.get(2).isNull());
        assertFalse(schema.toString().contains("scenario_id"));
    }
}
