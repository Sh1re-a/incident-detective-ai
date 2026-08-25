package dev.shirwac.incidentdetective.replay;

public record ModelTokenUsage(
        int inputTokens,
        int outputTokens,
        int totalTokens
) {
}
