package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scenarios")
@Tag(
        name = "Live AI investigation",
        description = "Runs bounded Gemini investigations over synthetic incident data."
)
public final class LiveInvestigationController {

    private final LiveInvestigationService service;

    public LiveInvestigationController(LiveInvestigationService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/{scenarioId}/runs/live-ai",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run a live Gemini incident investigation",
            description = "Requires explicit confirmation. Gemini selects bounded read-only "
                    + "tools, returns a structured diagnosis, and Java verifies it against "
                    + "hidden synthetic ground truth. No remediation is executed."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Live investigation completed or returned verification findings",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = LiveInvestigationResult.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body or live model call not confirmed",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Live AI confirmation required",
                                      "status": 400,
                                      "detail": "Set confirm_live_ai to true to allow this model call.",
                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                      "code": "LIVE_AI_CONFIRMATION_REQUIRED"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Synthetic scenario not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Investigation scenario not found",
                                      "status": 404,
                                      "detail": "Investigation scenario not found: unknown-scenario",
                                      "instance": "/api/v1/scenarios/unknown-scenario/runs/live-ai",
                                      "code": "SCENARIO_NOT_FOUND"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Request body is not application/json",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Unsupported media type",
                                      "status": 415,
                                      "detail": "Send the request body as application/json.",
                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                      "code": "UNSUPPORTED_MEDIA_TYPE"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Application admission, global daily allowance, or Gemini "
                            + "provider rate limit reached. Application limits include Retry-After; "
                            + "the provider SDK does not expose a trustworthy value.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "application_admission_limit",
                                            value = """
                                                    {
                                                      "title": "Live AI is busy",
                                                      "status": 429,
                                                      "detail": "Another live investigation is already running.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "LIVE_AI_RATE_LIMITED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "daily_live_allowance",
                                            value = """
                                                    {
                                                      "title": "Daily live AI limit reached",
                                                      "status": 429,
                                                      "detail": "The public demo has used its live AI allowance for today.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "LIVE_AI_DAILY_LIMIT_REACHED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "provider_rate_limit",
                                            value = """
                                                    {
                                                      "title": "Model provider rate limited",
                                                      "status": 429,
                                                      "detail": "Gemini temporarily rejected the bounded model request.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "MODEL_PROVIDER_RATE_LIMITED"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "Model or embedding provider failed, or returned "
                            + "output outside a strict contract",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "model_provider_error",
                                            value = """
                                                    {
                                                      "title": "Model provider failed",
                                                      "status": 502,
                                                      "detail": "Gemini could not complete the bounded model request.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "MODEL_PROVIDER_ERROR"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "rag_embedding_provider_error",
                                            value = """
                                                    {
                                                      "title": "Runbook embedding provider failed",
                                                      "status": 502,
                                                      "detail": "The embedding provider could not complete the bounded retrieval request.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "RAG_EMBEDDING_PROVIDER_ERROR"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "rag_embedding_response_invalid",
                                            value = """
                                                    {
                                                      "title": "Runbook embedding response rejected",
                                                      "status": 502,
                                                      "detail": "The embedding provider returned output outside the allowed contract.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "RAG_EMBEDDING_RESPONSE_INVALID"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Live AI or its pgvector retrieval dependency is unavailable",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "live_ai_disabled",
                                            value = """
                                                    {
                                                      "title": "Live AI unavailable",
                                                      "status": 503,
                                                      "detail": "Live AI is disabled by server configuration.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "LIVE_AI_DISABLED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "rag_index_not_ready",
                                            value = """
                                                    {
                                                      "title": "Runbook retrieval unavailable",
                                                      "status": 503,
                                                      "detail": "The pgvector runbook index is not ready for live retrieval.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "RAG_INDEX_NOT_READY"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "rag_embedding_not_configured",
                                            value = """
                                                    {
                                                      "title": "Runbook retrieval unavailable",
                                                      "status": 503,
                                                      "detail": "The embedding provider is not configured for live retrieval.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "RAG_EMBEDDING_NOT_CONFIGURED"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "rag_database_unavailable",
                                            value = """
                                                    {
                                                      "title": "Runbook retrieval unavailable",
                                                      "status": 503,
                                                      "detail": "The pgvector runbook store is temporarily unavailable.",
                                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                                      "code": "RAG_DATABASE_UNAVAILABLE"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "Provider or investigation deadline exceeded",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "title": "Live investigation timed out",
                                      "status": 504,
                                      "detail": "The live investigation exceeded its 45 second deadline.",
                                      "instance": "/api/v1/scenarios/checkout-orders-at-risk-v1/runs/live-ai",
                                      "code": "LIVE_INVESTIGATION_TIMEOUT"
                                    }
                                    """)
                    )
            )
    })
    public LiveInvestigationResult runLiveInvestigation(
            @Parameter(
                    description = "Synthetic scenario ID",
                    example = "checkout-orders-at-risk-v1"
            )
            @PathVariable String scenarioId,
            @RequestBody LiveInvestigationRequest request
    ) {
        return service.investigate(scenarioId, request);
    }
}
