package dev.shirwac.incidentdetective.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "incident-detective.rag")
public record RagProperties(
        @NotBlank String mode,
        @NotBlank String embeddingModel,
        @Min(1) @Max(2_000) int embeddingDimensions,
        @NotBlank String embeddingFormatVersion
) {
}
