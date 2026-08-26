package dev.shirwac.incidentdetective.rag;

public final class RunbookEmbeddingException extends RuntimeException {

    private final RunbookEmbeddingFailure failure;

    public RunbookEmbeddingException(
            RunbookEmbeddingFailure failure,
            String message
    ) {
        super(message);
        this.failure = failure;
    }

    public RunbookEmbeddingException(
            RunbookEmbeddingFailure failure,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failure = failure;
    }

    public RunbookEmbeddingFailure failure() {
        return failure;
    }
}
