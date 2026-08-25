package dev.shirwac.incidentdetective.replay;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(
        name = "Recorded replay",
        description = "Runs deterministic investigations over synthetic incident data."
)
public final class RecordedReplayController {

    private final RecordedReplayService replayService;

    public RecordedReplayController(RecordedReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping(
            value = "/{scenarioId}/runs/recorded-replay",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run a recorded incident investigation",
            description = "Returns ordered read-only tool events, cited evidence, "
                    + "a deterministic diagnosis and a post-completion comparison. "
                    + "No model runs and no remediation is executed."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Recorded replay completed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RecordedReplayResult.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Recorded scenario not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public RecordedReplayResult runRecordedReplay(
            @Parameter(
                    description = "Synthetic scenario ID",
                    example = "checkout-orders-at-risk-v1"
            )
            @PathVariable String scenarioId
    ) {
        return replayService.play(scenarioId);
    }
}
