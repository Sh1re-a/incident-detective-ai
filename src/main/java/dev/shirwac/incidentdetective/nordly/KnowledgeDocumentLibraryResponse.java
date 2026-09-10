package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        description = "Read-only view of every synthetic Nordly knowledge document. "
                + "Eligibility describes which documents may enter public RAG context."
)
public record KnowledgeDocumentLibraryResponse(
        String contractVersion,
        String mode,
        String truthLabel,
        String corpusVersion,
        boolean syntheticOnly,
        boolean currentVectorSearch,
        int documentCount,
        int chunkCount,
        int eligibleDocumentCount,
        int eligibleChunkCount,
        EligibilityRule eligibilityRule,
        EmbeddingProfile embeddingProfile,
        List<KnowledgeDocument> documents
) {
    public static final String CONTRACT_VERSION =
            "nordly-knowledge-document-library-v1";
    public static final String MODE = "read_only_corpus";

    public KnowledgeDocumentLibraryResponse {
        documents = List.copyOf(documents);
    }

    static KnowledgeDocumentLibraryResponse from(
            KnowledgeCorpusManifest manifest
    ) {
        List<KnowledgeDocument> documents = manifest.documents().stream()
                .map(KnowledgeDocumentLibraryResponse::document)
                .toList();
        int eligibleDocumentCount = (int) documents.stream()
                .filter(item -> item.ragEligibility().eligible())
                .count();
        int eligibleChunkCount = documents.stream()
                .filter(item -> item.ragEligibility().eligible())
                .mapToInt(item -> item.chunks().size())
                .sum();
        int chunkCount = documents.stream()
                .mapToInt(item -> item.chunks().size())
                .sum();

        return new KnowledgeDocumentLibraryResponse(
                CONTRACT_VERSION,
                MODE,
                manifest.truthLabel(),
                manifest.corpusVersion(),
                true,
                false,
                documents.size(),
                chunkCount,
                eligibleDocumentCount,
                eligibleChunkCount,
                new EligibilityRule(
                        NordlyKnowledgeCorpus.REQUIRED_LIFECYCLE,
                        NordlyKnowledgeCorpus.REQUIRED_ACCESS_SCOPE
                ),
                new EmbeddingProfile(
                        manifest.embeddingProfile().provider(),
                        manifest.embeddingProfile().modelId(),
                        manifest.embeddingProfile().dimensions()
                ),
                documents
        );
    }

    private static KnowledgeDocument document(
            KnowledgeCorpusManifest.KnowledgeDocument document
    ) {
        return new KnowledgeDocument(
                document.id(),
                document.version(),
                document.title(),
                document.ownerTeam(),
                document.lifecycle(),
                document.effectiveFrom(),
                document.effectiveUntil(),
                document.accessScopes(),
                eligibility(document),
                document.chunks().stream()
                        .map(chunk -> new KnowledgeChunk(
                                chunk.id(),
                                chunk.sectionHeading(),
                                chunk.sourceRef(),
                                chunk.evidenceId(),
                                chunk.displaySummarySv(),
                                chunk.displaySummaryEn(),
                                chunk.text()
                        ))
                        .toList()
        );
    }

    private static RagEligibility eligibility(
            KnowledgeCorpusManifest.KnowledgeDocument document
    ) {
        if (!NordlyKnowledgeCorpus.REQUIRED_LIFECYCLE.equals(
                document.lifecycle()
        )) {
            return new RagEligibility(
                    false,
                    "LIFECYCLE_NOT_APPROVED",
                    "Dokumentet visas i biblioteket men får inte användas av RAG "
                            + "eftersom livscykeln är " + document.lifecycle() + ".",
                    "The document is visible in the library but cannot be used by "
                            + "RAG because its lifecycle is " + document.lifecycle() + "."
            );
        }
        if (!document.accessScopes().contains(
                NordlyKnowledgeCorpus.REQUIRED_ACCESS_SCOPE
        )) {
            return new RagEligibility(
                    false,
                    "PUBLIC_DEMO_SCOPE_MISSING",
                    "Dokumentet saknar åtkomsten public_demo och får därför inte "
                            + "användas av publik RAG.",
                    "The document lacks the public_demo access scope and therefore "
                            + "cannot be used by public RAG."
            );
        }
        return new RagEligibility(
                true,
                "APPROVED_PUBLIC_DEMO",
                "Dokumentet är APPROVED och har public_demo-åtkomst. Det får delta "
                        + "i semantisk rankning.",
                "The document is APPROVED and has public_demo access. It may enter "
                        + "semantic ranking."
        );
    }

    public record EligibilityRule(
            String requiredLifecycle,
            String requiredAccessScope
    ) {
    }

    public record EmbeddingProfile(
            String provider,
            String modelId,
            int dimensions
    ) {
    }

    public record KnowledgeDocument(
            String id,
            String version,
            String title,
            String ownerTeam,
            String lifecycle,
            String effectiveFrom,
            @Schema(nullable = true)
            String effectiveUntil,
            List<String> accessScopes,
            RagEligibility ragEligibility,
            List<KnowledgeChunk> chunks
    ) {
        public KnowledgeDocument {
            accessScopes = List.copyOf(accessScopes);
            chunks = List.copyOf(chunks);
        }
    }

    public record RagEligibility(
            boolean eligible,
            String reasonCode,
            String summarySv,
            String summaryEn
    ) {
    }

    public record KnowledgeChunk(
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
