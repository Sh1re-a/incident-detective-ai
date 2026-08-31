package dev.shirwac.incidentdetective.proof;

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
@RequestMapping("/api/v1/proof/evals")
@Tag(
        name = "AI proof",
        description = "Read-only aggregate evidence for the published RAG "
                + "retrieval evaluation. This endpoint never starts an eval."
)
public final class ProofEvalController {

    private final ProofEvalSummaryService summaries;

    public ProofEvalController(ProofEvalSummaryService summaries) {
        this.summaries = summaries;
    }

    @GetMapping(
            path = "/retrieval",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Get published retrieval eval proof",
            description = "Returns a versioned aggregate of the frozen pgvector and "
                    + "embedding retrieval eval. No query, chunk text, or eval trigger "
                    + "is exposed."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Published retrieval eval aggregate",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(
                            implementation = RetrievalEvalProofResponse.class
                    )
            )
    )
    public RetrievalEvalProofResponse retrieval() {
        return summaries.retrieval();
    }

}
