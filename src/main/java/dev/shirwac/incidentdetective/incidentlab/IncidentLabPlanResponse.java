package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.planning.IncidentPlanProposal;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidationResult;
import dev.shirwac.incidentdetective.planning.IncidentPlannerReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record IncidentLabPlanResponse(
        String contractVersion,
        String outcome,
        String delivery,
        String truthLabel,
        SafetyReceipt safety,
        @Schema(nullable = true)
        IncidentPlanProposal proposal,
        @Schema(nullable = true)
        IncidentPlanValidationResult javaValidation,
        @Schema(nullable = true)
        IncidentPlannerReceipt providerReceipt,
        List<String> limitations
) {

    public static final String CONTRACT_VERSION = "incident-lab-plan-v1";
    public static final String DELIVERY = "synchronous_post_run";
    public static final String TRUTH_LABEL =
            "Real Gemini proposal — deterministic Java validation for a synthetic incident only.";
    public static final String BLOCKED_TRUTH_LABEL =
            "Deterministic Java safety decision — Gemini was not called.";

    public IncidentLabPlanResponse {
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public record SafetyReceipt(
            String decision,
            String reasonCode,
            String summarySv,
            String summaryEn
    ) {
    }
}
