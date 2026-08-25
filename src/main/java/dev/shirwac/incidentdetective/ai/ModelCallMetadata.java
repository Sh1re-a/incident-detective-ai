package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import java.util.Objects;

public record ModelCallMetadata(
        ModelPhase phase,
        int round,
        String providerResponseId,
        String modelVersion,
        ModelTokenUsage tokenUsage,
        long latencyMs
) {
    public ModelCallMetadata {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        Objects.requireNonNull(tokenUsage, "tokenUsage must not be null");
        if (round < 1) {
            throw new IllegalArgumentException("round must be positive");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
    }
}
