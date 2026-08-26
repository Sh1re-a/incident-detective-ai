package dev.shirwac.incidentdetective.rag;

public final class RunbookIndexNotReadyException extends RuntimeException {

    private final RunbookIndexStatus status;

    public RunbookIndexNotReadyException(RunbookIndexStatus status) {
        super("Runbook index is not ready: indexed " + status.indexedChunks()
                + ", current " + status.currentChunks()
                + ", expected " + status.expectedChunks() + " chunks");
        this.status = status;
    }

    public long indexedChunks() {
        return status.indexedChunks();
    }

    public long currentChunks() {
        return status.currentChunks();
    }

    public int expectedChunks() {
        return status.expectedChunks();
    }
}
