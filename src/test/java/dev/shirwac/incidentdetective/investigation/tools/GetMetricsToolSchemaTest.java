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

class GetMetricsToolSchemaTest {

    @Test
    void definesAStrictSnakeCaseFunctionSchema() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/tool-schemas/get_metrics-v1.json"
        );

        JsonNode schema;
        try (InputStream input = resource.getInputStream()) {
            schema = JsonMapper.builder().build().readTree(input);
        }

        assertEquals("object", schema.get("type").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertTrue(schema.get("properties").has("metric_names"));
        assertFalse(schema.get("properties").has("metricNames"));
        assertFalse(schema.get("properties").get("metric_names")
                .has("uniqueItems"));
        assertTrue(schema.get("properties").get("metric_names")
                .get("description").asText().contains("per-scenario catalog"));

        Set<String> required = new HashSet<>();
        schema.get("required").forEach(node -> required.add(node.asText()));
        assertEquals(Set.of("metric_names", "start", "end"), required);
    }
}
