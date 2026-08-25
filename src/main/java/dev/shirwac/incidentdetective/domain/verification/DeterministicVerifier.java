package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DeterministicVerifier {

    public CitationValidity verifyCitations(
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

    public EvidencePrecision scoreEvidencePrecision(
            Diagnosis diagnosis,
            GroundTruth groundTruth
    ) {
        Objects.requireNonNull(diagnosis, "diagnosis must not be null");
        Objects.requireNonNull(groundTruth, "groundTruth must not be null");

        if (groundTruth.expectedStatus() == DiagnosisStatus.INSUFFICIENT_EVIDENCE) {
            return EvidencePrecision.notApplicable();
        }

        if (diagnosis.status() != DiagnosisStatus.DIAGNOSED) {
            return EvidencePrecision.scored(0, 0);
        }

        Set<CitationTriple> citationTriples = citationTriples(diagnosis);
        Map<ClaimKey, Set<String>> allowedEvidenceByClaim = allowedEvidence(groundTruth);

        int supportedTriples = (int) citationTriples.stream()
                .filter(triple -> allowedEvidenceByClaim
                        .getOrDefault(triple.claimKey(), Set.of())
                        .contains(triple.evidenceId()))
                .count();

        return EvidencePrecision.scored(supportedTriples, citationTriples.size());
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
}
