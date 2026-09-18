package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public final class NordlyKnowledgeCorpus {

    public static final String REQUIRED_LIFECYCLE = "APPROVED";
    public static final String REQUIRED_ACCESS_SCOPE = "public_demo";

    private final String manifestVersion;
    private final String version;
    private final String corpusContentSha256;
    private final List<RunbookCorpusEntry> entries;
    private final Map<String, EntryMetadata> metadataByEvidenceId;
    private final int eligibleDocumentCount;

    public NordlyKnowledgeCorpus(NordlyResourceCatalog catalog) {
        KnowledgeCorpusManifest manifest = catalog.knowledgeManifest();
        manifestVersion = manifest.manifestVersion();
        version = manifest.corpusVersion();

        Map<String, EntryMetadata> metadata = new LinkedHashMap<>();
        entries = manifest.documents().stream()
                .filter(this::eligible)
                .flatMap(document -> document.chunks().stream()
                        .map(chunk -> entry(document, chunk)))
                .toList();
        corpusContentSha256 = CorpusFingerprint.sha256(entries);
        for (KnowledgeCorpusManifest.KnowledgeDocument document
                : manifest.documents()) {
            if (!eligible(document)) {
                continue;
            }
            for (KnowledgeCorpusManifest.KnowledgeChunk chunk
                    : document.chunks()) {
                metadata.put(chunk.evidenceId(), new EntryMetadata(
                        REQUIRED_LIFECYCLE,
                        document.id(),
                        chunk.id(),
                        document.version(),
                        document.title(),
                        chunk.sectionHeading(),
                        document.ownerTeam(),
                        chunk.sourceRef(),
                        chunk.evidenceId(),
                        chunk.displaySummarySv(),
                        chunk.displaySummaryEn(),
                        chunk.text()
                ));
            }
        }
        metadataByEvidenceId = Map.copyOf(metadata);
        eligibleDocumentCount = (int) entries.stream()
                .map(RunbookCorpusEntry::documentId)
                .distinct()
                .count();
        if (entries.isEmpty() || entries.size() != metadataByEvidenceId.size()) {
            throw new IllegalStateException(
                    "Nordly approved public knowledge corpus is inconsistent"
            );
        }
    }

    public String version() {
        return version;
    }

    public String manifestVersion() {
        return manifestVersion;
    }

    public String corpusContentSha256() {
        return corpusContentSha256;
    }

    public List<RunbookCorpusEntry> entries() {
        return entries;
    }

    public int eligibleDocumentCount() {
        return eligibleDocumentCount;
    }

    public int eligibleChunkCount() {
        return entries.size();
    }

    public EntryMetadata metadata(String evidenceId) {
        EntryMetadata metadata = metadataByEvidenceId.get(evidenceId);
        if (metadata == null) {
            throw new IllegalStateException(
                    "Vector search returned evidence outside the eligible Nordly corpus"
            );
        }
        return metadata;
    }

    private boolean eligible(
            KnowledgeCorpusManifest.KnowledgeDocument document
    ) {
        return REQUIRED_LIFECYCLE.equals(document.lifecycle())
                && document.accessScopes().contains(REQUIRED_ACCESS_SCOPE);
    }

    private RunbookCorpusEntry entry(
            KnowledgeCorpusManifest.KnowledgeDocument document,
            KnowledgeCorpusManifest.KnowledgeChunk chunk
    ) {
        return new RunbookCorpusEntry(
                chunk.evidenceId(),
                document.id(),
                document.version(),
                chunk.id(),
                document.title(),
                chunk.displaySummarySv(),
                chunk.sourceRef(),
                chunk.text()
        );
    }

    public record EntryMetadata(
            String status,
            String documentId,
            String chunkId,
            String documentVersion,
            String title,
            String sectionHeading,
            String ownerTeam,
            String sourceRef,
            String evidenceId,
            String displaySummarySv,
            String displaySummaryEn,
            String text
    ) {
    }
}
