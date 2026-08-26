package dev.shirwac.incidentdetective.domain.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCoverageTest {

    private static final double TOLERANCE = 0.000_001;

    @Test
    void createsAFullCoverageScore() {
        ClaimCoverage coverage = ClaimCoverage.scored(5, 5);

        assertTrue(coverage.applicable());
        assertEquals(5, coverage.matchedClaimCount());
        assertEquals(5, coverage.referenceClaimCount());
        assertEquals(1.0, coverage.score(), TOLERANCE);
    }

    @Test
    void createsAPartialCoverageScore() {
        ClaimCoverage coverage = ClaimCoverage.scored(2, 5);

        assertEquals(0.4, coverage.score(), TOLERANCE);
    }

    @Test
    void createsANonApplicableResultWithoutReferenceClaims() {
        ClaimCoverage coverage = ClaimCoverage.notApplicable();

        assertFalse(coverage.applicable());
        assertEquals(0, coverage.matchedClaimCount());
        assertEquals(0, coverage.referenceClaimCount());
        assertNull(coverage.score());
    }

    @Test
    void rejectsCountsAndScoresThatDisagree() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ClaimCoverage.scored(3, 2)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClaimCoverage(true, 1, 2, 1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClaimCoverage(false, 0, 0, 0.0)
        );
    }
}
