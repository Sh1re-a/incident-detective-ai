package dev.shirwac.incidentdetective.domain.verification;

public record EvidencePrecision(
        boolean applicable,
        int supportedTriples,
        int totalTriples,
        Double score
) {
    public EvidencePrecision {
        if (supportedTriples < 0 || totalTriples < 0 || supportedTriples > totalTriples) {
            throw new IllegalArgumentException("citation counts must be valid");
        }

        if (!applicable && (supportedTriples != 0 || totalTriples != 0 || score != null)) {
            throw new IllegalArgumentException(
                    "a non-applicable score must not contain counts or a score"
            );
        }

        if (applicable && (score == null
                || !Double.isFinite(score)
                || score < 0.0
                || score > 1.0)) {
            throw new IllegalArgumentException(
                    "an applicable score must be between zero and one"
            );
        }

        if (applicable) {
            double expectedScore = totalTriples == 0
                    ? 0.0
                    : (double) supportedTriples / totalTriples;
            if (Double.compare(score, expectedScore) != 0) {
                throw new IllegalArgumentException("score must match the citation counts");
            }
        }
    }

    public static EvidencePrecision notApplicable() {
        return new EvidencePrecision(false, 0, 0, null);
    }

    public static EvidencePrecision scored(int supportedTriples, int totalTriples) {
        double score = totalTriples == 0
                ? 0.0
                : (double) supportedTriples / totalTriples;
        return new EvidencePrecision(true, supportedTriples, totalTriples, score);
    }
}
