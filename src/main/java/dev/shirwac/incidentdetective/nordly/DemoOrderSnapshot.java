package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "One completely synthetic, read-only Nordly demo order.")
public record DemoOrderSnapshot(
        String orderId,
        String market,
        int itemCount,
        String itemSummarySv,
        String itemSummaryEn,
        Instant createdAt,
        Instant updatedAt,
        LocalDate estimatedDeliveryFrom,
        LocalDate estimatedDeliveryThrough,
        String statusCode,
        String statusSv,
        String statusEn,
        String paymentState,
        String fulfilmentState,
        String summarySv,
        String summaryEn,
        String nextStepSv,
        String nextStepEn,
        String sourceRef,
        String evidenceId
) {
}
