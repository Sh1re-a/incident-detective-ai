package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/demo-customer/chat")
@Tag(
        name = "Nordly controlled customer chat",
        description = "Runs one stateless, read-only chat turn for the same "
                + "synthetic Nordly customer and backend-owned current order."
)
public final class DemoCustomerChatController {

    private final DemoCustomerChatService service;

    public DemoCustomerChatController(DemoCustomerChatService service) {
        this.service = service;
    }

    @PostMapping(
            path = "/turns",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Run one controlled Nordly customer-chat turn",
            description = "The request accepts only a message, locale and an "
                    + "explicit live-AI confirmation. The server selects the "
                    + "fixed synthetic customer and NORD-2051; it exposes no "
                    + "customer-selected ID, session memory or write tool. "
                    + "Returned events are completed system receipts, never "
                    + "private model reasoning."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "A grounded answer, authority stop, refusal, abstention or controlled failure",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = DemoCustomerChatTurnResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The small public request contract was rejected",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "The bounded public live-AI allowance is busy or exhausted",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The shared live-AI budget guard is unavailable. "
                            + "Controlled RAG and provider failures otherwise return "
                            + "200 with outcome unavailable and a stable error code.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            )
    })
    public DemoCustomerChatTurnResponse turn(
            @Valid @RequestBody DemoCustomerChatTurnRequest request
    ) {
        return service.run(request);
    }
}
