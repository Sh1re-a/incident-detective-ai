package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/questions")
@Tag(
        name = "Nordly knowledge replay",
        description = "Serves bounded, provider-free knowledge demonstrations."
)
public final class KnowledgeReplayController {

    private final NordlyResourceCatalog resources;

    public KnowledgeReplayController(NordlyResourceCatalog resources) {
        this.resources = resources;
    }

    @PostMapping(
            path = "/{questionId}/runs/recorded-replay",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Replay a curated Nordly knowledge answer",
            description = "Returns a validated, recorded retrieval snapshot. "
                    + "No model, embedding, vector search, write tool or action runs."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Versioned provider-free knowledge replay",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = KnowledgeReplayResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "The curated question ID does not exist",
            content = @Content(
                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    schema = @Schema(implementation = ApiProblemResponse.class)
            )
    )
    public KnowledgeReplayResponse replay(
            @Parameter(required = true)
            @PathVariable String questionId
    ) {
        return resources.replay(questionId);
    }
}
