package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("rag")
@RequestMapping("/api/v1/incident-lab")
@Tag(name = "Incident Lab")
public final class IncidentFollowUpController {

    private final IncidentFollowUpService service;

    public IncidentFollowUpController(IncidentFollowUpService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/follow-ups",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Ask about one completed synthetic incident",
            description = "Loads a server-owned frozen receipt. Live mode uses Gemini only to select report sections; Java authors and verifies every returned fact. Replay mode accepts only the returned provider-free suggestions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bounded follow-up returned"),
            @ApiResponse(responseCode = "400", description = "Invalid body or live-AI confirmation missing", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Run reference unknown or expired", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict or prior uncertain attempt", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))),
            @ApiResponse(responseCode = "429", description = "Live-AI allowance reached", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))),
            @ApiResponse(responseCode = "502", description = "Model route or frozen receipt failed verification", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))),
            @ApiResponse(responseCode = "503", description = "Provider, budget store, or snapshot store unavailable", content = @Content(schema = @Schema(implementation = ApiProblemResponse.class)))
    })
    public IncidentFollowUpResponse answer(
            @Valid @RequestBody IncidentFollowUpRequest request
    ) {
        return service.answer(request);
    }
}
