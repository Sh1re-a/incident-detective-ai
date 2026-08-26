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
        int billableCharacters = 0;
        double inputTokens = 0;
        long embeddingLatencyMs = 0;

        for (RunbookCorpusEntry entry : corpus.entries()) {
            if (store.containsCurrent(corpus.version(), entry, properties)) {
                skipped++;
                items.add(new RunbookImportItem(
                        entry.evidenceId(),
                        entry.contentSha256(),
                        RunbookImportStatus.SKIPPED_UNCHANGED,
                        0,
                        0,
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
            billableCharacters += embedding.billableCharacters();
            inputTokens += embedding.inputTokens();
            embeddingLatencyMs += embedding.latencyMs();
            items.add(new RunbookImportItem(
                    entry.evidenceId(),
                    entry.contentSha256(),
                    RunbookImportStatus.IMPORTED,
                    embedding.billableCharacters(),
                    embedding.inputTokens(),
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
                billableCharacters,
                inputTokens,
                embeddingLatencyMs,
                clock.instant(),
                items
        );
    }
}
