package dev.shirwac.incidentdetective.rag;

import java.util.List;

public record EmbeddingResult(
        List<Float> values,
        int billableCharacters,
        double inputTokens,
        long latencyMs
) {
    public EmbeddingResult {
        values = values == null ? null : List.copyOf(values);
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("embedding values are required");
        }
        if (values.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite");
        }
        if (billableCharacters < 0 || inputTokens < 0 || latencyMs < 0) {
            throw new IllegalArgumentException("embedding usage and latency cannot be negative");
        }
    }
}
