package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import io.swagger.v3.oas.annotations.media.Schema;

/** Generic evidence for the one model call that produced a proposal. */
public record IncidentPlannerReceipt(
        String transport,
        String model,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String providerResponseId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        ModelTokenUsage tokenUsage,
        long latencyMs
) {
    public IncidentPlannerReceipt {
        if (transport == null || transport.isBlank()) {
            throw new IllegalArgumentException("transport must not be blank");
        }
        transport = transport.strip();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        model = model.strip();
        providerResponseId = providerResponseId == null
                || providerResponseId.isBlank()
                ? null
                : providerResponseId.strip();
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
    }
}
