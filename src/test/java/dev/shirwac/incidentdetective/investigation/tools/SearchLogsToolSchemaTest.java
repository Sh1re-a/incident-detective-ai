package dev.shirwac.incidentdetective.investigation.tools;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchLogsToolSchemaTest {

    @Test
    void definesAStrictBoundedSnakeCaseFunctionSchema() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/tool-schemas/search_logs-v1.json"
        );

        JsonNode schema;
        try (InputStream input = resource.getInputStream()) {
            schema = JsonMapper.builder().build().readTree(input);
        }

        assertEquals("object", schema.get("type").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertTrue(schema.get("properties").has("services"));
        assertTrue(schema.get("properties").has("levels"));
        assertTrue(schema.get("properties").has("query"));
        assertFalse(schema.get("properties").has("scenario_id"));
        assertFalse(schema.get("properties").has("scenarioId"));
        assertFalse(schema.get("properties").get("services").has("uniqueItems"));
        assertFalse(schema.get("properties").get("levels").has("uniqueItems"));
        assertEquals(8, schema.get("properties").get("services")
                .get("maxItems").asInt());
        assertEquals(8, schema.get("properties").get("levels")
                .get("maxItems").asInt());
        assertEquals(160, schema.get("properties").get("query")
                .get("maxLength").asInt());
        assertTrue(schema.get("properties").get("query").has("pattern"));
        assertTrue(schema.get("properties").get("query")
                .get("description").asText().contains("attribute"));

        Set<String> required = new HashSet<>();
        schema.get("required").forEach(node -> required.add(node.asText()));
        assertEquals(
                Set.of("services", "levels", "query", "start", "end"),
                required
        );
    }
}
