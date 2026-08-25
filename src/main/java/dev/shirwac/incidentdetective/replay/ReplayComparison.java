package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;

public record ReplayComparison(
        DiagnosisStatus expectedStatus,
        String expectedRootCauseCode,
        String expectedAffectedService,
        boolean rootCauseCorrect,
        boolean affectedServiceCorrect,
        boolean abstentionCorrect
) {
}
