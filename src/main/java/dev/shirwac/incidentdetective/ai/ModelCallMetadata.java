package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

public record ModelCallMetadata(
        ModelPhase phase,
        int round,
        String providerResponseId,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Provider-reported model version; null when omitted."
        )
        String modelVersion,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                nullable = true,
                description = "Null when the provider omits usage metadata."
        )
        ModelTokenUsage tokenUsage,
        long latencyMs
) {
    public ModelCallMetadata {
        Objects.requireNonNull(phase, "phase must not be null");
        if (modelVersion != null && modelVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "modelVersion must be null or non-blank"
            );
        }
        if (round < 1) {
            throw new IllegalArgumentException("round must be positive");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
    }
}
