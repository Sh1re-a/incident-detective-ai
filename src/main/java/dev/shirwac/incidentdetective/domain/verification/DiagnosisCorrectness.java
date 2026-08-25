package dev.shirwac.incidentdetective.domain.verification;

public record DiagnosisCorrectness(
        boolean evaluated,
        boolean diagnosisApplicable,
        boolean rootCauseCorrect,
        boolean affectedServiceCorrect,
        boolean abstentionCorrect
) {
    public DiagnosisCorrectness {
        if (!evaluated && (diagnosisApplicable
                || rootCauseCorrect
                || affectedServiceCorrect
                || abstentionCorrect)) {
            throw new IllegalArgumentException(
                    "a non-evaluated result must not contain correctness values"
            );
        }

        if (diagnosisApplicable && abstentionCorrect) {
            throw new IllegalArgumentException(
                    "a diagnosis result cannot also be a successful abstention"
            );
        }

        if (!diagnosisApplicable && (rootCauseCorrect || affectedServiceCorrect)) {
            throw new IllegalArgumentException(
                    "root cause and service only apply to diagnosable cases"
            );
        }
    }

    public static DiagnosisCorrectness notEvaluated() {
        return new DiagnosisCorrectness(false, false, false, false, false);
    }

    public static DiagnosisCorrectness diagnosis(
            boolean rootCauseCorrect,
            boolean affectedServiceCorrect
    ) {
        return new DiagnosisCorrectness(
                true,
                true,
                rootCauseCorrect,
                affectedServiceCorrect,
                false
        );
    }

    public static DiagnosisCorrectness abstention(boolean abstentionCorrect) {
        return new DiagnosisCorrectness(
                true,
                false,
                false,
                false,
                abstentionCorrect
        );
    }
}
