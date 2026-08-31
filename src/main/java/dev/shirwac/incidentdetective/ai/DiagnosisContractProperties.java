package dev.shirwac.incidentdetective.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident-detective.ai.diagnosis")
public record DiagnosisContractProperties(String schemaResource) {

    public DiagnosisContractProperties {
        if (schemaResource == null
                || !schemaResource.startsWith("ai/")
                || schemaResource.contains("..")
                || !schemaResource.matches("[a-zA-Z0-9/_-]+\\.json")) {
            throw new IllegalArgumentException(
                    "diagnosis schema resource must be a safe classpath JSON resource"
            );
        }
    }
}
