package dev.shirwac.incidentdetective.rag;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "incident-detective.rag")
public record RagProperties(
        @NotBlank String embeddingModel,
        int embeddingDimensions,
        @NotBlank String embeddingFormatVersion,
        double minimumSimilarity
) {
    public RagProperties {
        if (embeddingDimensions != 768) {
            throw new IllegalArgumentException(
                    "the current pgvector schema requires 768 dimensions"
            );
        }
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < -1
                || minimumSimilarity > 1) {
            throw new IllegalArgumentException(
                    "minimum similarity must be finite and between -1 and 1"
            );
        }
    }
}
