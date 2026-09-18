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

    @Test
    void vertexUsesProjectAndLocationInsteadOfAnApiKey() {
        GeminiAiProperties configured = new GeminiAiProperties(
                " ",
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.VERTEX_AI,
                " vertex-project ",
                " eu "
        );
        GeminiAiProperties incomplete = new GeminiAiProperties(
                null,
                true,
                "gemini-test",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.VERTEX_AI,
                "vertex-project",
                " "
        );

        assertFalse(configured.hasApiKey());
        assertTrue(configured.hasProviderConfiguration());
        assertTrue(configured.provider() == GoogleGenAiProvider.VERTEX_AI);
        assertTrue("vertex-project".equals(configured.vertexProject()));
        assertTrue("eu".equals(configured.vertexLocation()));
        assertFalse(incomplete.hasProviderConfiguration());
    }
}
