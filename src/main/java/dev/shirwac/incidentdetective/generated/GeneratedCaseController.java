package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/generated-cases")
@Tag(
        name = "Generated live investigation",
        description = "Generates one reproducible synthetic case and investigates it with Gemini."
)
public final class GeneratedCaseController {

    private final GeneratedCaseInvestigationService service;

    public GeneratedCaseController(GeneratedCaseInvestigationService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/runs/live-ai",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Generate and investigate a synthetic payment-timeout case",
            description = "A deterministic Java generator creates request-local signals and "
                    + "a hidden reference answer. Gemini may inspect signals only through bounded "
                    + "read-only tools. Java then verifies the structured diagnosis."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Generated investigation completed or returned verification findings",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GeneratedCaseRunResult.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid generator controls or live AI not confirmed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Request body is not application/json",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Rolling, concurrent, daily, or provider limit reached",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Gemini returned an upstream or contract failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Live AI or retrieval dependency is not configured",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "The bounded investigation timed out",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            )
    })
    public GeneratedCaseRunResult investigate(
            @RequestBody GeneratedCaseLiveRequest request
    ) {
        return service.investigate(request);
    }
}
