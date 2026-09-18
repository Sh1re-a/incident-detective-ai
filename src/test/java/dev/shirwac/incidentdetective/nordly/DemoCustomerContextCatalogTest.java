package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DemoCustomerContextCatalogTest {

    @Autowired
    private DemoCustomerContextCatalog context;

    @Test
    void bindsEveryTurnToOneNamedSyntheticCustomerAndShippedOrder()
            throws Exception {
        assertEquals("nordly-demo-customer-v1", context.contextVersion());
        assertEquals("public-demo-customer", context.contextId());
        assertEquals("Shirwac \"Shirre\" Abib", context.customerDisplayName());
        assertEquals("Shirre", context.customerPreferredName());
        assertEquals(
                "demo/nordly-demo-customer-v1#current-order",
                context.sourceRef()
        );
        assertEquals(
                "nordly-demo-customer-current-order-context",
                context.evidenceId()
        );
        assertEquals("NORD-2051", context.currentOrder().orderId());
        assertEquals("shipped", context.currentOrder().statusCode());
        assertTrue(context.currentOrder().summarySv().contains("syntetisk"));

        String resource = new ClassPathResource(
                DemoCustomerContextCatalog.RESOURCE
        ).getContentAsString(StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        assertFalse(resource.contains("email"));
        assertFalse(resource.contains("address"));
        assertFalse(resource.contains("card_number"));
        assertFalse(resource.contains("session"));
    }
}
