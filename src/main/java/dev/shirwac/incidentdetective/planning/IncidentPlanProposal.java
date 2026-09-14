package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Untrusted, structured model output. It is never executable until the
 * deterministic validator has produced an {@link IncidentPlan}.
 */
public record IncidentPlanProposal(
        IncidentPlanProposalStatus status,
        String summary,
        GeneratedIncidentFamily incidentFamily,
        IncidentSeverity requestedSeverity,
        List<IncidentService> affectedServices,
        IncidentBlastRadius requestedBlastRadius
) {

    public static final int MAX_SUMMARY_LENGTH = 240;

    public IncidentPlanProposal {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(
                requestedBlastRadius,
                "requestedBlastRadius must not be null"
        );
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.strip();
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException(
                    "summary must be at most " + MAX_SUMMARY_LENGTH
                            + " characters"
            );
        }
        affectedServices = List.copyOf(Objects.requireNonNull(
                affectedServices,
                "affectedServices must not be null"
        ));
        if (affectedServices.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "affectedServices must not contain null"
            );
        }
        if (new HashSet<>(affectedServices).size() != affectedServices.size()) {
            throw new IllegalArgumentException(
                    "affectedServices must not contain duplicates"
            );
        }

        if (status == IncidentPlanProposalStatus.CANDIDATE) {
            Objects.requireNonNull(
                    incidentFamily,
                    "candidate incidentFamily must not be null"
            );
            Objects.requireNonNull(
                    requestedSeverity,
                    "candidate requestedSeverity must not be null"
            );
            if (affectedServices.isEmpty()) {
                throw new IllegalArgumentException(
                        "candidate affectedServices must not be empty"
                );
            }
        } else if (incidentFamily != null
                || requestedSeverity != null
                || !affectedServices.isEmpty()) {
            throw new IllegalArgumentException(
                    "unsupported proposals must not contain an executable candidate"
            );
        }
    }
}
