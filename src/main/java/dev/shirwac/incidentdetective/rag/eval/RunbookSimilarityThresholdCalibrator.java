package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.RunbookSearchHit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RunbookSimilarityThresholdCalibrator {

    private static final double EPSILON = 1e-12;

    Calibration calibrate(List<CalibrationCase> developmentCases) {
        List<CalibrationCase> cases = List.copyOf(developmentCases);
        validateCases(cases);

        List<Double> candidates = candidates(cases);
        Calibration best = null;
        for (double threshold : candidates) {
            Calibration candidate = score(cases, threshold, candidates.size());
            if (best == null || better(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private List<Double> candidates(List<CalibrationCase> cases) {
        List<Double> scores = cases.stream()
                .flatMap(evalCase -> evalCase.hits().stream())
                .map(RunbookSearchHit::cosineSimilarity)
                .distinct()
                .sorted()
                .toList();
        List<Double> candidates = new ArrayList<>();
        candidates.add(-1.0);
        for (int index = 1; index < scores.size(); index++) {
            double lower = scores.get(index - 1);
            double upper = scores.get(index);
            candidates.add(lower + ((upper - lower) / 2.0));
        }
        candidates.add(1.0);
        return candidates.stream().distinct().sorted().toList();
    }

    private Calibration score(
            List<CalibrationCase> cases,
            double threshold,
            int candidateCount
    ) {
        int positiveCases = 0;
        int positiveHits = 0;
        double reciprocalRankSum = 0;
        int noMatchCases = 0;
        int correctNoMatches = 0;

        for (CalibrationCase evalCase : cases) {
            List<RunbookSearchHit> accepted = evalCase.hits().stream()
                    .filter(hit -> hit.cosineSimilarity() >= threshold)
                    .toList();
            if (evalCase.expectedEmpty()) {
                noMatchCases++;
                if (accepted.isEmpty()) {
                    correctNoMatches++;
                }
                continue;
            }

            positiveCases++;
            for (int index = 0; index < accepted.size(); index++) {
                if (evalCase.relevantEvidenceIds().contains(
                        accepted.get(index).entry().evidenceId()
                )) {
                    positiveHits++;
                    reciprocalRankSum += 1.0 / (index + 1);
                    break;
                }
            }
        }

        double hitAtK = ratio(positiveHits, positiveCases);
        double noMatchAccuracy = ratio(correctNoMatches, noMatchCases);
        double meanReciprocalRank = reciprocalRankSum / positiveCases;
        double objective = 0.5 * hitAtK + 0.5 * noMatchAccuracy;
        return new Calibration(
                threshold,
                objective,
                hitAtK,
                meanReciprocalRank,
                noMatchAccuracy,
                candidateCount
        );
    }

    private boolean better(Calibration candidate, Calibration best) {
        if (greater(candidate.objective(), best.objective())) {
            return true;
        }
        if (!equal(candidate.objective(), best.objective())) {
            return false;
        }
        if (greater(candidate.noMatchAccuracy(), best.noMatchAccuracy())) {
            return true;
        }
        if (!equal(candidate.noMatchAccuracy(), best.noMatchAccuracy())) {
            return false;
        }
        if (greater(candidate.meanReciprocalRank(), best.meanReciprocalRank())) {
            return true;
        }
        if (!equal(candidate.meanReciprocalRank(), best.meanReciprocalRank())) {
            return false;
        }
        return candidate.threshold() < best.threshold();
    }

    private boolean greater(double left, double right) {
        return left - right > EPSILON;
    }

    private boolean equal(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private double ratio(int numerator, int denominator) {
        return (double) numerator / denominator;
    }

    private void validateCases(List<CalibrationCase> cases) {
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("development cases are required");
        }
        boolean hasPositive = false;
        boolean hasNoMatch = false;
        for (CalibrationCase evalCase : cases) {
            if (evalCase.expectedEmpty()) {
                hasNoMatch = true;
            } else {
                hasPositive = true;
            }
            double previous = Double.POSITIVE_INFINITY;
            for (RunbookSearchHit hit : evalCase.hits()) {
                if (hit.cosineSimilarity() > previous) {
                    throw new IllegalArgumentException(
                            "search hits must be sorted by descending similarity"
                    );
                }
                previous = hit.cosineSimilarity();
            }
        }
        if (!hasPositive || !hasNoMatch) {
            throw new IllegalArgumentException(
                    "development cases need positive and no-match coverage"
            );
        }
    }

    record CalibrationCase(
            boolean expectedEmpty,
            Set<String> relevantEvidenceIds,
            List<RunbookSearchHit> hits
    ) {
        CalibrationCase {
            relevantEvidenceIds = Set.copyOf(relevantEvidenceIds);
            hits = List.copyOf(hits);
            if (expectedEmpty != relevantEvidenceIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "expected-empty and relevant evidence must agree"
                );
            }
            if (new HashSet<>(hits).size() != hits.size()) {
                throw new IllegalArgumentException("search hits must be unique");
            }
        }
    }

    record Calibration(
            double threshold,
            double objective,
            double hitAtK,
            double meanReciprocalRank,
            double noMatchAccuracy,
            int candidateCount
    ) {
    }
}
