package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "One read-only lookup from the synthetic Nordly demo-order catalog.")
public record DemoOrderLookupResponse(
        String contractVersion,
        String mode,
        String truthLabel,
        String truthLabelEn,
        String catalogVersion,
        Instant snapshotAt,
        boolean syntheticOnly,
        DemoOrderSnapshot order,
        DemoOrderReadReceipt actionReceipt,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "nordly-demo-order-lookup-v1";
    public static final String MODE = "read_only_synthetic_order_lookup";

    public DemoOrderLookupResponse {
        limitations = List.copyOf(limitations);
    }
}
