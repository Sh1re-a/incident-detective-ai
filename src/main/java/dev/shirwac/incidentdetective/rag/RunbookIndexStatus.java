package dev.shirwac.incidentdetective.rag;

public record RunbookIndexStatus(
        long indexedChunks,
        long currentChunks,
        int expectedChunks
) {
    public RunbookIndexStatus {
        if (indexedChunks < 0 || currentChunks < 0 || expectedChunks < 0) {
            throw new IllegalArgumentException("runbook index counts cannot be negative");
        }
        if (currentChunks > indexedChunks) {
            throw new IllegalArgumentException(
                    "current runbook chunks cannot exceed indexed chunks"
            );
        }
    }

    public boolean ready() {
        return indexedChunks == expectedChunks && currentChunks == expectedChunks;
    }
}
