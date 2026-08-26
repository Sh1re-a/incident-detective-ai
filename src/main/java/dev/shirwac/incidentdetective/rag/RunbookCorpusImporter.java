package dev.shirwac.incidentdetective.rag;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("rag")
public final class RunbookCorpusImporter {

    private final ClasspathRunbookCorpus corpus;
    private final RunbookVectorStore store;
    private final EmbeddingGateway embeddings;
    private final RagProperties properties;
    private final Clock clock;

    public RunbookCorpusImporter(
            ClasspathRunbookCorpus corpus,
            RunbookVectorStore store,
            EmbeddingGateway embeddings,
            RagProperties properties,
            Clock clock
    ) {
        this.corpus = corpus;
        this.store = store;
        this.embeddings = embeddings;
        this.properties = properties;
        this.clock = clock;
    }

    public RunbookImportReport importMissingOrChanged() {
        List<RunbookImportItem> items = new ArrayList<>();
        int imported = 0;
        int skipped = 0;
        int inputCharacters = 0;
        int providerBillableCharacters = 0;
        double providerInputTokens = 0;
        boolean providerUsageMetadataComplete = true;
        long embeddingLatencyMs = 0;

        for (RunbookCorpusEntry entry : corpus.entries()) {
            if (store.containsCurrent(corpus.version(), entry, properties)) {
                skipped++;
                items.add(new RunbookImportItem(
                        entry.evidenceId(),
                        entry.contentSha256(),
                        RunbookImportStatus.SKIPPED_UNCHANGED,
                        0,
                        null,
                        null,
                        0
                ));
                continue;
            }

            EmbeddingResult embedding = embeddings.embedDocument(
                    entry.title(),
                    entry.text()
            );
            store.upsert(corpus.version(), entry, properties, embedding);
            imported++;
            inputCharacters += embedding.inputCharacters();
            if (embedding.providerBillableCharacters() == null
                    || embedding.providerInputTokens() == null) {
                providerUsageMetadataComplete = false;
            } else {
                providerBillableCharacters += embedding.providerBillableCharacters();
                providerInputTokens += embedding.providerInputTokens();
            }
            embeddingLatencyMs += embedding.latencyMs();
            items.add(new RunbookImportItem(
                    entry.evidenceId(),
                    entry.contentSha256(),
                    RunbookImportStatus.IMPORTED,
                    embedding.inputCharacters(),
                    embedding.providerBillableCharacters(),
                    embedding.providerInputTokens(),
                    embedding.latencyMs()
            ));
        }

        return new RunbookImportReport(
                corpus.version(),
                properties.embeddingModel(),
                properties.embeddingDimensions(),
                properties.embeddingFormatVersion(),
                corpus.entries().size(),
                imported,
                skipped,
                inputCharacters,
                imported > 0 && providerUsageMetadataComplete
                        ? providerBillableCharacters
                        : null,
                imported > 0 && providerUsageMetadataComplete
                        ? providerInputTokens
                        : null,
                imported == 0 || providerUsageMetadataComplete,
                embeddingLatencyMs,
                clock.instant(),
                items
        );
    }
}
