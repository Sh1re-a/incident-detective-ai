package dev.shirwac.incidentdetective.domain.verification;

public record ClaimCoverage(
        boolean applicable,
        int matchedClaimCount,
        int referenceClaimCount,
        Double score
) {
    public ClaimCoverage {
        if (matchedClaimCount < 0
                || referenceClaimCount < 0
                || matchedClaimCount > referenceClaimCount) {
            throw new IllegalArgumentException("claim counts must be valid");
        }

        if (!applicable && (matchedClaimCount != 0
                || referenceClaimCount != 0
                || score != null)) {
            throw new IllegalArgumentException(
                    "a non-applicable score must not contain counts or a score"
            );
        }

        if (applicable && (referenceClaimCount == 0
                || score == null
                || !Double.isFinite(score)
                || score < 0.0
                || score > 1.0)) {
            throw new IllegalArgumentException(
                    "an applicable score needs a reference and must be between zero and one"
            );
        }

        if (applicable) {
            double expectedScore = (double) matchedClaimCount / referenceClaimCount;
            if (Double.compare(score, expectedScore) != 0) {
                throw new IllegalArgumentException("score must match the claim counts");
            }
        }
    }

    public static ClaimCoverage notApplicable() {
        return new ClaimCoverage(false, 0, 0, null);
    }

    public static ClaimCoverage scored(int matchedClaimCount, int referenceClaimCount) {
        if (referenceClaimCount <= 0) {
            throw new IllegalArgumentException("referenceClaimCount must be positive");
        }
        return new ClaimCoverage(
                true,
                matchedClaimCount,
                referenceClaimCount,
                (double) matchedClaimCount / referenceClaimCount
        );
    }
}
