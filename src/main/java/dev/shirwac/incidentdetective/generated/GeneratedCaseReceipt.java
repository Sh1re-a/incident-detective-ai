package dev.shirwac.incidentdetective.generated;

import java.util.Objects;

/** Safe, compact provenance for reproducing one generated incident variant. */
public record GeneratedCaseReceipt(
        String generatorVersion,
        long seed,
        GeneratedCaseSeed.Origin seedOrigin,
        GeneratedIncidentFamily incidentFamily,
        GeneratedEvidenceMode evidenceMode,
        GeneratedNoiseLevel noiseLevel,
        String scenarioId,
        GeneratedCaseVariant variant
) {
    public GeneratedCaseReceipt {
        if (generatorVersion == null || generatorVersion.isBlank()) {
            throw new IllegalArgumentException("generatorVersion must not be blank");
        }
        Objects.requireNonNull(seedOrigin, "seedOrigin must not be null");
        Objects.requireNonNull(incidentFamily, "incidentFamily must not be null");
        Objects.requireNonNull(evidenceMode, "evidenceMode must not be null");
        Objects.requireNonNull(noiseLevel, "noiseLevel must not be null");
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        Objects.requireNonNull(variant, "variant must not be null");
        GeneratedCaseRequest replayRequest = new GeneratedCaseRequest(
                seed,
                incidentFamily,
                evidenceMode,
                noiseLevel
        );
        GeneratedCaseVariant expectedVariant = GeneratedCaseVariant.from(
                generatorVersion,
                replayRequest,
                scenarioId
        );
        if (!expectedVariant.equals(variant)) {
            throw new IllegalArgumentException(
                    "variant must match the receipt replay controls"
            );
        }
    }

    static GeneratedCaseReceipt from(
            String generatorVersion,
            GeneratedCaseSeed seed,
            GeneratedCaseRequest request,
            GeneratedCase generatedCase
    ) {
        Objects.requireNonNull(seed, "seed must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(generatedCase, "generatedCase must not be null");
        String scenarioId = generatedCase.scenario().scenarioId();
        return new GeneratedCaseReceipt(
                generatorVersion,
                seed.value(),
                seed.origin(),
                request.incidentFamily(),
                request.evidenceMode(),
                request.noiseLevel(),
                scenarioId,
                GeneratedCaseVariant.from(generatorVersion, request, scenarioId)
        );
    }
}
