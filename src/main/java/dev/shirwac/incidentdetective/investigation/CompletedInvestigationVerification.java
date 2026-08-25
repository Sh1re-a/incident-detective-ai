package dev.shirwac.incidentdetective.investigation;

import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.replay.ReplayComparison;

import java.util.Objects;

public record CompletedInvestigationVerification(
        VerificationReport report,
        ReplayComparison comparison
) {
    public CompletedInvestigationVerification {
        Objects.requireNonNull(report, "report must not be null");
        Objects.requireNonNull(comparison, "comparison must not be null");
    }
}
