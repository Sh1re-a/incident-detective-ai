package dev.shirwac.incidentdetective.nordly;

import java.util.List;

interface KnowledgeAnswerGateway {

    KnowledgeGenerationResult generate(
            String question,
            List<KnowledgeRagResponse.RankedMatch> context
    );
}
