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

class GetTraceToolSchemaTest {

    @Test
    void definesAStrictExactTraceIdSchema() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/tool-schemas/get_trace-v1.json"
        );

        JsonNode schema;
        try (InputStream input = resource.getInputStream()) {
            schema = JsonMapper.builder().build().readTree(input);
        }

        assertEquals("object", schema.get("type").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals(Set.of("trace_id"), propertyNames(schema));
        assertFalse(schema.get("properties").has("traceId"));
        assertEquals(
                "^[a-z0-9][a-z0-9_-]{1,127}$",
                schema.get("properties").get("trace_id").get("pattern").asText()
        );
        assertTrue(schema.get("properties").get("trace_id")
                .get("description").asText().contains("Exact trace ID"));
        assertTrue(schema.get("properties").get("trace_id")
                .get("description").asText().contains("current scenario"));

        Set<String> required = new HashSet<>();
        schema.get("required").forEach(node -> required.add(node.asText()));
        assertEquals(Set.of("trace_id"), required);
    }

    private Set<String> propertyNames(JsonNode schema) {
        Set<String> properties = new HashSet<>();
        properties.addAll(schema.get("properties").propertyNames());
        return properties;
    }
}
