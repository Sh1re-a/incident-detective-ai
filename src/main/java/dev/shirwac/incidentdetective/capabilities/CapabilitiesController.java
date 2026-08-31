package dev.shirwac.incidentdetective.capabilities;

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
@RequestMapping("/api/v1/capabilities")
@Tag(
        name = "Capabilities",
        description = "Describes the backend's active, safe and versioned capability boundary."
)
public final class CapabilitiesController {

    private final CapabilitiesService capabilities;

    public CapabilitiesController(CapabilitiesService capabilities) {
        this.capabilities = capabilities;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Describe backend capabilities",
            description = "Returns synthetic-data boundaries, investigation modes, read-only "
                    + "tools, enforced live-AI budgets, active retrieval configuration and "
                    + "prompt-cache policy. Credentials are never returned."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Versioned backend capability contract",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CapabilitiesResponse.class)
            )
    )
    public CapabilitiesResponse describe() {
        return capabilities.describe();
    }
}
