package dev.shirwac.incidentdetective.nordly;

import java.util.List;

record KnowledgeCorpusManifest(
        String manifestVersion,
        String truthLabel,
        String corpusVersion,
        CorpusEmbeddingProfile embeddingProfile,
        List<KnowledgeDocument> documents
) {
    static final String MANIFEST_VERSION = "nordly-knowledge-manifest-v2";

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
            String displayFilename,
            String documentType,
            String classification,
            String title,
            String titleSv,
            String summarySv,
            String summaryEn,
            String ownerTeam,
            String lifecycle,
            String effectiveFrom,
            String effectiveUntil,
            List<String> accessScopes,
            List<String> relatedDocumentIds,
            List<KnowledgeChunk> chunks
    ) {
        KnowledgeDocument {
            accessScopes = accessScopes == null
                    ? null
                    : List.copyOf(accessScopes);
            relatedDocumentIds = relatedDocumentIds == null
                    ? null
                    : List.copyOf(relatedDocumentIds);
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
