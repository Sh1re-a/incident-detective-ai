package dev.shirwac.incidentdetective.domain.verification;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record EvidencePrecision(
        boolean applicable,
        int supportedTriples,
        int totalTriples,
        Double score,
        List<CitationSupportResult> citationSupport
) {
    public EvidencePrecision {
        Objects.requireNonNull(citationSupport, "citationSupport must not be null");
        citationSupport = List.copyOf(citationSupport);

        if (supportedTriples < 0 || totalTriples < 0 || supportedTriples > totalTriples) {
            throw new IllegalArgumentException("citation counts must be valid");
        }

        if (new HashSet<>(citationSupport).size() != citationSupport.size()) {
            throw new IllegalArgumentException("citationSupport must not contain duplicates");
        }

        if (!applicable && (supportedTriples != 0
                || totalTriples != 0
                || score != null
                || !citationSupport.isEmpty())) {
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
            long supportedDetails = citationSupport.stream()
                    .filter(CitationSupportResult::supported)
                    .count();
            if (citationSupport.size() != totalTriples
                    || supportedDetails != supportedTriples) {
                throw new IllegalArgumentException(
                        "citationSupport must match the citation counts"
                );
            }
            double expectedScore = totalTriples == 0
                    ? 0.0
                    : (double) supportedTriples / totalTriples;
            if (Double.compare(score, expectedScore) != 0) {
                throw new IllegalArgumentException("score must match the citation counts");
            }
        }
    }

    public static EvidencePrecision notApplicable() {
        return new EvidencePrecision(false, 0, 0, null, List.of());
    }

    public static EvidencePrecision scored(
            List<CitationSupportResult> citationSupport
    ) {
        Objects.requireNonNull(citationSupport, "citationSupport must not be null");
        int supportedTriples = (int) citationSupport.stream()
                .filter(CitationSupportResult::supported)
                .count();
        int totalTriples = citationSupport.size();
        double score = totalTriples == 0
                ? 0.0
                : (double) supportedTriples / totalTriples;
        return new EvidencePrecision(
                true,
                supportedTriples,
                totalTriples,
                score,
                citationSupport
        );
    }
}
