package dev.shirwac.incidentdetective.rag;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class GoogleGenAiEmbeddingApiTest {

    @Test
    void rejectsAProviderTransportMismatchBeforeAnyEmbeddingCanBeStored() {
        GeminiAiProperties ai = new GeminiAiProperties(
                "test-key",
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.DEVELOPER_API,
                null,
                null
        );
        RagProperties rag = new RagProperties(
                "gemini-embedding-2",
                768,
                "search-result-v1",
                0.0,
                GoogleGenAiProvider.VERTEX_AI
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new GoogleGenAiEmbeddingApi(
                        ai,
                        rag,
                        mock(GoogleGenAiClientFactory.class)
                )
        );

        assertEquals(
                "AI provider and embedding profile transport must match",
                exception.getMessage()
        );
    }
}
