package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.live.LiveInvestigationResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record GeneratedCaseRunResult(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = CONTRACT_VERSION
        )
        String contractVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GenerationMetadata generation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LiveInvestigationResult investigation
) {
    public static final String CONTRACT_VERSION = "generated-live-run-v1";

    public record GenerationMetadata(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String generatorVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            long seed,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            GeneratedEvidenceMode evidenceMode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            GeneratedNoiseLevel noiseLevel
    ) {
    }
}
