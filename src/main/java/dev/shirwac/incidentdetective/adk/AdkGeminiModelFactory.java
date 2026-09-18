package dev.shirwac.incidentdetective.adk;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import org.springframework.stereotype.Component;

/** Builds one explicitly bounded Gemini client for a request-scoped ADK runner. */
@Component
public final class AdkGeminiModelFactory {

    static final int PROVIDER_TIMEOUT_MS = 28_000;

    private final GeminiAiProperties properties;
    private final GoogleGenAiClientFactory clientFactory;

    public AdkGeminiModelFactory(
            GeminiAiProperties properties,
            GoogleGenAiClientFactory clientFactory
    ) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    public ModelLease create() {
        Client client = clientFactory.create(HttpOptions.builder()
                        .timeout(PROVIDER_TIMEOUT_MS)
                        .retryOptions(HttpRetryOptions.builder()
                                .attempts(1)
                                .build())
                        .build());
        BaseLlm model = Gemini.builder()
                .modelName(properties.modelId())
                .apiClient(client)
                .build();
        return new ModelLease(model, client);
    }

    public static final class ModelLease implements AutoCloseable {

        private final BaseLlm model;
        private final Client client;

        ModelLease(BaseLlm model, Client client) {
            this.model = model;
            this.client = client;
        }

        public BaseLlm model() {
            return model;
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
