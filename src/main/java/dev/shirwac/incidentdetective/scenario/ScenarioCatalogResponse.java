package dev.shirwac.incidentdetective.scenario;

import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ScenarioCatalogResponse(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = CONTRACT_VERSION
        )
        String contractVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean syntheticOnly,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = SOURCE
        )
        String source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String truthLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Scenario> scenarios
) {

    public static final String CONTRACT_VERSION = "scenario-catalog-v2";
    public static final String SOURCE = "versioned_recorded_fixtures";
    public static final String TRUTH_LABEL =
            "Synthetic scenario summaries from versioned recorded fixtures.";

    public ScenarioCatalogResponse {
        scenarios = List.copyOf(scenarios);
    }

    public static ScenarioCatalogResponse recordedFixtures(
            List<Scenario> scenarios
    ) {
        return new ScenarioCatalogResponse(
                CONTRACT_VERSION,
                true,
                SOURCE,
                TRUTH_LABEL,
                scenarios
        );
    }
}
