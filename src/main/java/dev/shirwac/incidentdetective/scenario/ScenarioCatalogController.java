package dev.shirwac.incidentdetective.scenario;

import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(
        name = "Scenarios",
        description = "Lists safe public summaries of synthetic incident scenarios."
)
public final class ScenarioCatalogController {

    private final InvestigationScenarioCatalog scenarios;

    public ScenarioCatalogController(InvestigationScenarioCatalog scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List synthetic incident scenarios",
            description = "Returns scenario context only. Evidence, recorded answers and "
                    + "hidden ground truth are not included."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Synthetic scenario summaries",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ScenarioCatalogResponse.class)
            )
    )
    public ScenarioCatalogResponse listScenarios() {
        return new ScenarioCatalogResponse(scenarios.findAll());
    }
}
