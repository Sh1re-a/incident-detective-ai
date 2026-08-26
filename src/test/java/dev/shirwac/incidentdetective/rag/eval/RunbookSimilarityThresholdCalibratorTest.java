package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunbookSimilarityThresholdCalibratorTest {

    private final RunbookSimilarityThresholdCalibrator calibrator =
            new RunbookSimilarityThresholdCalibrator();

    @Test
    void separatesDevelopmentPositivesFromDevelopmentNoMatches() {
        var calibration = calibrator.calibrate(List.of(
                positive("relevant", hit("relevant", 0.80), hit("other", 0.55)),
                positive("second", hit("second", 0.72), hit("other", 0.50)),
                noMatch(hit("other", 0.40), hit("second", 0.35))
        ));

        assertEquals(0.45, calibration.threshold(), 0.000_000_1);
        assertEquals(1.0, calibration.objective());
        assertEquals(1.0, calibration.hitAtK());
        assertEquals(1.0, calibration.meanReciprocalRank());
        assertEquals(1.0, calibration.noMatchAccuracy());
    }

    @Test
    void prefersNoMatchAccuracyThenMrrThenLowerThresholdOnTies() {
        var calibration = calibrator.calibrate(List.of(
                positive("relevant", hit("other", 0.90), hit("relevant", 0.80)),
                noMatch(hit("noise", 0.85))
        ));

        assertEquals(0.875, calibration.threshold());
        assertEquals(0.5, calibration.objective());
        assertEquals(0.0, calibration.hitAtK());
        assertEquals(1.0, calibration.noMatchAccuracy());
    }

    @Test
    void rejectsUnsortedSearchResults() {
        var cases = List.of(
                positive("relevant", hit("other", 0.40), hit("relevant", 0.80)),
                noMatch(hit("noise", 0.30))
        );

        assertThrows(IllegalArgumentException.class, () -> calibrator.calibrate(cases));
    }

    private RunbookSimilarityThresholdCalibrator.CalibrationCase positive(
            String relevantEvidenceId,
            RunbookSearchHit... hits
    ) {
        return new RunbookSimilarityThresholdCalibrator.CalibrationCase(
                false,
                Set.of(relevantEvidenceId),
                List.of(hits)
        );
    }

    private RunbookSimilarityThresholdCalibrator.CalibrationCase noMatch(
            RunbookSearchHit... hits
    ) {
        return new RunbookSimilarityThresholdCalibrator.CalibrationCase(
                true,
                Set.of(),
                List.of(hits)
        );
    }

    private RunbookSearchHit hit(String evidenceId, double similarity) {
        return new RunbookSearchHit(
                new RunbookCorpusEntry(
                        evidenceId,
                        "doc-" + evidenceId,
                        "1.0",
                        "chunk-" + evidenceId,
                        "Title " + evidenceId,
                        "Summary " + evidenceId,
                        "runbooks/doc-" + evidenceId + "#chunk-" + evidenceId,
                        "Body " + evidenceId
                ),
                similarity
        );
    }
}
