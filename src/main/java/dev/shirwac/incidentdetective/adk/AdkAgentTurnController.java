package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
@Tag(
        name = "Nordly ADK agent",
        description = "One bounded Google ADK turn over synthetic Nordly evidence."
)
public final class AdkAgentTurnController {

    private final AdkAgentTurnService service;
    private final AdkAgentTurnPublicProjector publicProjector =
            new AdkAgentTurnPublicProjector();

    public AdkAgentTurnController(AdkAgentTurnService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/turns",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run one controlled Nordly incident-agent turn",
            description = "Java blocks unsafe input before the provider. A real Google ADK "
                    + "Runner then creates an in-memory session, lets Gemini call one bounded "
                    + "read-only evidence tool, and records the tool trajectory. Java verifies "
                    + "the structured diagnosis before it may be returned. Model-authored "
                    + "prose and the private expected answer are withheld. Events are a "
                    + "post-run receipt, not hidden reasoning or streaming."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Turn completed, was verification-blocked, or was safety-blocked",
                    content = @Content(schema = @Schema(
                            implementation = AdkAgentTurnResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or missing explicit live-AI confirmation",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Public live-AI budget is busy or exhausted",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Provider, embedding, or model tool output failed validation",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "ADK, live AI, credentials, or retrieval is unavailable",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "The bounded ADK turn or model provider timed out",
                    content = @Content(schema = @Schema(
                            implementation = ApiProblemResponse.class
                    ))
            )
    })
    public AdkAgentTurnResponse run(
            @Valid @RequestBody AdkAgentTurnRequest request
    ) {
        return publicProjector.project(service.run(request));
    }
}
