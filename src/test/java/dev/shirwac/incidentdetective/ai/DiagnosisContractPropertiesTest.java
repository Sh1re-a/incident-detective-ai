package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosisContractPropertiesTest {

    @Test
    void acceptsOnlySafeClasspathSchemaResources() {
        assertDoesNotThrow(() -> new DiagnosisContractProperties(
                "ai/diagnosis-schema-v4.json"
        ));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContractProperties(
                        "../../local-secrets.properties"
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisContractProperties(
                        "https://example.invalid/schema.json"
                ));
    }

    @Test
    void defaultRuntimeUsesTheCurrentV4DiagnosisSchema() throws Exception {
        Properties properties = new Properties();
        try (var input = new ClassPathResource("application.properties")
                .getInputStream()) {
            properties.load(input);
        }

        assertEquals(
                "ai/diagnosis-schema-v4.json",
                properties.getProperty(
                        "incident-detective.ai.diagnosis.schema-resource"
                )
        );
    }
}
