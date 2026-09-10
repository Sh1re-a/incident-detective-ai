package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge/documents")
@Tag(
        name = "Nordly knowledge documents",
        description = "Exposes the synthetic document library and its RAG boundary."
)
public final class KnowledgeDocumentLibraryController {

    private final NordlyResourceCatalog resources;

    public KnowledgeDocumentLibraryController(NordlyResourceCatalog resources) {
        this.resources = resources;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Browse the synthetic Nordly knowledge library",
            description = "Returns every synthetic document and chunk, including "
                    + "quarantined examples, with an explicit RAG eligibility decision. "
                    + "No embedding, vector search, model call or write action runs."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Versioned read-only synthetic document library",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = KnowledgeDocumentLibraryResponse.class
                    )
            )
    )
    public KnowledgeDocumentLibraryResponse list() {
        return KnowledgeDocumentLibraryResponse.from(
                resources.knowledgeManifest()
        );
    }
}
