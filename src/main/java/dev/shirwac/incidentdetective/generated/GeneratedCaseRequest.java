package dev.shirwac.incidentdetective.generated;

import java.util.Objects;

/**
 * Reproducible input for one request-local generated case.
 *
 * <p>Identifiers and incident content are always produced by the generator;
 * callers can only select a family, seed and the two bounded generation modes.</p>
 */
public record GeneratedCaseRequest(
        long seed,
        GeneratedIncidentFamily incidentFamily,
        GeneratedEvidenceMode evidenceMode,
        GeneratedNoiseLevel noiseLevel
) {
    public GeneratedCaseRequest {
        incidentFamily = incidentFamily == null
                ? GeneratedIncidentFamily.PAYMENT_TIMEOUT
                : incidentFamily;
        Objects.requireNonNull(evidenceMode, "evidenceMode must not be null");
        Objects.requireNonNull(noiseLevel, "noiseLevel must not be null");
    }

    /** Keeps the original API compatible and defaults to payment timeout. */
    public GeneratedCaseRequest(
            long seed,
            GeneratedEvidenceMode evidenceMode,
            GeneratedNoiseLevel noiseLevel
    ) {
        this(
                seed,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                evidenceMode,
                noiseLevel
        );
    }
}
