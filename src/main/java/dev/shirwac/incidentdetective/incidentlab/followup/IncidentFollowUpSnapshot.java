package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;

import java.util.List;

/** Sanitized immutable facts retained for bounded post-run questions. */
public record IncidentFollowUpSnapshot(
        String scenarioId,
        IncidentFollowUpResponse.Mode mode,
        IncidentLabRunResponse.AnswerState originalAnswerState,
        String problemService,
        LocalizedReport sv,
        LocalizedReport en,
        List<Source> sources,
        List<VerifiedFact> verifiedFacts,
        Boundary boundary
) {
    public IncidentFollowUpSnapshot {
        sources = sources == null ? List.of() : List.copyOf(sources);
        verifiedFacts = verifiedFacts == null
                ? List.of()
                : List.copyOf(verifiedFacts);
    }

    public record LocalizedReport(
            String headline,
            String problemLocation,
            String cause,
            String customerImpact,
            List<String> known,
            List<String> unknown,
            String certainty,
            String boundary
    ) {
        public LocalizedReport {
            known = known == null ? List.of() : List.copyOf(known);
            unknown = unknown == null ? List.of() : List.copyOf(unknown);
        }
    }

    public record Source(
            String evidenceId,
            String sourceRef,
            String sourceType,
            String label,
            String detail,
            IncidentFollowUpResponse.TargetScene targetScene,
            String targetId
    ) {
    }

    public record VerifiedFact(
            String claimCode,
            String claimValueCode,
            List<String> evidenceIds
    ) {
        public VerifiedFact {
            evidenceIds = evidenceIds == null
                    ? List.of()
                    : List.copyOf(evidenceIds);
        }
    }

    public record Boundary(
            int readOperations,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            boolean humanApprovalRequired,
            boolean javaAnswerReleased
    ) {
    }
}
