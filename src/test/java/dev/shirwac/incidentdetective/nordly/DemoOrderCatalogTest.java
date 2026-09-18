package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DemoOrderCatalogTest {

    @Autowired
    private DemoOrderCatalog catalog;

    @Test
    void loadsThreeSafeVersionedSyntheticOrders() throws Exception {
        assertEquals("nordly-demo-orders-v1", catalog.catalogVersion());
        assertEquals("2026-09-15T10:00:00Z", catalog.snapshotAt().toString());
        assertTrue(catalog.truthLabel().contains("SYNTETISKA DEMOORDER"));
        assertTrue(catalog.truthLabelEn().contains("SYNTHETIC DEMO ORDERS"));
        assertEquals(
                List.of("NORD-2048", "NORD-2051", "NORD-2057"),
                catalog.orders().stream()
                        .map(DemoOrderSnapshot::orderId)
                        .toList()
        );
        assertEquals(
                catalog.orders().size(),
                catalog.orders().stream()
                        .map(DemoOrderSnapshot::evidenceId)
                        .distinct()
                        .count()
        );
        assertTrue(catalog.orders().stream().allMatch(order ->
                !order.updatedAt().isBefore(order.createdAt())
                        && !order.updatedAt().isAfter(catalog.snapshotAt())
                        && !order.estimatedDeliveryThrough().isBefore(
                        order.estimatedDeliveryFrom()
                )
        ));

        DemoOrderSnapshot order = catalog.findById("NORD-2048");
        assertEquals(
                "Lumi bordslampa och Fjord ullpläd",
                order.itemSummarySv()
        );
        assertEquals("packing", order.statusCode());
        assertEquals("Packas", order.statusSv());
        assertEquals("Packing", order.statusEn());
        assertEquals("nordly-demo-order-2048-snapshot", order.evidenceId());

        String rawResource = new ClassPathResource(DemoOrderCatalog.RESOURCE)
                .getContentAsString(StandardCharsets.UTF_8)
                .toLowerCase(java.util.Locale.ROOT);
        assertFalse(rawResource.contains("customer_name"));
        assertFalse(rawResource.contains("email"));
        assertFalse(rawResource.contains("address"));
        assertFalse(rawResource.contains("card_number"));
    }
}
