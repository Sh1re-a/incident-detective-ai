package dev.shirwac.incidentdetective.diagnostic;

import java.util.List;
import java.util.Objects;

/** Immutable receipt proving which bounded probe ran and what it observed. */
public record DiagnosticProbeReceipt(
        String scenarioId,
        DiagnosticProbeId probeId,
        DiagnosticProbeOutcome outcome,
        String safeSummary,
        List<DiagnosticProbeFinding> findings,
        boolean truncated
) {
    public static final int MAX_SUMMARY_LENGTH = 240;
    public static final int MAX_FINDINGS = 8;

    public DiagnosticProbeReceipt {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        scenarioId = scenarioId.strip();
        Objects.requireNonNull(probeId, "probeId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (safeSummary == null || safeSummary.isBlank()) {
            throw new IllegalArgumentException("safeSummary must not be blank");
        }
        safeSummary = safeSummary.strip().replaceAll("\\s+", " ");
        if (safeSummary.length() > MAX_SUMMARY_LENGTH) {
            safeSummary = safeSummary.substring(0, MAX_SUMMARY_LENGTH);
        }
        if (findings == null || findings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "findings must not be null or contain null"
            );
        }
        truncated = truncated || findings.size() > MAX_FINDINGS;
        findings = findings.stream().limit(MAX_FINDINGS).toList();
        if (outcome == DiagnosticProbeOutcome.OBSERVED && findings.isEmpty()) {
            throw new IllegalArgumentException(
                    "observed receipt must contain at least one finding"
            );
        }
    }
}
