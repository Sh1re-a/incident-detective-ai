package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiAiPropertiesTest {

    @Test
    void keyPresenceDoesNotChangeTheExplicitLiveFlag() {
        GeminiAiProperties disabled = new GeminiAiProperties(
                "test-key",
                false,
                "gemini-test",
                GeminiThinkingLevel.LOW,
                "prompt-v1"
        );
        GeminiAiProperties enabledWithoutKey = new GeminiAiProperties(
                " ",
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                "prompt-v1"
        );

        assertTrue(disabled.hasApiKey());
        assertFalse(disabled.liveEnabled());
        assertTrue(disabled.thinkingLevel() == GeminiThinkingLevel.LOW);
        assertFalse(enabledWithoutKey.hasApiKey());
        assertTrue(enabledWithoutKey.liveEnabled());
    }
}
