package dev.shirwac.incidentdetective.live;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/live-ai")
@Tag(
        name = "Live AI status",
        description = "Read-only public availability for the bounded Incident Lab journey."
)
public final class LiveAiStatusController {

    private final LiveAiStatusService service;

    public LiveAiStatusController(LiveAiStatusService service) {
        this.service = service;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Read live Incident Lab availability",
            description = "Checks configuration and the application budget without probing the model provider. POST admission remains authoritative."
    )
    public LiveAiStatusResponse status() {
        return service.describe();
    }
}
