package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentPlannerSchemaResourceTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void schemaIsStrictAndEnumeratesEveryGeneratedFamily() throws Exception {
        JsonNode schema;
        try (InputStream input = new ClassPathResource(
                GeminiIncidentPlannerGateway.SCHEMA_RESOURCE
        ).getInputStream()) {
            schema = jsonMapper.readTree(input);
        }

        assertEquals("object", schema.get("type").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals(6, schema.get("required").size());

        Set<String> schemaFamilies = new HashSet<>();
        schema.get("properties")
                .get("incident_family")
                .get("enum")
                .forEach(value -> {
                    if (!value.isNull()) {
                        schemaFamilies.add(value.asText());
                    }
                });
        Set<String> domainFamilies = new HashSet<>();
        for (GeneratedIncidentFamily family
                : GeneratedIncidentFamily.values()) {
            domainFamilies.add(family.wireValue());
        }
        assertEquals(domainFamilies, schemaFamilies);
    }

    @Test
    void schemaServiceEnumMatchesTheJavaAllowlist() throws Exception {
        JsonNode schema;
        try (InputStream input = new ClassPathResource(
                GeminiIncidentPlannerGateway.SCHEMA_RESOURCE
        ).getInputStream()) {
            schema = jsonMapper.readTree(input);
        }

        Set<String> schemaServices = new HashSet<>();
        schema.get("properties")
                .get("affected_services")
                .get("items")
                .get("enum")
                .forEach(value -> schemaServices.add(value.asText()));
        Set<String> domainServices = new HashSet<>();
        for (IncidentService service : IncidentService.values()) {
            domainServices.add(service.wireValue());
        }

        assertEquals(domainServices, schemaServices);
        assertTrue(schema.get("properties")
                .get("affected_services")
                .get("uniqueItems")
                .asBoolean());
    }

    @Test
    void promptMakesProposalAndExecutionBoundariesExplicit() throws Exception {
        String prompt = new ClassPathResource(
                GeminiIncidentPlannerGateway.PROMPT_RESOURCE
        ).getContentAsString(StandardCharsets.UTF_8);

        assertTrue(prompt.contains("You propose only"));
        assertTrue(prompt.contains("never execute"));
        assertTrue(prompt.contains("Java validator"));
        assertTrue(prompt.contains("låt hela systemet krascha"));
        assertTrue(prompt.contains("payment_timeout, high"));
        assertTrue(prompt.contains("not a real total outage"));
        assertTrue(prompt.contains("Do not apply this"));
        assertTrue(prompt.contains("Return only the schema-defined JSON"));
        assertFalse(prompt.contains("ground_truth"));
    }
}
