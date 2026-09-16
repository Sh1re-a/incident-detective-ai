package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/demo-orders")
@Tag(
        name = "Nordly demo orders",
        description = "Reads completely synthetic order snapshots without AI or writes."
)
public final class DemoOrderController {

    private static final List<String> LIMITATIONS = List.of(
            "These order snapshots are synthetic and are not connected to a real commerce system.",
            "The endpoint cannot create, cancel, refund or update an order.",
            "No model, embedding, vector search or write tool runs in this request."
    );

    private final DemoOrderCatalog catalog;

    public DemoOrderController(DemoOrderCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List synthetic Nordly demo orders",
            description = "Returns the complete small versioned demo-order catalog. "
                    + "Exactly one backend read is recorded; no AI or write runs."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Versioned read-only synthetic order catalog",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DemoOrderCatalogResponse.class)
            )
    )
    public DemoOrderCatalogResponse list() {
        List<DemoOrderSnapshot> orders = catalog.orders();
        return new DemoOrderCatalogResponse(
                DemoOrderCatalogResponse.CONTRACT_VERSION,
                DemoOrderCatalogResponse.MODE,
                catalog.truthLabel(),
                catalog.truthLabelEn(),
                catalog.catalogVersion(),
                catalog.snapshotAt(),
                true,
                orders.size(),
                orders,
                DemoOrderReadReceipt.list(orders.size()),
                LIMITATIONS
        );
    }

    @GetMapping(
            value = "/{orderId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Get one synthetic Nordly demo order",
            description = "Looks up one exact synthetic order ID in the versioned "
                    + "backend catalog. No customer data, AI or write is available."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "One read-only synthetic order snapshot",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = DemoOrderLookupResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The synthetic demo order does not exist",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ApiProblemResponse.class)
                    )
            )
    })
    public DemoOrderLookupResponse get(
            @Parameter(required = true, example = "NORD-2048")
            @PathVariable String orderId
    ) {
        return new DemoOrderLookupResponse(
                DemoOrderLookupResponse.CONTRACT_VERSION,
                DemoOrderLookupResponse.MODE,
                catalog.truthLabel(),
                catalog.truthLabelEn(),
                catalog.catalogVersion(),
                catalog.snapshotAt(),
                true,
                catalog.findById(orderId),
                DemoOrderReadReceipt.lookup(),
                LIMITATIONS
        );
    }
}
