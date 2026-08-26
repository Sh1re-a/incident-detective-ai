package dev.shirwac.incidentdetective.rag;

import java.util.List;

public record EmbeddingResult(
        List<Float> values,
        int inputCharacters,
        Integer providerBillableCharacters,
        Double providerInputTokens,
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
        if (inputCharacters < 0 || latencyMs < 0) {
            throw new IllegalArgumentException("embedding input and latency cannot be negative");
        }
        if (providerBillableCharacters != null && providerBillableCharacters < 0) {
            throw new IllegalArgumentException("provider billable characters cannot be negative");
        }
        if (providerInputTokens != null
                && (!Double.isFinite(providerInputTokens) || providerInputTokens < 0)) {
            throw new IllegalArgumentException("provider input tokens must be finite and non-negative");
        }
    }
}
