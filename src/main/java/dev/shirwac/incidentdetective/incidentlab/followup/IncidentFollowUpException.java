package dev.shirwac.incidentdetective.incidentlab.followup;

public final class IncidentFollowUpException extends RuntimeException {

    private final Code code;

    IncidentFollowUpException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        RUN_REFERENCE_EXPIRED,
        IDEMPOTENCY_CONFLICT,
        TURN_ALREADY_ATTEMPTED,
        REPLAY_QUESTION_NOT_SUPPORTED,
        RESPONSE_NOT_VERIFIABLE
    }
}
