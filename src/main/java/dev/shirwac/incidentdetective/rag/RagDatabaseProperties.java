package dev.shirwac.incidentdetective.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import java.util.Locale;

@Validated
@Profile("rag")
@ConfigurationProperties(prefix = "incident-detective.rag.database")
public record RagDatabaseProperties(
        @NotBlank String url,
        @NotBlank String username,
        @NotBlank String password,
        @Min(1) @Max(10) int maximumPoolSize,
        @Min(250) @Max(30_000) long connectionTimeoutMs,
        String cloudSqlInstance,
        String cloudSqlIpType
) {
    public RagDatabaseProperties {
        cloudSqlInstance = normalized(cloudSqlInstance);
        cloudSqlIpType = normalizedIpType(cloudSqlIpType);
    }

    boolean usesCloudSqlConnector() {
        return cloudSqlInstance != null;
    }

    private static String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizedIpType(String value) {
        if (value == null || value.isBlank()) {
            return "PRIVATE";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"PRIVATE".equals(normalized) && !"PUBLIC".equals(normalized)) {
            throw new IllegalArgumentException(
                    "cloudSqlIpType must be PRIVATE or PUBLIC"
            );
        }
        return normalized;
    }
}
