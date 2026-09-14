package dev.shirwac.incidentdetective.planning;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void clientInitializationFailureIsNotReportedAsMalformedModelOutput() {
        GeminiIncidentPlannerGateway gateway = gateway(properties(
                "test-only-key",
                true
        ));
        when(clientFactory.create(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("ADC unavailable"));

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
    }

    @Test
    void blankPlannerResponseIsClassifiedAsMalformed() {
        assertMalformed(GenerateContentResponse.builder().build());
    }

    @Test
    void truncatedPlannerResponseIsClassifiedAsMalformed() {
        assertMalformed(response(
                validCandidateJson(),
                FinishReason.Known.MAX_TOKENS
        ));
    }

    @ParameterizedTest
    @MethodSource("malformedPlannerBodies")
    void invalidPlannerJsonOrSchemaIsClassifiedAsMalformed(String body) {
        assertMalformed(response(body, FinishReason.Known.STOP));
    }

    private static Stream<String> malformedPlannerBodies() {
        return Stream.of(
                "{not-json",
                """
                        {
                          "status": "candidate",
                          "summary": "Synthetic incident.",
                          "incident_family": "unknown_family",
                          "requested_severity": "high",
                          "affected_services": ["payment_adapter"],
                          "requested_blast_radius": "single_service"
                        }
                        """
        );
    }

    private void assertMalformed(GenerateContentResponse response) {
        IncidentPlannerException exception = assertThrows(
                IncidentPlannerException.class,
                () -> gateway(properties("test-only-key", true))
                        .decodeProposal(response)
        );

        assertEquals(
                IncidentPlannerFailure.MALFORMED_RESPONSE,
                exception.failure()
        );
    }

    private GenerateContentResponse response(
            String text,
            FinishReason.Known finishReason
    ) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(text))
                                .build())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private String validCandidateJson() {
        return """
                {
                  "status": "candidate",
                  "summary": "Synthetic incident.",
                  "incident_family": "payment_timeout",
                  "requested_severity": "high",
                  "affected_services": ["payment_adapter"],
                  "requested_blast_radius": "single_service"
                }
                """;
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
