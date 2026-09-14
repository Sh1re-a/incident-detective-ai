package dev.shirwac.incidentdetective.ai;

import java.util.Optional;

public final class ModelProviderException extends RuntimeException {

    private final ModelProviderFailure failure;
    private final ModelResponseFailureMetadata safeMetadata;

    public ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage
    ) {
        this(failure, internalMessage, null, null);
    }

    public ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage,
            Throwable cause
    ) {
        this(failure, internalMessage, cause, null);
    }

    public ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage,
            ModelResponseFailureMetadata safeMetadata
    ) {
        this(failure, internalMessage, null, safeMetadata);
    }

    private ModelProviderException(
            ModelProviderFailure failure,
            String internalMessage,
            Throwable cause,
            ModelResponseFailureMetadata safeMetadata
    ) {
        super(internalMessage, cause);
        this.failure = failure;
        this.safeMetadata = safeMetadata;
    }

    public ModelProviderFailure failure() {
        return failure;
    }

    public Optional<ModelResponseFailureMetadata> safeMetadata() {
        return Optional.ofNullable(safeMetadata);
    }
}
