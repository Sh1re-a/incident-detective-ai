package dev.shirwac.incidentdetective.generated;

import java.util.Objects;

/** One generated case paired with its safe replay receipt. */
public record GeneratedCaseGeneration(
        GeneratedCase generatedCase,
        GeneratedCaseReceipt receipt
) {
    public GeneratedCaseGeneration {
        Objects.requireNonNull(generatedCase, "generatedCase must not be null");
        Objects.requireNonNull(receipt, "receipt must not be null");
        if (!generatedCase.scenario().scenarioId().equals(receipt.scenarioId())) {
            throw new IllegalArgumentException(
                    "receipt must belong to the generated case"
            );
        }
    }
}
