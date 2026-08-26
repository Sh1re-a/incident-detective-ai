package dev.shirwac.incidentdetective.rag;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("rag")
public final class RunbookIndexReadiness {

    private final ClasspathRunbookCorpus corpus;
    private final RunbookVectorStore store;
    private final RagProperties properties;

    public RunbookIndexReadiness(
            ClasspathRunbookCorpus corpus,
            RunbookVectorStore store,
            RagProperties properties
    ) {
        this.corpus = corpus;
        this.store = store;
        this.properties = properties;
    }

    public RunbookIndexStatus inspect() {
        long indexedChunks = store.count(corpus.version(), properties);
        long currentChunks = corpus.entries().stream()
                .filter(entry -> store.containsCurrent(
                        corpus.version(),
                        entry,
                        properties
                ))
                .count();
        return new RunbookIndexStatus(
                indexedChunks,
                currentChunks,
                corpus.entries().size()
        );
    }

    public RunbookIndexStatus requireReady() {
        RunbookIndexStatus status = inspect();
        if (!status.ready()) {
            throw new RunbookIndexNotReadyException(status);
        }
        return status;
    }
}
