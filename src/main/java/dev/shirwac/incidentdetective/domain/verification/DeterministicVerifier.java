package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;

import java.util.List;
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
}
