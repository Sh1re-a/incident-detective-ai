package dev.shirwac.incidentdetective.diagnostic;

import java.util.Objects;

/** Safe rejection raised before a probe handler can inspect data. */
public final class DiagnosticProbeRejectedException extends RuntimeException {

    private final Code code;

    public DiagnosticProbeRejectedException(Code code, String safeMessage) {
        super(requireMessage(safeMessage));
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }

    private static String requireMessage(String safeMessage) {
        if (safeMessage == null || safeMessage.isBlank()) {
            throw new IllegalArgumentException("safeMessage must not be blank");
        }
        return safeMessage.strip();
    }

    /** Stable reasons that an API edge may translate without exposing data. */
    public enum Code {
        INVALID_CASE,
        SCENARIO_MISMATCH,
        CROSS_CASE_EVIDENCE
    }
}
