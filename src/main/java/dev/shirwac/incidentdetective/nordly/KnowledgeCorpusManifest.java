package dev.shirwac.incidentdetective.nordly;

import java.util.List;

record KnowledgeCorpusManifest(
        String manifestVersion,
        String truthLabel,
        String corpusVersion,
        CorpusEmbeddingProfile embeddingProfile,
        List<KnowledgeDocument> documents
) {
    static final String MANIFEST_VERSION = "nordly-knowledge-manifest-v1";

    KnowledgeCorpusManifest {
        documents = documents == null ? null : List.copyOf(documents);
    }

    record CorpusEmbeddingProfile(
            String provider,
            String modelId,
            int dimensions
    ) {
    }

    record KnowledgeDocument(
            String id,
            String version,
            String title,
            String ownerTeam,
            String lifecycle,
            String effectiveFrom,
            String effectiveUntil,
            List<String> accessScopes,
            List<KnowledgeChunk> chunks
    ) {
        KnowledgeDocument {
            accessScopes = accessScopes == null
                    ? null
                    : List.copyOf(accessScopes);
            chunks = chunks == null ? null : List.copyOf(chunks);
        }
    }

    record KnowledgeChunk(
            String id,
            String sectionHeading,
            String sourceRef,
            String evidenceId,
            String displaySummarySv,
            String displaySummaryEn,
            String text
    ) {
    }
}
