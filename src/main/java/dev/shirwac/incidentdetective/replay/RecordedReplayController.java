package dev.shirwac.incidentdetective.replay;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @PostMapping("/{scenarioId}/runs/recorded-replay")
    @Operation(
            summary = "Run a recorded incident investigation",
            description = "Returns ordered read-only tool events, cited evidence, "
                    + "a deterministic diagnosis and a post-completion comparison. "
                    + "No model runs and no remediation is executed."
    )
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
