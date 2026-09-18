package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/v1/knowledge/questions")
@Tag(
        name = "Nordly live knowledge RAG",
        description = "Runs a bounded, explicitly confirmed RAG answer over "
                + "synthetic Nordly documents."
)
public final class KnowledgeRagController {

    private final KnowledgeRagService service;

    public KnowledgeRagController(KnowledgeRagService service) {
        this.service = service;
    }

    @PostMapping(
            path = "/runs/rag",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Ask a free-text question using live Nordly RAG",
            description = "Safety is evaluated before any provider call. "
                    + "An allowed and explicitly confirmed request performs "
                    + "at most one query embedding and one Gemini synthesis. "
                    + "Phase events describe completed system work and never "
                    + "contain chain-of-thought."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Controlled answer, refusal, abstention or readiness result",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = KnowledgeRagResponse.class)
            )
    )
    public KnowledgeRagResponse ask(
            @Valid @RequestBody KnowledgeRagRequest request
    ) {
        return service.ask(request);
    }
}
