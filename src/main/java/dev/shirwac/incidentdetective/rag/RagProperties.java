package dev.shirwac.incidentdetective.rag;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "incident-detective.rag")
public record RagProperties(
        @NotBlank String embeddingModel,
        int embeddingDimensions,
        @NotBlank String embeddingFormatVersion
) {
    public RagProperties {
        if (embeddingDimensions != 768) {
            throw new IllegalArgumentException(
                    "this sprint's pgvector schema requires 768 dimensions"
            );
        }
    }
}
