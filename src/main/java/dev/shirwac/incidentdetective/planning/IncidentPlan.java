package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;

import java.util.List;
import java.util.Objects;

/** Immutable plan that is safe to hand to a synthetic case generator. */
public record IncidentPlan(
        String contractVersion,
        GeneratedIncidentFamily incidentFamily,
        IncidentSeverity severity,
        List<IncidentService> affectedServices,
        String summary,
        boolean syntheticOnly,
        boolean writeActionsAllowed,
        boolean humanApprovalRequired
) {

    public static final String CONTRACT_VERSION = "incident-plan-v1";

    public IncidentPlan {
        if (!CONTRACT_VERSION.equals(contractVersion)) {
            throw new IllegalArgumentException(
                    "contractVersion must be " + CONTRACT_VERSION
            );
        }
        Objects.requireNonNull(incidentFamily, "incidentFamily must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        affectedServices = List.copyOf(Objects.requireNonNull(
                affectedServices,
                "affectedServices must not be null"
        ));
        if (affectedServices.isEmpty()) {
            throw new IllegalArgumentException(
                    "affectedServices must not be empty"
            );
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        summary = summary.strip();
        if (!syntheticOnly || writeActionsAllowed || !humanApprovalRequired) {
            throw new IllegalArgumentException(
                    "incident plans must stay synthetic, read-only and human-approved"
            );
        }
    }

    public List<String> affectedServiceCodes() {
        return affectedServices.stream()
                .map(IncidentService::serviceCode)
                .toList();
    }
}
