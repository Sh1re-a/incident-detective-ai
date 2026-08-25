package dev.shirwac.incidentdetective.domain.verification;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record VerificationReport(
        boolean schemaPass,
        CitationValidity citationValidity,
        EvidencePrecision evidencePrecision,
        DiagnosisCorrectness diagnosisCorrectness,
        List<VerificationErrorCode> hardErrors
) {
    public VerificationReport {
        Objects.requireNonNull(citationValidity, "citationValidity must not be null");
        Objects.requireNonNull(evidencePrecision, "evidencePrecision must not be null");
        Objects.requireNonNull(diagnosisCorrectness, "diagnosisCorrectness must not be null");
        Objects.requireNonNull(hardErrors, "hardErrors must not be null");
        hardErrors = List.copyOf(hardErrors);

        if (new HashSet<>(hardErrors).size() != hardErrors.size()) {
            throw new IllegalArgumentException("hardErrors must not contain duplicates");
        }

        boolean hasSchemaError = hardErrors.contains(
                VerificationErrorCode.DIAGNOSIS_SCHEMA_INVALID
        ) || hardErrors.contains(VerificationErrorCode.GROUND_TRUTH_SCHEMA_INVALID);
        if (schemaPass == hasSchemaError) {
            throw new IllegalArgumentException(
                    "schemaPass must be false exactly when a schema error exists"
            );
        }

        boolean hasUnknownEvidence = hardErrors.contains(
                VerificationErrorCode.UNKNOWN_EVIDENCE_ID
        );
        if (citationValidity.valid() == hasUnknownEvidence) {
            throw new IllegalArgumentException(
                    "citation validity must match the unknown-evidence error"
            );
        }
    }
}
