package dev.shirwac.incidentdetective.rag;

public record RunbookSearchHit(
        RunbookCorpusEntry entry,
        double cosineSimilarity
) {
    public RunbookSearchHit {
        if (entry == null) {
            throw new IllegalArgumentException("runbook entry is required");
        }
        if (!Double.isFinite(cosineSimilarity)
                || cosineSimilarity < -1
                || cosineSimilarity > 1) {
            throw new IllegalArgumentException("cosine similarity must be finite and between -1 and 1");
        }
    }
}
