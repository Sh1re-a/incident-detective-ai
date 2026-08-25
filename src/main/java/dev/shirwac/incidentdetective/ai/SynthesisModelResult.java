package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;

import java.util.Objects;

public record SynthesisModelResult(
        Diagnosis diagnosis,
        ModelCallMetadata metadata
) {
    public SynthesisModelResult {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
    }
}
