package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "The versioned, completely synthetic Nordly demo-order catalog.")
public record DemoOrderCatalogResponse(
        String contractVersion,
        String mode,
        String truthLabel,
        String truthLabelEn,
        String catalogVersion,
        Instant snapshotAt,
        boolean syntheticOnly,
        int orderCount,
        List<DemoOrderSnapshot> orders,
        DemoOrderReadReceipt actionReceipt,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "nordly-demo-order-catalog-v1";
    public static final String MODE = "read_only_synthetic_order_catalog";

    public DemoOrderCatalogResponse {
        orders = List.copyOf(orders);
        limitations = List.copyOf(limitations);
    }
}
