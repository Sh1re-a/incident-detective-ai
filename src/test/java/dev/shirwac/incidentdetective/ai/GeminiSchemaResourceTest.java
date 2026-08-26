package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimValueTaxonomy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.HashSet;
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
                "ai/diagnosis-schema-v2.json",
                "ai/diagnosis-schema-v3.json",
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
                "ai/prompts/collect-gemini-live-v2.txt",
                "ai/prompts/synthesize-gemini-live-v2.txt",
                "ai/prompts/collect-gemini-live-v3.txt",
                "ai/prompts/synthesize-gemini-live-v3.txt",
                "ai/prompts/collect-gemini-live-v4.txt",
                "ai/prompts/synthesize-gemini-live-v4.txt",
                "ai/prompts/collect-gemini-live-v5.txt",
                "ai/prompts/synthesize-gemini-live-v5.txt",
                "ai/prompts/collect-gemini-live-v6.txt",
                "ai/prompts/synthesize-gemini-live-v6.txt"
        )) {
            String prompt = new ClassPathResource(resourcePath)
                    .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(prompt.contains("ground_truth"), resourcePath);
            assertFalse(prompt.contains("allowed_evidence_ids"), resourcePath);
        }
    }

    @Test
    void collectionPromptV4KeepsBroadLogsAndRunbooksBounded() throws Exception {
        String prompt = new ClassPathResource(
                "ai/prompts/collect-gemini-live-v4.txt"
        ).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(prompt.contains("use an empty levels array"));
        assertTrue(prompt.contains("Retrieve runbooks at most once"));
        assertTrue(prompt.contains("already_collected_evidence contains a runbook"));
    }

    @Test
    void collectionPromptV5PrioritizesDirectIncidentCoverage() throws Exception {
        String prompt = new ClassPathResource(
                "ai/prompts/collect-gemini-live-v5.txt"
        ).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(prompt.contains("search separately for the change event"));
        assertTrue(prompt.contains("two distinct search_logs calls plus get_metrics"));
        assertTrue(prompt.contains("Do not infer the trigger from scenario wording alone"));
        assertTrue(prompt.contains("Do not retrieve a runbook while a concrete trigger"));
    }

    @Test
    void collectionPromptV6TreatsTheRuntimeBudgetAsAuthoritative()
            throws Exception {
        String prompt = new ClassPathResource(
                "ai/prompts/collect-gemini-live-v6.txt"
        ).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(prompt.contains("remaining_tool_budget is authoritative"));
        assertTrue(prompt.contains("never exceed max_calls_this_round"));
        assertTrue(prompt.contains("discovered_trace_ids"));
        assertTrue(prompt.contains("remaining count is zero"));
        assertTrue(prompt.contains("all relevant metric names together"));
    }

    @Test
    void synthesisPromptV5DefinesDirectSupportByClaimType() throws Exception {
        String prompt = new ClassPathResource(
                "ai/prompts/synthesize-gemini-live-v5.txt"
        ).getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(prompt.contains("root_cause: cite a log or trace"));
        assertTrue(prompt.contains("A metric label alone is insufficient"));
        assertTrue(prompt.contains("explicitly records the release"));
        assertTrue(prompt.contains("scored independently"));
        assertTrue(prompt.contains("may be reused across claims"));
        assertTrue(prompt.contains("Audit every claim_value_code and evidence_id pair"));
        assertTrue(prompt.contains("return insufficient_evidence instead of guessing"));
        assertFalse(prompt.contains("cic-v1"));
        assertFalse(prompt.contains("INVENTORY_SCHEMA_MISMATCH"));
    }

    @Test
    void diagnosisSchemaExposesTheSharedTaxonomyWithoutScenarioAnswers()
            throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/diagnosis-schema-v2.json"
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

    @Test
    void diagnosisSchemaV3UsesTheSharedClaimValueTaxonomy() throws Exception {
        JsonNode schema;
        try (InputStream input = new ClassPathResource(
                "ai/diagnosis-schema-v3.json"
        ).getInputStream()) {
            schema = jsonMapper.readTree(input);
        }

        JsonNode values = schema.get("properties")
                .get("claims")
                .get("items")
                .get("properties")
                .get("claim_value_code")
                .get("enum");
        HashSet<String> schemaValues = new HashSet<>();
        values.forEach(value -> schemaValues.add(value.asText()));

        assertEquals(ClaimValueTaxonomy.allValues(), schemaValues);
        assertEquals(2, schema.get("properties")
                .get("claims")
                .get("items")
                .get("properties")
                .get("evidence_ids")
                .get("maxItems")
                .asInt());
    }
}
