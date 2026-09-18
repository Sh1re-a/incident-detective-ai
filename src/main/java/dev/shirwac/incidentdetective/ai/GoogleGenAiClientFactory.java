package dev.shirwac.incidentdetective.ai;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

/**
 * Creates Google Gen AI clients through one provider-aware authentication seam.
 * Callers retain ownership of the returned client and must close it.
 */
@Component
public final class GoogleGenAiClientFactory {

    private final GeminiAiProperties properties;
    private final ApplicationDefaultCredentials applicationDefaultCredentials;

    @Autowired
    public GoogleGenAiClientFactory(GeminiAiProperties properties) {
        this(properties, GoogleCredentials::getApplicationDefault);
    }

    GoogleGenAiClientFactory(
            GeminiAiProperties properties,
            ApplicationDefaultCredentials applicationDefaultCredentials
    ) {
        this.properties = Objects.requireNonNull(properties);
        this.applicationDefaultCredentials = Objects.requireNonNull(
                applicationDefaultCredentials
        );
    }

    public Client create(HttpOptions httpOptions) {
        Objects.requireNonNull(httpOptions, "httpOptions must not be null");
        if (!properties.hasProviderConfiguration()) {
            throw new IllegalStateException(
                    "Google Gen AI provider configuration is incomplete"
            );
        }

        Client.Builder builder = Client.builder().httpOptions(httpOptions);
        return switch (properties.provider()) {
            case DEVELOPER_API -> builder
                    .vertexAI(false)
                    .apiKey(properties.geminiApiKey())
                    .build();
            case VERTEX_AI -> builder
                    .vertexAI(true)
                    .project(properties.vertexProject())
                    .location(properties.vertexLocation())
                    .credentials(loadApplicationDefaultCredentials())
                    .build();
        };
    }

    private GoogleCredentials loadApplicationDefaultCredentials() {
        try {
            GoogleCredentials credentials = applicationDefaultCredentials.load();
            if (credentials == null) {
                throw new IOException("ADC returned no credentials");
            }
            return credentials;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Application Default Credentials are unavailable",
                    exception
            );
        }
    }

    @FunctionalInterface
    interface ApplicationDefaultCredentials {
        GoogleCredentials load() throws IOException;
    }
}
