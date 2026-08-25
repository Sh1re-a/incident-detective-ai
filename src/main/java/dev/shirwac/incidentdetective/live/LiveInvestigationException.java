package dev.shirwac.incidentdetective.live;

public final class LiveInvestigationException extends RuntimeException {

    private final LiveInvestigationFailure failure;

    public LiveInvestigationException(
            LiveInvestigationFailure failure,
            String internalMessage
    ) {
        super(internalMessage);
        this.failure = failure;
    }

    public LiveInvestigationFailure failure() {
        return failure;
    }
}
