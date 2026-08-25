package dev.shirwac.incidentdetective.ai;

public final class ModelProviderException extends RuntimeException {

    private final ModelProviderFailure failure;

    public ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage
    ) {
        super(internalMessage);
        this.failure = failure;
    }

    public ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage,
            Throwable cause
    ) {
        super(internalMessage, cause);
        this.failure = failure;
    }

    public ModelProviderFailure failure() {
        return failure;
    }
}
