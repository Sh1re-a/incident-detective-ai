package dev.shirwac.incidentdetective.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

@Validated
@Profile("rag")
@ConfigurationProperties(prefix = "incident-detective.rag.database")
public record RagDatabaseProperties(
        @NotBlank String url,
        @NotBlank String username,
        @NotBlank String password,
        @Min(1) @Max(10) int maximumPoolSize,
        @Min(250) @Max(30_000) long connectionTimeoutMs
) {
}
