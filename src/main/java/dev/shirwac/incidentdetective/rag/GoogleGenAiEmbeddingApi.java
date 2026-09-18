package dev.shirwac.incidentdetective.rag;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rag")
public final class GoogleGenAiEmbeddingApi implements GeminiEmbeddingApi {

    private static final int TIMEOUT_MS = 10_000;

    private final GeminiAiProperties aiProperties;
    private final GoogleGenAiClientFactory clientFactory;
    private volatile Client client;

    public GoogleGenAiEmbeddingApi(
            GeminiAiProperties aiProperties,
            RagProperties ragProperties,
            GoogleGenAiClientFactory clientFactory
    ) {
        if (aiProperties.provider() != ragProperties.providerTransport()) {
            throw new IllegalStateException(
                    "AI provider and embedding profile transport must match"
            );
        }
        this.aiProperties = aiProperties;
        this.clientFactory = clientFactory;
    }

    @Override
    public EmbedContentResponse embed(
            String model,
            String input,
            EmbedContentConfig config
    ) {
        try {
            return client().models.embedContent(model, input, config);
        } catch (RunbookEmbeddingException exception) {
            throw exception;
        } catch (GenAiIOException | ApiException exception) {
            throw new RunbookEmbeddingException(
                    RunbookEmbeddingFailure.UPSTREAM,
                    "Gemini embedding request failed",
                    exception
            );
        } catch (RuntimeException exception) {
            throw new RunbookEmbeddingException(
                    RunbookEmbeddingFailure.UPSTREAM,
                    "Gemini embedding request failed",
                    exception
            );
        }
    }

    private synchronized Client client() {
        if (client == null) {
            if (!aiProperties.hasProviderConfiguration()) {
                throw new RunbookEmbeddingException(
                        RunbookEmbeddingFailure.CONFIGURATION,
                        "Google Gen AI provider is required for pgvector RAG"
                );
            }
            client = clientFactory.create(HttpOptions.builder()
                            .timeout(TIMEOUT_MS)
                            .retryOptions(HttpRetryOptions.builder()
                                    .attempts(1)
                                    .build())
                            .build());
        }
        return client;
    }

    @PreDestroy
    void closeClient() {
        Client current = client;
        if (current != null) {
            current.close();
        }
    }
}
