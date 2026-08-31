package dev.shirwac.incidentdetective.generated;

import java.util.Objects;

/**
 * Reproducible input for one request-local generated case.
 *
 * <p>Identifiers and incident content are always produced by the generator;
 * callers can only select a seed and the two bounded generation modes.</p>
 */
public record GeneratedCaseRequest(
        long seed,
        GeneratedEvidenceMode evidenceMode,
        GeneratedNoiseLevel noiseLevel
) {
    public GeneratedCaseRequest {
        Objects.requireNonNull(evidenceMode, "evidenceMode must not be null");
        Objects.requireNonNull(noiseLevel, "noiseLevel must not be null");
    }
}
