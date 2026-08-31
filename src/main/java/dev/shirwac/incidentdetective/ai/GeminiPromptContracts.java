package dev.shirwac.incidentdetective.ai;

/** Versioned classpath contracts used by the live Gemini gateway. */
public final class GeminiPromptContracts {

    public static final String LIVE_PROMPT_VERSION = "gemini-live-v6";
    public static final String COLLECTION_PROMPT_VERSION =
            "collect-gemini-live-v6";
    public static final String COLLECTION_PROMPT_RESOURCE =
            "ai/prompts/collect-gemini-live-v6.txt";
    public static final String SYNTHESIS_PROMPT_VERSION =
            "synthesize-gemini-live-v6";
    public static final String SYNTHESIS_PROMPT_RESOURCE =
            "ai/prompts/synthesize-gemini-live-v6.txt";
    private GeminiPromptContracts() {
    }
}
