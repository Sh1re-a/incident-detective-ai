package dev.shirwac.incidentdetective.planning;

import java.util.Objects;

public record IncidentPlannerResponse(
        IncidentPlanProposal proposal,
        IncidentPlannerReceipt receipt
) {
    public IncidentPlannerResponse {
        Objects.requireNonNull(proposal, "proposal must not be null");
        Objects.requireNonNull(receipt, "receipt must not be null");
    }
}
