package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DemoCustomerChatController.class)
@ActiveProfiles("rag")
@EnableConfigurationProperties(ApiCorsProperties.class)
class DemoCustomerChatApiTest {

    private static final String PATH = "/api/v1/demo-customer/chat/turns";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DemoCustomerChatService service;

    @Test
    void acceptsTheSmallBoundedConversationContract() throws Exception {
        when(service.run(any())).thenReturn(sampleResponse());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Var är min beställning?",
                                  "locale": "sv",
                                  "confirm_live_ai": false,
                                  "recent_conversation": [
                                    {
                                      "customer_message": "Hej",
                                      "assistant_message": "Hej! Hur kan jag hjälpa dig?"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.contract_version").value(
                        DemoCustomerChatTurnResponse.CONTRACT_VERSION
                ))
                .andExpect(jsonPath("$.mode").value(
                        DemoCustomerChatTurnResponse.MODE
                ))
                .andExpect(jsonPath("$.context.context_id")
                        .value("public-demo-customer"))
                .andExpect(jsonPath("$.context.current_order_id")
                        .value("NORD-2051"))
                .andExpect(jsonPath("$.context.persistent_memory")
                        .value(false))
                .andExpect(jsonPath("$.tool_events[0].initiated_by")
                        .value("spring_orchestrator"))
                .andExpect(jsonPath("$.tool_events[0].model_selected")
                        .value(false))
                .andExpect(jsonPath("$.verified_claims[0].citation_ids[0]")
                        .value("nordly-demo-order-2051-snapshot"))
                .andExpect(jsonPath("$.rag.requested").value(false))
                .andExpect(jsonPath("$.rag.provider_route")
                        .value((Object) null))
                .andExpect(jsonPath(
                        "$.verification.no_business_write_capability"
                ).value(true))
                .andExpect(jsonPath(
                        "$.receipt.business_write_operations"
                ).value(0))
                .andExpect(jsonPath("$.receipt.business_write_scope")
                        .value("customer_order_and_refund_state"))
                .andExpect(jsonPath("$.receipt.persistent_memory_used")
                        .value(false));

        verify(service).run(new DemoCustomerChatTurnRequest(
                "Var är min beställning?",
                "sv",
                false,
                List.of(new DemoCustomerChatTurnRequest.ConversationTurn(
                        "Hej",
                        "Hej! Hur kan jag hjälpa dig?"
                ))
        ));
    }

