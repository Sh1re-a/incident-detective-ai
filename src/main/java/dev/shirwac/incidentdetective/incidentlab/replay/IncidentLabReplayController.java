package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incident-lab")
@Tag(
        name = "Incident Lab recorded replay",
        description = "Reports and plays one optional versioned Incident Lab capture without live dependencies."
)
public final class IncidentLabReplayController {

    private final IncidentLabReplayService service;

    public IncidentLabReplayController(IncidentLabReplayService service) {
        this.service = service;
    }

    @GetMapping(
            value = "/recorded-replay",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Read recorded Incident Lab availability",
            description = "Always provider-, database-, and quota-free. The packaged capture is available by default and can be disabled by clearing its resource configuration."
    )
    public IncidentLabReplayAvailabilityResponse availability() {
        return service.availability();
    }

    @PostMapping(
            value = "/runs/recorded-replay",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Play the recorded Incident Lab run",
            description = "Returns a versioned historical plan and run. No provider, ADK runner, database, embedding, vector search, quota, or action executes in this request."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recorded playback returned",
                    content = @Content(schema = @Schema(
                            implementation = IncidentLabReplayResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "No verified golden recording is configured",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public IncidentLabReplayResponse play() {
        return service.play();
    }
}
