package dev.shirwac.incidentdetective.replay;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
public final class RecordedReplayController {

    private final RecordedReplayService replayService;

    public RecordedReplayController(RecordedReplayService replayService) {
        this.replayService = replayService;
    }

    @PostMapping("/{scenarioId}/runs/recorded-replay")
    public RecordedReplayResult runRecordedReplay(@PathVariable String scenarioId) {
        return replayService.play(scenarioId);
    }
}