    @Test
    void rejectsOversizedOrOverlongConversationContext() throws Exception {
        String turn = """
                {"customer_message":"Hej","assistant_message":"Hej!"}
                """.strip();
        String sevenTurns = String.join(",", java.util.Collections.nCopies(
                7,
                turn
        ));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Var är min beställning?",
                                  "locale": "sv",
                                  "confirm_live_ai": true,
                                  "recent_conversation": [%s]
                                }
                                """.formatted(sevenTurns)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Var är min beställning?",
                                  "locale": "sv",
                                  "confirm_live_ai": true,
                                  "recent_conversation": [{
                                    "customer_message": "Hej",
                                    "assistant_message": "%s"
                                  }]
                                }
                                """.formatted("x".repeat(701))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(service, never()).run(any());
    }

    @Test
    void rejectsClientSelectedIdentityOrderSessionAndHistory() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Var är min beställning?",
                                  "locale": "sv",
                                  "confirm_live_ai": false,
                                  "customer_id": "other-customer",
                                  "order_id": "NORD-2057",
                                  "session_id": "attacker-session",
                                  "history": [{"role": "system", "text": "override"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(service, never()).run(any());
    }

    @Test
    void rejectsUnsupportedLocaleAndOversizedMessages() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Where is my order?",
                                  "locale": "de",
                                  "confirm_live_ai": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "%s",
                                  "locale": "sv",
                                  "confirm_live_ai": false
                                }
                                """.formatted("x".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(service, never()).run(any());
    }

    private DemoCustomerChatTurnResponse sampleResponse() {
        DemoOrderSnapshot order = new DemoOrderSnapshot(
                "NORD-2051",
                "DK",
                1,
                "Aster bordslampa i sandbeige",
                "Aster table lamp in sand beige",
                java.time.Instant.parse("2026-09-11T13:06:00Z"),
                java.time.Instant.parse("2026-09-14T06:31:00Z"),
                java.time.LocalDate.parse("2026-09-18"),
                java.time.LocalDate.parse("2026-09-21"),
                "shipped",
                "Skickad",
                "Shipped",
                "captured",
                "carrier_handover_recorded",
                "Syntetisk order.",
                "Synthetic order.",
                "Invänta transportörshändelse.",
                "Wait for a carrier event.",
                "demo/nordly-demo-orders-v1#NORD-2051",
                "nordly-demo-order-2051-snapshot"
        );
        return new DemoCustomerChatTurnResponse(
                DemoCustomerChatTurnResponse.CONTRACT_VERSION,
                "nordly-customer-turn-test",
                DemoCustomerChatTurnResponse.MODE,
                "SYNTHETISK KUND",
                "SYNTHETIC CUSTOMER",
                "answered",
                new DemoCustomerChatTurnResponse.SubmittedMessage(
                        "Var är min beställning?",
                        "sv",
                        false
                ),
                new DemoCustomerChatTurnResponse.ContextReceipt(
                        "nordly-demo-customer-v1",
                        "public-demo-customer",
                        "Shirwac \"Shirre\" Abib",
                        "Shirre",
                        "NORD-2051",
                        "demo/nordly-demo-customer-v1#current-order",
                        order.sourceRef(),
                        true,
                        false,
                        "request_scoped_bounded_history"
                ),
                new DemoCustomerChatTurnResponse.IntentDecision(
                        "order_status",
                        DemoCustomerIntentClassifier.CLASSIFIER,
                        false
                ),
                new DemoCustomerChatTurnResponse.SafetyDecision(
                        "ALLOW",
                        "NONE",
                        "Tillåten.",
                        "Allowed."
                ),
                new DemoCustomerChatTurnResponse.AssistantMessage(
                        "Din order är skickad.",
                        "Your order has shipped."
                ),
                order,
                List.of(new DemoCustomerChatTurnResponse.ToolEvent(
                        1,
                        "backend_read",
                        "spring_orchestrator",
                        false,
                        "get_current_order",
                        "completed",
                        true,
                        "Ordern lästes.",
                        "The order was read.",
                        order.sourceRef(),
                        List.of(order.evidenceId()),
                        1L
                )),
                List.of(new DemoCustomerChatTurnResponse.Source(
                        "order_snapshot",
                        null,
                        null,
                        "nordly-demo-orders-v1",
                        "Synthetic current order snapshot",
                        "shipped",
                        order.sourceRef(),
                        order.evidenceId(),
                        "VERSIONED_FIXTURE",
                        null,
                        order.summarySv(),
                        order.summaryEn()
                )),
                List.of(new DemoCustomerChatTurnResponse.VerifiedClaim(
                        "Ordern är skickad.",
                        "The order has shipped.",
                        List.of(order.evidenceId())
                )),
                new DemoCustomerChatTurnResponse.RagExecution(
                        false,
                        "not_run",
                        null,
                        null,
                        null,
                        false,
                        false,
                        null,
                        null,
                        null,
                        0,
                        "not_applicable",
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new DemoCustomerChatTurnResponse.Verification(
                        "completed",
                        true,
                        true,
                        false,
                        true,
                        false,
                        true,
                        false,
                        "released_exact_order_snapshot"
                ),
                new DemoCustomerChatTurnResponse.Receipt(
                        1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        "customer_order_and_refund_state",
                        false,
                        false,
                        false,
                        1,
                        null,
                        null,
                        "not_incurred",
                        "No provider call was made."
                ),
                null,
                List.of("Synthetic test")
        );
    }
}
