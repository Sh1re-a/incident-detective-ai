package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("rag")
public final class NordlyKnowledgeIndexReadiness {

    private final NordlyKnowledgeCorpus corpus;
    private final RunbookVectorStore store;
    private final RagProperties properties;

    public NordlyKnowledgeIndexReadiness(
            NordlyKnowledgeCorpus corpus,
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
                corpus.eligibleChunkCount()
        );
    }
}
