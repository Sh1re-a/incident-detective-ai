package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoogleGenAiProviderRouteTest {

    @Test
    void developerApiRouteDisclosesNoUnusedLocationOrSecret() {
        GoogleGenAiProviderRoute route = GoogleGenAiProviderRoute.from(
                properties(
                        GoogleGenAiProvider.DEVELOPER_API,
                        "not-public",
                        "unused-location"
                )
        );

        assertEquals("developer_api", route.transport());
        assertEquals("api_key", route.authenticationMode());
        assertNull(route.location());
    }

    @Test
    void vertexRouteDisclosesOnlySafeTransportAuthenticationAndLocation()
            throws Exception {
        GoogleGenAiProviderRoute route = GoogleGenAiProviderRoute.from(
                properties(
                        GoogleGenAiProvider.VERTEX_AI,
                        "secret-project-id",
                        "europe-west1"
                )
        );

        JsonNode serialized = JsonMapper.builder().build().valueToTree(route);
        assertEquals("vertex_ai", route.transport());
        assertEquals("adc", route.authenticationMode());
        assertEquals("europe-west1", route.location());
        assertFalse(serialized.has("vertexProject"));
        assertFalse(serialized.has("project"));
        assertFalse(serialized.has("credentials"));
        assertFalse(serialized.toString().contains("secret-project-id"));
    }

    private GeminiAiProperties properties(
            GoogleGenAiProvider provider,
            String project,
            String location
    ) {
        return new GeminiAiProperties(
                provider == GoogleGenAiProvider.DEVELOPER_API
                        ? "test-only-key"
                        : null,
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                provider,
                project,
                location
        );
    }
}
