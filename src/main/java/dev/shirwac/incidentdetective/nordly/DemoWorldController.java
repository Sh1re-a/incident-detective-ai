package dev.shirwac.incidentdetective.nordly;

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
@RequestMapping("/api/v1/demo-world")
@Tag(
        name = "Nordly demo world",
        description = "Describes the fictional company, services and curated questions."
)
public final class DemoWorldController {

    private final NordlyResourceCatalog resources;

    public DemoWorldController(NordlyResourceCatalog resources) {
        this.resources = resources;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get the fictional Nordly demo world",
            description = "Returns synthetic company context and corpus counts. "
                    + "No AI, vector search or provider call is started."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Versioned synthetic demo-world contract",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DemoWorldResponse.class)
            )
    )
    public DemoWorldResponse getWorld() {
        return resources.world();
    }
}
