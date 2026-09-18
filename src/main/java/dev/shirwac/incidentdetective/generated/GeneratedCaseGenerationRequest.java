package dev.shirwac.incidentdetective.generated;

import java.util.Objects;

/**
 * Bounded generation controls with an optional seed.
 *
 * <p>A null seed asks the server to choose one. A null evidence mode asks the
 * service to select a reproducible mode from the resolved seed. Both resolved
 * values are returned in the generation receipt so the exact variant can be
 * replayed.</p>
 */
public record GeneratedCaseGenerationRequest(
        Long seed,
        GeneratedIncidentFamily incidentFamily,
        GeneratedEvidenceMode evidenceMode,
        GeneratedNoiseLevel noiseLevel
) {
    public GeneratedCaseGenerationRequest {
        incidentFamily = incidentFamily == null
                ? GeneratedIncidentFamily.PAYMENT_TIMEOUT
                : incidentFamily;
        Objects.requireNonNull(noiseLevel, "noiseLevel must not be null");
    }

    GeneratedCaseRequest replayRequest(
            GeneratedCaseSeed resolvedSeed,
            GeneratedEvidenceMode resolvedEvidenceMode
    ) {
        Objects.requireNonNull(resolvedSeed, "resolvedSeed must not be null");
        Objects.requireNonNull(
                resolvedEvidenceMode,
                "resolvedEvidenceMode must not be null"
        );
        return new GeneratedCaseRequest(
                resolvedSeed.value(),
                incidentFamily,
                resolvedEvidenceMode,
                noiseLevel
        );
    }
}
