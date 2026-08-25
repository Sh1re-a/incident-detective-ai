package dev.shirwac.incidentdetective.live;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(
        name = "Live AI investigation",
        description = "Runs bounded Gemini investigations over synthetic incident data."
)
public final class LiveInvestigationController {

    private final LiveInvestigationService service;

    public LiveInvestigationController(LiveInvestigationService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/{scenarioId}/runs/live-ai",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run a live Gemini incident investigation",
            description = "Requires explicit confirmation. Gemini selects bounded read-only "
                    + "tools, returns a structured diagnosis, and Java verifies it against "
                    + "hidden synthetic ground truth. No remediation is executed."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Live investigation completed or returned verification findings",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LiveInvestigationResult.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Live model call was not explicitly confirmed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Synthetic scenario not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Provider response or model tool arguments were invalid",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Live AI is disabled or not configured",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "Provider or investigation deadline exceeded",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public LiveInvestigationResult runLiveInvestigation(
            @Parameter(
                    description = "Synthetic scenario ID",
                    example = "checkout-orders-at-risk-v1"
            )
            @PathVariable String scenarioId,
            @RequestBody LiveInvestigationRequest request
    ) {
        return service.investigate(scenarioId, request);
    }
}
