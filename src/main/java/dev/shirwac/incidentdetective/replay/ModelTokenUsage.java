package dev.shirwac.incidentdetective.replay;

public record ModelTokenUsage(
        Integer inputTokens,
        Integer cachedInputTokens,
        Integer uncachedInputTokens,
        Integer candidateOutputTokens,
        Integer thinkingOutputTokens,
        Integer outputTokens,
        Integer toolUsePromptTokens,
        Integer totalTokens
) {
    public ModelTokenUsage(int inputTokens, int outputTokens, int totalTokens) {
        this(
                inputTokens,
                null,
                null,
                null,
                null,
                outputTokens,
                null,
                totalTokens
        );
    }

    public ModelTokenUsage {
        requireNonNegative(inputTokens, "inputTokens");
        requireNonNegative(cachedInputTokens, "cachedInputTokens");
        requireNonNegative(uncachedInputTokens, "uncachedInputTokens");
        requireNonNegative(candidateOutputTokens, "candidateOutputTokens");
        requireNonNegative(thinkingOutputTokens, "thinkingOutputTokens");
        requireNonNegative(outputTokens, "outputTokens");
        requireNonNegative(toolUsePromptTokens, "toolUsePromptTokens");
        requireNonNegative(totalTokens, "totalTokens");
        if (inputTokens != null
                && cachedInputTokens != null
                && cachedInputTokens > inputTokens) {
            throw new IllegalArgumentException(
                    "cachedInputTokens must not exceed inputTokens"
            );
        }
        if (inputTokens != null
                && cachedInputTokens != null
                && uncachedInputTokens != null
                && uncachedInputTokens != inputTokens - cachedInputTokens) {
            throw new IllegalArgumentException(
                    "uncachedInputTokens must equal inputTokens minus cachedInputTokens"
            );
        }
    }

    private static void requireNonNegative(Integer value, String name) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
