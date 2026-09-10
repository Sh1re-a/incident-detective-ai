package dev.shirwac.incidentdetective.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "incident-detective.ai")
public record GeminiAiProperties(
        String geminiApiKey,
        boolean liveEnabled,
        String modelId,
        GeminiThinkingLevel thinkingLevel,
        String promptVersion,
        GoogleGenAiProvider provider,
        String vertexProject,
        String vertexLocation
) {
    @ConstructorBinding
    public GeminiAiProperties {
        provider = provider == null
                ? GoogleGenAiProvider.DEVELOPER_API
                : provider;
        vertexProject = normalized(vertexProject);
        vertexLocation = normalized(vertexLocation);
        if (!GeminiPromptContracts.LIVE_PROMPT_VERSION.equals(promptVersion)) {
            throw new IllegalArgumentException(
                    "promptVersion must match the deployed Gemini prompt contract"
            );
        }
    }

    public GeminiAiProperties(
            String geminiApiKey,
            boolean liveEnabled,
            String modelId,
            GeminiThinkingLevel thinkingLevel,
            String promptVersion
    ) {
        this(
                geminiApiKey,
                liveEnabled,
                modelId,
                thinkingLevel,
                promptVersion,
                GoogleGenAiProvider.DEVELOPER_API,
                null,
                null
        );
    }

    public boolean hasApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public boolean hasProviderConfiguration() {
        return switch (provider) {
            case DEVELOPER_API -> hasApiKey();
            case VERTEX_AI -> vertexProject != null && vertexLocation != null;
        };
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
