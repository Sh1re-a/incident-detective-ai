package dev.shirwac.incidentdetective.investigation.tools;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieveRunbooksToolSchemaTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void exposesTheBoundedSnakeCaseContract() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "ai/tool-schemas/retrieve_runbooks-v1.json"
        );

        try (InputStream input = resource.getInputStream()) {
            JsonNode schema = jsonMapper.readTree(input);
            assertEquals("object", schema.get("type").asText());
            assertFalse(schema.get("additionalProperties").asBoolean());
            assertTrue(schema.get("properties").has("query"));
            assertEquals(
                    4,
                    schema.get("properties").get("max_results").get("maximum").asInt()
            );
        }
    }
}
