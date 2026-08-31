package dev.shirwac.incidentdetective.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident-detective.ai")
public record GeminiAiProperties(
        String geminiApiKey,
        boolean liveEnabled,
        String modelId,
        GeminiThinkingLevel thinkingLevel,
        String promptVersion
) {
    public GeminiAiProperties {
        if (!GeminiPromptContracts.LIVE_PROMPT_VERSION.equals(promptVersion)) {
            throw new IllegalArgumentException(
                    "promptVersion must match the deployed Gemini prompt contract"
            );
        }
    }

    public boolean hasApiKey() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
