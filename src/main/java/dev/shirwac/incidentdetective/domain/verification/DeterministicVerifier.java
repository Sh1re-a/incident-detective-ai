package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.ExpectedClaim;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DeterministicVerifier {

    private final Validator validator;

    public DeterministicVerifier() {
        this(DefaultValidatorHolder.INSTANCE);
    }

    public DeterministicVerifier(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    public VerificationReport verify(
            Diagnosis diagnosis,
            Set<String> seenEvidenceIds,
            GroundTruth groundTruth
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(seenEvidenceIds, "seenEvidenceIds must not be null");
        Objects.requireNonNull(groundTruth, "groundTruth must not be null");

        boolean diagnosisSchemaValid = validator.validate(diagnosis).isEmpty();
        boolean groundTruthSchemaValid = validator.validate(groundTruth).isEmpty();
        CitationValidity citationValidity = verifyCitations(diagnosis, seenEvidenceIds);
        List<VerificationErrorCode> hardErrors = new ArrayList<>();
        if (!diagnosisSchemaValid) {
            hardErrors.add(VerificationErrorCode.DIAGNOSIS_SCHEMA_INVALID);
        }
        if (!groundTruthSchemaValid) {
            hardErrors.add(VerificationErrorCode.GROUND_TRUTH_SCHEMA_INVALID);
        }
        if (!citationValidity.valid()) {
            hardErrors.add(VerificationErrorCode.UNKNOWN_EVIDENCE_ID);
        }

        EvidencePrecision evidencePrecision;
        ClaimCoverage claimCoverage;
        DiagnosisCorrectness diagnosisCorrectness;
        if (!groundTruthSchemaValid) {
            evidencePrecision = EvidencePrecision.notApplicable();
            claimCoverage = ClaimCoverage.notApplicable();
            diagnosisCorrectness = DiagnosisCorrectness.notEvaluated();
        } else if (!diagnosisSchemaValid) {
            evidencePrecision = hasReferenceClaimSupport(groundTruth)
                    ? EvidencePrecision.scored(List.of())
                    : EvidencePrecision.notApplicable();
            claimCoverage = groundTruth.expectedClaims().isEmpty()
                    ? ClaimCoverage.notApplicable()
                    : ClaimCoverage.scored(0, groundTruth.expectedClaims().size());
            diagnosisCorrectness = DiagnosisCorrectness.notEvaluated();
        } else {
            evidencePrecision = scoreEvidencePrecision(diagnosis, groundTruth);
            claimCoverage = scoreClaimCoverage(diagnosis, groundTruth);
            diagnosisCorrectness = verifyDiagnosis(diagnosis, groundTruth);
        }

        return new VerificationReport(
                diagnosisSchemaValid,
                groundTruthSchemaValid,
                citationValidity,
                evidencePrecision,
                claimCoverage,
                diagnosisCorrectness,
                hardErrors
        );
    }

    CitationValidity verifyCitations(
            Diagnosis diagnosis,
            Set<String> seenEvidenceIds
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(seenEvidenceIds, "seenEvidenceIds must not be null");

        List<String> unknownEvidenceIds = citedEvidenceIds(diagnosis).stream()
                .filter(evidenceId -> !seenEvidenceIds.contains(evidenceId))
                .sorted()
                .toList();

        return new CitationValidity(unknownEvidenceIds.isEmpty(), unknownEvidenceIds);
    }

    EvidencePrecision scoreEvidencePrecision(
            Diagnosis diagnosis,
            GroundTruth groundTruth
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(groundTruth, "groundTruth must not be null");

        if (!hasReferenceClaimSupport(groundTruth)) {
            return EvidencePrecision.notApplicable();
        }

        Set<CitationTriple> citationTriples = citationTriples(diagnosis);
        Map<ClaimKey, Set<String>> allowedEvidenceByClaim = allowedEvidence(groundTruth);

        List<CitationSupportResult> citationSupport = citationTriples.stream()
                .sorted(Comparator
                        .comparing((CitationTriple triple) ->
                                triple.claimKey().claimCode().wireValue())
                        .thenComparing(triple ->
                                triple.claimKey().claimValueCode())
                        .thenComparing(CitationTriple::evidenceId))
                .map(triple -> new CitationSupportResult(
                        triple.claimKey().claimCode(),
                        triple.claimKey().claimValueCode(),
                        triple.evidenceId(),
                        allowedEvidenceByClaim
                                .getOrDefault(triple.claimKey(), Set.of())
                                .contains(triple.evidenceId())
                ))
                .toList();

        return EvidencePrecision.scored(citationSupport);
    }

    private boolean hasReferenceClaimSupport(GroundTruth groundTruth) {
        return groundTruth.claimSupport() != null
                && !groundTruth.claimSupport().isEmpty();
    }

    ClaimCoverage scoreClaimCoverage(
            Diagnosis diagnosis,
            GroundTruth groundTruth
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(groundTruth, "groundTruth must not be null");

        if (groundTruth.expectedClaims() == null || groundTruth.expectedClaims().isEmpty()) {
            return ClaimCoverage.notApplicable();
        }

        Set<ClaimKey> referenceClaims = groundTruth.expectedClaims().stream()
                .filter(Objects::nonNull)
                .map(this::claimKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ClaimKey> modelClaims = diagnosis.claims() == null
                ? Set.of()
                : diagnosis.claims().stream()
                        .filter(Objects::nonNull)
                        .filter(claim -> claim.claimCode() != null)
                        .filter(claim -> claim.claimValueCode() != null)
                        .map(claim -> new ClaimKey(
                                claim.claimCode(),
                                claim.claimValueCode()
                        ))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());

        int matchedClaims = (int) referenceClaims.stream()
                .filter(modelClaims::contains)
                .count();
        return ClaimCoverage.scored(matchedClaims, referenceClaims.size());
    }

    DiagnosisCorrectness verifyDiagnosis(
            Diagnosis diagnosis,
            GroundTruth groundTruth
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(groundTruth, "groundTruth must not be null");

        if (groundTruth.expectedStatus() == DiagnosisStatus.DIAGNOSED) {
            boolean diagnosed = diagnosis.status() == DiagnosisStatus.DIAGNOSED;
            return DiagnosisCorrectness.diagnosis(
                    diagnosed && Objects.equals(
                            diagnosis.rootCauseCode(),
                            groundTruth.rootCauseCode()
                    ),
                    diagnosed && Objects.equals(
                            diagnosis.affectedService(),
                            groundTruth.affectedService()
                    )
            );
        }

        if (groundTruth.expectedStatus() == DiagnosisStatus.INSUFFICIENT_EVIDENCE) {
            boolean correctAbstention = diagnosis.status()
                    == DiagnosisStatus.INSUFFICIENT_EVIDENCE
                    && diagnosis.rootCauseCode() == null
                    && diagnosis.affectedService() == null;
            return DiagnosisCorrectness.abstention(correctAbstention);
        }

        return DiagnosisCorrectness.notEvaluated();
    }

    private Set<String> citedEvidenceIds(Diagnosis diagnosis) {
        if (diagnosis.claims() == null) {
            return Set.of();
        }

        return diagnosis.claims().stream()
                .filter(Objects::nonNull)
                .map(Claim::evidenceIds)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Set<CitationTriple> citationTriples(Diagnosis diagnosis) {
        if (diagnosis.claims() == null) {
            return Set.of();
        }

        Set<CitationTriple> triples = new HashSet<>();
        for (Claim claim : diagnosis.claims()) {
            if (claim == null
                    || claim.claimCode() == null
                    || claim.claimValueCode() == null
                    || claim.evidenceIds() == null) {
                continue;
            }

            ClaimKey claimKey = new ClaimKey(claim.claimCode(), claim.claimValueCode());
            claim.evidenceIds().stream()
                    .filter(Objects::nonNull)
                    .map(evidenceId -> new CitationTriple(claimKey, evidenceId))
                    .forEach(triples::add);
        }
        return Set.copyOf(triples);
    }

    private Map<ClaimKey, Set<String>> allowedEvidence(GroundTruth groundTruth) {
        if (groundTruth.claimSupport() == null) {
            return Map.of();
        }

        Map<ClaimKey, Set<String>> allowedEvidenceByClaim = new HashMap<>();
        for (ClaimSupport support : groundTruth.claimSupport()) {
            if (support == null
                    || support.claimCode() == null
                    || support.claimValueCode() == null
                    || support.allowedEvidenceIds() == null) {
                continue;
            }

            ClaimKey claimKey = new ClaimKey(
                    support.claimCode(),
                    support.claimValueCode()
            );
            allowedEvidenceByClaim
                    .computeIfAbsent(claimKey, ignored -> new HashSet<>())
                    .addAll(support.allowedEvidenceIds());
        }
        return Map.copyOf(allowedEvidenceByClaim);
    }

    private ClaimKey claimKey(ExpectedClaim expectedClaim) {
        return new ClaimKey(
                expectedClaim.claimCode(),
                expectedClaim.claimValueCode()
        );
    }

    private static final class DefaultValidatorHolder {
        private static final Validator INSTANCE = Validation
                .buildDefaultValidatorFactory()
                .getValidator();

        private DefaultValidatorHolder() {
        }
    }
}
