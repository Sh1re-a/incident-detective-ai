package dev.shirwac.incidentdetective.nordly;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "incident-detective.rag.knowledge")
public record NordlyKnowledgeRagProperties(
        int topK,
        double minimumSimilarity
) {
    public NordlyKnowledgeRagProperties {
        if (topK < 1 || topK > 5) {
            throw new IllegalArgumentException(
                    "Nordly knowledge topK must be between 1 and 5"
            );
        }
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < -1
                || minimumSimilarity > 1) {
            throw new IllegalArgumentException(
                    "Nordly knowledge minimum similarity must be between -1 and 1"
            );
        }
    }
}
