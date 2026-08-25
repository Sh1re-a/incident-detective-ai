package dev.shirwac.incidentdetective.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "GEMINI_API_KEY=test-only-placeholder")
class LocalSecretsConfigurationTest {

    @Value("${incident-detective.ai.gemini-api-key}")
    private String configuredKey;

    @Test
    void mapsTheLocalKeyWithoutMakingANetworkCall() {
        assertEquals("test-only-placeholder", configuredKey);
    }
}
