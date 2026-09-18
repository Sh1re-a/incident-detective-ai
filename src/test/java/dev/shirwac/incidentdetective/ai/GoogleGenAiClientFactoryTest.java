package dev.shirwac.incidentdetective.ai;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleGenAiClientFactoryTest {

    @Test
    void createsADeveloperApiClientWithoutLoadingAdc() {
        GeminiAiProperties properties = properties(
                GoogleGenAiProvider.DEVELOPER_API,
                "developer-test-key",
                null,
                null
        );
        AtomicInteger adcLoads = new AtomicInteger();
        GoogleGenAiClientFactory factory = new GoogleGenAiClientFactory(
                properties,
                () -> {
                    adcLoads.incrementAndGet();
                    return testCredentials();
                }
        );

        try (Client client = factory.create(httpOptions())) {
            assertFalse(client.vertexAI());
            assertEquals("developer-test-key", client.apiKey());
            assertEquals(0, adcLoads.get());
        }
    }

    @Test
    void createsAVertexClientWithProjectLocationAndAdc() {
        GeminiAiProperties properties = properties(
                GoogleGenAiProvider.VERTEX_AI,
                null,
                "vertex-test-project",
                "eu"
        );
        AtomicInteger adcLoads = new AtomicInteger();
        GoogleGenAiClientFactory factory = new GoogleGenAiClientFactory(
                properties,
                () -> {
                    adcLoads.incrementAndGet();
                    return testCredentials();
                }
        );

        try (Client client = factory.create(httpOptions())) {
            assertTrue(client.vertexAI());
            assertEquals("vertex-test-project", client.project());
            assertEquals("eu", client.location());
            assertNull(client.apiKey());
            assertEquals(1, adcLoads.get());
        }
    }

    @Test
    void rejectsIncompleteProviderConfigurationBeforeClientCreation() {
        GeminiAiProperties properties = properties(
                GoogleGenAiProvider.VERTEX_AI,
                null,
                " ",
                "global"
        );
        GoogleGenAiClientFactory factory = new GoogleGenAiClientFactory(
                properties,
                () -> {
                    throw new AssertionError("ADC must not be loaded");
                }
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> factory.create(httpOptions())
        );

        assertEquals(
                "Google Gen AI provider configuration is incomplete",
                exception.getMessage()
        );
    }

    @Test
    void reportsAdcFailureWithoutCopyingCredentialDetails() {
        GeminiAiProperties properties = properties(
                GoogleGenAiProvider.VERTEX_AI,
                null,
                "vertex-test-project",
                "global"
        );
        GoogleGenAiClientFactory factory = new GoogleGenAiClientFactory(
                properties,
                () -> {
                    throw new IOException("private credential path");
                }
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> factory.create(httpOptions())
        );

        assertEquals(
                "Application Default Credentials are unavailable",
                exception.getMessage()
        );
        assertFalse(exception.getMessage().contains("private credential path"));
    }

    private GeminiAiProperties properties(
            GoogleGenAiProvider provider,
            String apiKey,
            String project,
            String location
    ) {
        return new GeminiAiProperties(
                apiKey,
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                provider,
                project,
                location
        );
    }

    private HttpOptions httpOptions() {
        return HttpOptions.builder().timeout(1_000).build();
    }

    private GoogleCredentials testCredentials() {
        return GoogleCredentials.create(new AccessToken(
                "test-access-token",
                new Date(System.currentTimeMillis() + 60_000)
        ));
    }
}
