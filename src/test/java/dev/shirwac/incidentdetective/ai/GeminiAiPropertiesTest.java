package dev.shirwac.incidentdetective.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiAiPropertiesTest {

    @Test
    void keyPresenceDoesNotChangeTheExplicitLiveFlag() {
        GeminiAiProperties disabled = new GeminiAiProperties(
                "test-key",
                false,
                "gemini-test",
                GeminiThinkingLevel.LOW,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );
        GeminiAiProperties enabledWithoutKey = new GeminiAiProperties(
                " ",
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );

        assertTrue(disabled.hasApiKey());
        assertFalse(disabled.liveEnabled());
        assertTrue(disabled.thinkingLevel() == GeminiThinkingLevel.LOW);
        assertFalse(enabledWithoutKey.hasApiKey());
        assertTrue(enabledWithoutKey.liveEnabled());
    }

    @Test
    void rejectsAPromptLabelThatDoesNotMatchTheRuntimeContract() {
        assertThrows(IllegalArgumentException.class, () ->
                new GeminiAiProperties(
                        "test-key",
                        true,
                        "gemini-test",
                        GeminiThinkingLevel.MINIMAL,
                        "overridden-prompt-label"
                )
        );
    }
}
