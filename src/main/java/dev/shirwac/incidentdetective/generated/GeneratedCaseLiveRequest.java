package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.live.LiveInvestigationRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/** Bounded generator controls plus explicit permission for one Gemini run. */
public record GeneratedCaseLiveRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        Long seed,
        @Schema(
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                nullable = true,
                description = "Optional bounded Nordly incident family. Defaults to payment_timeout.",
                example = "catalog_cache_invalidation"
        )
        GeneratedIncidentFamily incidentFamily,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "diagnostic")
        GeneratedEvidenceMode evidenceMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "low")
        GeneratedNoiseLevel noiseLevel,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Explicit confirmation that this request may call Gemini.",
                example = "true"
        )
        boolean confirmLiveAi
) {
    public GeneratedCaseLiveRequest {
        Objects.requireNonNull(seed, "seed must not be null");
        incidentFamily = incidentFamily == null
                ? GeneratedIncidentFamily.PAYMENT_TIMEOUT
                : incidentFamily;
        Objects.requireNonNull(evidenceMode, "evidenceMode must not be null");
        Objects.requireNonNull(noiseLevel, "noiseLevel must not be null");
    }

    GeneratedCaseRequest generatedCaseRequest() {
        return new GeneratedCaseRequest(
                seed.longValue(),
                incidentFamily,
                evidenceMode,
                noiseLevel
        );
    }

    LiveInvestigationRequest liveRequest() {
        return new LiveInvestigationRequest(confirmLiveAi);
    }
}
