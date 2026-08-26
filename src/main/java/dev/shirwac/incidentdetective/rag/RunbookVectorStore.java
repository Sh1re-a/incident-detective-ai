package dev.shirwac.incidentdetective.rag;

import java.util.List;

public interface RunbookVectorStore {

    boolean containsCurrent(
            String corpusVersion,
            RunbookCorpusEntry entry,
            RagProperties profile
    );

    void upsert(
            String corpusVersion,
            RunbookCorpusEntry entry,
            RagProperties profile,
            EmbeddingResult embedding
    );

    List<RunbookSearchHit> search(
            String corpusVersion,
            RagProperties profile,
            List<Float> queryEmbedding,
            int topK,
            double minimumSimilarity
    );

    List<String> documentIds(
            String corpusVersion,
            RagProperties profile
    );

    long count(
            String corpusVersion,
            RagProperties profile
    );
}
