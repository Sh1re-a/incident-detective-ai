package dev.shirwac.incidentdetective.planning;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

/** Deterministic Java decision over one untrusted model proposal. */
public record IncidentPlanValidationResult(
        IncidentPlanDecision decision,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        IncidentPlan plan,
        List<IncidentPlanAdjustmentCode> adjustments,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        IncidentPlanRejection rejection
) {
    public IncidentPlanValidationResult {
        Objects.requireNonNull(decision, "decision must not be null");
        adjustments = List.copyOf(Objects.requireNonNull(
                adjustments,
                "adjustments must not be null"
        ));
        boolean rejected = decision == IncidentPlanDecision.REJECTED;
        if (rejected
                && (plan != null || rejection == null || !adjustments.isEmpty())) {
            throw new IllegalArgumentException(
                    "rejected results require only a rejection"
            );
        }
        if (!rejected && (plan == null || rejection != null)) {
            throw new IllegalArgumentException(
                    "accepted results require only a plan"
            );
        }
        if (decision == IncidentPlanDecision.APPROVED
                && !adjustments.isEmpty()) {
            throw new IllegalArgumentException(
                    "approved results must not contain adjustments"
            );
        }
        if (decision == IncidentPlanDecision.NARROWED
                && adjustments.isEmpty()) {
            throw new IllegalArgumentException(
                    "narrowed results require at least one adjustment"
            );
        }
    }

    public static IncidentPlanValidationResult accepted(
            IncidentPlan plan,
            List<IncidentPlanAdjustmentCode> adjustments
    ) {
        List<IncidentPlanAdjustmentCode> copy = List.copyOf(adjustments);
        return new IncidentPlanValidationResult(
                copy.isEmpty()
                        ? IncidentPlanDecision.APPROVED
                        : IncidentPlanDecision.NARROWED,
                Objects.requireNonNull(plan),
                copy,
                null
        );
    }

    public static IncidentPlanValidationResult rejected(
            IncidentPlanRejectionCode code,
            String safeMessage
    ) {
        return new IncidentPlanValidationResult(
                IncidentPlanDecision.REJECTED,
                null,
                List.of(),
                new IncidentPlanRejection(code, safeMessage)
        );
    }

    public boolean accepted() {
        return decision != IncidentPlanDecision.REJECTED;
    }
}
