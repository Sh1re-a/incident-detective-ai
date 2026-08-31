package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.InvestigationData;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * One immutable, request-local generated incident package.
 *
 * <p>{@code hiddenGroundTruth} is for post-model verification only. It must
 * never be included in a model prompt, tool response, or pre-completion API
 * payload.</p>
 */
public record GeneratedCase(
        Scenario scenario,
        InvestigationData investigationData,
        @JsonIgnore
        GroundTruth hiddenGroundTruth
) {
    public GeneratedCase {
        Objects.requireNonNull(scenario, "scenario must not be null");
        Objects.requireNonNull(
                investigationData,
                "investigationData must not be null"
        );
        Objects.requireNonNull(
                hiddenGroundTruth,
                "hiddenGroundTruth must not be null"
        );

        if (!scenario.equals(investigationData.scenario())) {
            throw new IllegalArgumentException(
                    "investigationData must contain the generated scenario"
            );
        }
        if (!scenario.scenarioId().equals(hiddenGroundTruth.scenarioId())) {
            throw new IllegalArgumentException(
                    "hiddenGroundTruth must belong to the generated scenario"
            );
        }

        Set<String> evidenceIds = new HashSet<>();
        for (Evidence evidence : investigationData.evidenceInventory()) {
            if (!scenario.scenarioId().equals(evidence.scenarioId())) {
                throw new IllegalArgumentException(
                        "generated evidence must belong to the generated scenario"
                );
            }
            if (!evidenceIds.add(evidence.evidenceId())) {
                throw new IllegalArgumentException(
                        "generated evidence IDs must be unique"
                );
            }
        }

        Set<String> supportedIds = hiddenGroundTruth.claimSupport().stream()
                .map(ClaimSupport::allowedEvidenceIds)
                .flatMap(java.util.List::stream)
                .collect(java.util.stream.Collectors.toSet());
        if (!evidenceIds.containsAll(supportedIds)) {
            throw new IllegalArgumentException(
                    "hiddenGroundTruth must cite only generated evidence"
            );
        }
    }
}
