package dev.shirwac.incidentdetective.incidentlab;

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
@Tag(
        name = "Incident Lab",
        description = "Plans and investigates one bounded synthetic incident."
)
public final class IncidentLabController {

    private final IncidentLabService service;

    public IncidentLabController(IncidentLabService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/plans",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Propose and validate a synthetic incident plan",
            description = "Safety runs before Gemini. Java then validates the structured proposal; no fallback plan is created."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan decision returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid body or missing live-AI confirmation",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Live-AI or provider limit reached",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Planner provider or response contract failed",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Planner provider is disabled or not configured",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "Planner provider timed out",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            )
    })
    public IncidentLabPlanResponse createPlan(
            @Valid @RequestBody IncidentLabPlanRequest request
    ) {
        return service.createPlan(request);
    }

    @PostMapping(
            value = "/runs",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run one approved synthetic incident",
            description = "Java revalidates the canonical v1 plan, generates one request-local case, evaluates the alarm, and invokes ADK only if the alarm fires."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Run receipt returned"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Plan is invalid or live AI was not confirmed",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Live-AI or provider limit reached",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "ADK provider failed before a post-run receipt could be completed",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "ADK, Gemini, or retrieval is unavailable",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "The ADK investigation timed out",
                    content = @Content(schema = @Schema(implementation = ApiProblemResponse.class))
            )
    })
    public IncidentLabRunResponse run(
            @Valid @RequestBody IncidentLabRunRequest request
    ) {
        return service.run(request);
    }
}
