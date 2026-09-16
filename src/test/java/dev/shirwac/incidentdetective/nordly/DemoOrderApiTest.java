package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoOrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsOnlyBackendOwnedSyntheticOrderSnapshots() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/demo-orders"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version")
                        .value(DemoOrderCatalogResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.mode")
                        .value(DemoOrderCatalogResponse.MODE))
                .andExpect(jsonPath("$.truth_label")
                        .value(containsString("INGEN KUNDDATA")))
                .andExpect(jsonPath("$.truth_label_en")
                        .value(containsString("NO CUSTOMER DATA")))
                .andExpect(jsonPath("$.catalog_version")
                        .value("nordly-demo-orders-v1"))
                .andExpect(jsonPath("$.snapshot_at")
                        .value("2026-09-15T10:00:00Z"))
                .andExpect(jsonPath("$.synthetic_only").value(true))
                .andExpect(jsonPath("$.order_count").value(3))
                .andExpect(jsonPath("$.orders[*].order_id").value(contains(
                        "NORD-2048",
                        "NORD-2051",
                        "NORD-2057"
                )))
                .andExpect(jsonPath("$.action_receipt.operation")
                        .value("list_demo_orders"))
                .andExpect(jsonPath("$.action_receipt.read_operations").value(1))
                .andExpect(jsonPath("$.action_receipt.records_returned").value(3))
                .andExpect(jsonPath("$.action_receipt.write_operations").value(0))
                .andExpect(jsonPath("$.action_receipt.ai_calls").value(0))
                .andExpect(jsonPath("$.action_receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.action_receipt.action_executed")
                        .value(false))
                .andReturn();

        assertContainsNoCustomerSecrets(result.getResponse().getContentAsString());
    }

    @Test
    void returnsNord2048WithLocalizedBackendFactsAndProvenance()
            throws Exception {
        MvcResult result = mockMvc.perform(get(
                        "/api/v1/demo-orders/{orderId}",
                        "NORD-2048"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version")
                        .value(DemoOrderLookupResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.mode")
                        .value(DemoOrderLookupResponse.MODE))
                .andExpect(jsonPath("$.snapshot_at")
                        .value("2026-09-15T10:00:00Z"))
                .andExpect(jsonPath("$.synthetic_only").value(true))
                .andExpect(jsonPath("$.order.order_id").value("NORD-2048"))
                .andExpect(jsonPath("$.order.status_code").value("packing"))
                .andExpect(jsonPath("$.order.status_sv").value("Packas"))
                .andExpect(jsonPath("$.order.status_en").value("Packing"))
                .andExpect(jsonPath("$.order.summary_sv")
                        .value(containsString("syntetiska produkter")))
                .andExpect(jsonPath("$.order.summary_en")
                        .value(containsString("synthetic products")))
                .andExpect(jsonPath("$.order.next_step_sv")
                        .value(containsString("Nästa registrerade steg")))
                .andExpect(jsonPath("$.order.next_step_en")
                        .value(containsString("next recorded step")))
                .andExpect(jsonPath("$.order.created_at")
                        .value("2026-09-12T08:14:00Z"))
                .andExpect(jsonPath("$.order.updated_at")
                        .value("2026-09-12T10:42:00Z"))
                .andExpect(jsonPath("$.order.source_ref")
                        .value("demo/nordly-demo-orders-v1#NORD-2048"))
                .andExpect(jsonPath("$.order.evidence_id")
                        .value("nordly-demo-order-2048-snapshot"))
                .andExpect(jsonPath("$.action_receipt.operation")
                        .value("get_demo_order"))
                .andExpect(jsonPath("$.action_receipt.read_operations").value(1))
                .andExpect(jsonPath("$.action_receipt.records_returned").value(1))
                .andExpect(jsonPath("$.action_receipt.write_operations").value(0))
                .andExpect(jsonPath("$.action_receipt.ai_calls").value(0))
                .andReturn();

        assertContainsNoCustomerSecrets(result.getResponse().getContentAsString());
    }

    @Test
    void returnsStructuredProblemForAnUnknownSyntheticOrder() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/demo-orders/{orderId}",
                        "NORD-9999"
                ))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/problem+json"
                ))
                .andExpect(jsonPath("$.title")
                        .value("Nordly demo order not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("DEMO_ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(
                        "Synthetic Nordly demo order not found: NORD-9999"
                ))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/demo-orders/NORD-9999"));
    }

    @Test
    void doesNotServeTheBackingCatalogAsAStaticResource() throws Exception {
        mockMvc.perform(get("/demo/nordly-demo-orders-v1.json"))
                .andExpect(status().isNotFound());
    }

    private void assertContainsNoCustomerSecrets(String responseBody) {
        String normalized = responseBody.toLowerCase(java.util.Locale.ROOT);
        assertFalse(normalized.contains("customer_name"));
        assertFalse(normalized.contains("email"));
        assertFalse(normalized.contains("address"));
        assertFalse(normalized.contains("card_number"));
    }
}
