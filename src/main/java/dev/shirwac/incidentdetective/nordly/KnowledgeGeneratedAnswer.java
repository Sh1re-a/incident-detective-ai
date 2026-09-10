package dev.shirwac.incidentdetective.nordly;

import java.util.List;

record KnowledgeGeneratedAnswer(
        String summarySv,
        String summaryEn,
        List<GeneratedClaim> claims
) {
    KnowledgeGeneratedAnswer {
        claims = claims == null ? null : List.copyOf(claims);
    }

    record GeneratedClaim(
            String textSv,
            String textEn,
            List<String> citationIds
    ) {
        GeneratedClaim {
            citationIds = citationIds == null
                    ? null
                    : List.copyOf(citationIds);
        }
    }
}
