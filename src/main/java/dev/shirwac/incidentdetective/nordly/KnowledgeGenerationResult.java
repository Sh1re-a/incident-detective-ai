package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

record KnowledgeGenerationResult(
        KnowledgeGeneratedAnswer answer,
        String providerResponseId,
        String modelVersion,
        ModelTokenUsage tokenUsage,
        long latencyMs
) {
}
