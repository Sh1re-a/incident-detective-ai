package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class GeminiIncidentPlannerGatewayTest {

    private final GoogleGenAiClientFactory clientFactory = mock(
            GoogleGenAiClientFactory.class
    );
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void disabledPlanningDoesNotCreateAProviderClient() {
        GeminiIncidentPlannerGateway gateway = gateway(properties(
                "test-only-key",
                false
        ));

        IncidentPlannerException exception = assertThrows(
                IncidentPlannerException.class,
                () -> gateway.propose(new IncidentPlanningRequest(
                        "Simulera timeout i Nordlys betalningsflöde."
                ))
        );

        assertEquals(IncidentPlannerFailure.DISABLED, exception.failure());
        verifyNoInteractions(clientFactory);
    }

    @Test
    void missingProviderConfigurationDoesNotCreateAClient() {
        GeminiIncidentPlannerGateway gateway = gateway(properties(null, true));

        IncidentPlannerException exception = assertThrows(
                IncidentPlannerException.class,
                () -> gateway.propose(new IncidentPlanningRequest(
                        "Simulera timeout i Nordlys betalningsflöde."
                ))
        );

        assertEquals(
                IncidentPlannerFailure.NOT_CONFIGURED,
                exception.failure()
        );
        verifyNoInteractions(clientFactory);
    }

    private GeminiIncidentPlannerGateway gateway(
            GeminiAiProperties properties
    ) {
        return new GeminiIncidentPlannerGateway(
                properties,
                clientFactory,
                jsonMapper
        );
    }

    private GeminiAiProperties properties(
            String apiKey,
            boolean enabled
    ) {
        return new GeminiAiProperties(
                apiKey,
                enabled,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.DEVELOPER_API,
                null,
                null
        );
    }
}
