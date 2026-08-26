package dev.shirwac.incidentdetective.rag;

public final class RunbookIndexNotReadyException extends RuntimeException {

    private final long indexedChunks;
    private final int expectedChunks;

    public RunbookIndexNotReadyException(long indexedChunks, int expectedChunks) {
        super("Runbook index is not ready: indexed " + indexedChunks
                + " of " + expectedChunks + " chunks");
        this.indexedChunks = indexedChunks;
        this.expectedChunks = expectedChunks;
    }

    public long indexedChunks() {
        return indexedChunks;
    }

    public int expectedChunks() {
        return expectedChunks;
    }
}
