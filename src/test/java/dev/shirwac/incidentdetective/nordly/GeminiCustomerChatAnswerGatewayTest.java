package dev.shirwac.incidentdetective.nordly;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiClientFactory;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

class GeminiCustomerChatAnswerGatewayTest {

    private static final String ORDER_EVIDENCE =
            "nordly-demo-order-2051-snapshot";
    private static final String CONTACT_EVIDENCE =
            "nordly-evidence-manual-support-contact";
    private static final String DATA_BOUNDARY_EVIDENCE =
            "nordly-evidence-data-minimization";
    private static final String AUTHORITY_EVIDENCE =
            "nordly-evidence-assistant-operating-role";

    private final GoogleGenAiClientFactory clientFactory = mock(
            GoogleGenAiClientFactory.class
    );
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void promptMakesProtectedBoundaryRepliesNaturalAndNonDisclosing()
            throws Exception {
        String prompt = new ClassPathResource(
                GeminiCustomerChatAnswerGateway.PROMPT_RESOURCE
        ).getContentAsString(StandardCharsets.UTF_8);

        assertTrue(prompt.contains("routed_intent is \"protected_boundary\""));
        assertTrue(prompt.contains("Return no claims"));
        assertTrue(prompt.contains(
                "Jag har inte tillgång till privat information, men jag "
                        + "hjälper dig gärna med en annan vanlig orderfråga."
        ));
        assertTrue(prompt.contains(
                "I don't have access to private information, but I am happy "
                        + "to help with another ordinary order question."
        ));
        assertTrue(prompt.contains("Do not invent or suggest a contact route"));
        assertTrue(prompt.contains("Build text_sv by joining only the selected"));
        assertTrue(prompt.contains("Never add a tracking link"));
    }

    @Test
    void usesTheSharedConfirmedAnswerBudgetBoundary() {
        LiveAiRunGuard guard = mock(LiveAiRunGuard.class);
        CustomerChatAnswerGateway.Result expected = new CustomerChatAnswerGateway.Result(
                new CustomerChatAnswerGateway.Answer(
                        "Jag hjälper dig gärna.",
                        "I am happy to help.",
                        List.of()
                ),
                new CustomerChatAnswerGateway.ProviderMetadata(
                        GoogleGenAiProviderRoute.from(properties("test-only-key")),
                        "response-1",
                        "gemini-3.1-flash-lite",
                        null,
                        4
                ),
                new ModelCostEstimate(null, null, "test estimate")
        );
        when(guard.runConfirmed(
                eq(true),
                eq(LiveAiOperation.CUSTOMER_CHAT_ANSWER),
                any()
        )).thenReturn(expected);
        GeminiCustomerChatAnswerGateway gateway = gateway(
                properties("test-only-key"),
                guard
        );

        CustomerChatAnswerGateway.Result actual = gateway.generate(
                true,
                answeredInput()
        );

        assertEquals(expected, actual);
        verify(guard).runConfirmed(
                eq(true),
                eq(LiveAiOperation.CUSTOMER_CHAT_ANSWER),
                any()
        );
        assertEquals(
                5_000,
                LiveAiOperation.CUSTOMER_CHAT_ANSWER.allowanceMicroUsd()
        );
    }

    @Test
    void decodesANaturalAnswerWithBoundedEvidenceAndProviderMetadata() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response(validAnswer(), FinishReason.Known.STOP),
                answeredInput(),
                87
        );

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
        assertEquals(1, result.answer().claims().size());
        assertEquals(
                List.of(ORDER_EVIDENCE),
                result.answer().claims().getFirst().citationIds()
        );
        assertEquals("developer_api", result.provider().route().transport());
        assertEquals("api_key", result.provider().route().authenticationMode());
        assertEquals("gemini-3.1-flash-lite", result.provider().modelVersion());
        assertEquals(87, result.provider().latencyMs());
        assertNull(result.provider().tokenUsage());
    }

    @Test
    void allowsAClarificationWithoutClaimsWhenNoEvidenceWasUsed() {
        CustomerChatAnswerGateway.Input input = new CustomerChatAnswerGateway.Input(
                "Vad menar du?",
                "sv",
                "clarification_required",
                "clarification_required",
                List.of()
        );

        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Menar du leveransstatus eller returregler?",
                          "text_en": "Do you mean delivery status or return rules?",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                input,
                12
        );

        assertTrue(result.answer().claims().isEmpty());
    }

    @Test
    void allowsAnAnsweredConversationWithoutEvidenceClaims() {
        CustomerChatAnswerGateway.Input input = new CustomerChatAnswerGateway.Input(
                "Hej!",
                "sv",
                "conversation",
                "answered",
                List.of(evidence())
        );

        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Vad vill du ha hjälp med idag?",
                          "text_en": "Hi! What would you like help with today?",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                input,
                9
        );

        assertEquals("Hej! Vad vill du ha hjälp med idag?",
                result.answer().textSv());
        assertTrue(result.answer().claims().isEmpty());
        assertEquals(9, result.provider().latencyMs());
    }

    @Test
    void rejectsACitationOutsideTheBoundedEvidenceList() {
        assertMalformed("""
                {
                  "text_sv": "Hej! Din order är skickad.",
                  "text_en": "Hi! Your order has shipped.",
                  "claims": [{
                    "text_sv": "Ordern är skickad.",
                    "text_en": "The order has shipped.",
                    "citation_ids": ["unknown-evidence"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void rejectsAnAnsweredOutcomeWithoutEvidenceClaims() {
        assertMalformed("""
                {
                  "text_sv": "Hej! Din order är skickad.",
                  "text_en": "Hi! Your order has shipped.",
                  "claims": []
                }
                """, answeredInput());
    }

    @Test
    void projectsAwayFactualTextOutsideTheSelectedCanonicalClaim() {
        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Order NORD-2051 är skickad. Du kan följa paketet via spårningslänken i din leveransbekräftelse.",
                  "text_en": "Order NORD-2051 has shipped. You can follow the package through the tracking link in your shipping confirmation.",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void projectsCanonicalClaimInsteadOfARephrasedFactualSentence() {
        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Order NORD-2051 är skickad med DHL.",
                  "text_en": "Order NORD-2051 has shipped with DHL.",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void restoresCanonicalRangePunctuationFromTheSelectedClaim() {
        CustomerChatAnswerGateway.Claim deliveryClaim =
                new CustomerChatAnswerGateway.Claim(
                        "Den beräknas komma 18–21 september.",
                        "It is expected to arrive September 18–21.",
                        List.of(ORDER_EVIDENCE)
                );
        CustomerChatAnswerGateway.Input input =
                new CustomerChatAnswerGateway.Input(
                        "När kommer den?",
                        "sv",
                        "order_status",
                        "answered",
                        List.of(),
                        List.of(evidence()),
                        List.of(deliveryClaim)
                );

        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Den beräknas komma 18, 21 september.",
                  "text_en": "It is expected to arrive September 18, 21.",
                  "claims": [{
                    "text_sv": "Den beräknas komma 18–21 september.",
                    "text_en": "It is expected to arrive September 18–21.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, input);

        assertEquals("Den beräknas komma 18–21 september.",
                result.answer().textSv());
    }

    @Test
    void projectsASelectedClaimEvenWhenModelProseOmittedIt() {
        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Hej!",
                  "text_en": "Hi!",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void dropsAnUnsupportedFactualTailAndReleasesOnlyTheCanonicalClaim() {
        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Jag hjälper dig gärna vidare, och spårningslänken finns i leveransbekräftelsen.",
                  "text_en": "I am happy to help further, and the tracking link is in the shipping confirmation.",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void acceptsNaturalReplyWhenEveryFactCopiesAVerifiedClaim() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Order NORD-2051 är skickad.",
                          "text_en": "Hi! Order NORD-2051 has shipped.",
                          "claims": [{
                            "text_sv": "Order NORD-2051 är skickad.",
                            "text_en": "Order NORD-2051 has shipped.",
                            "citation_ids": ["nordly-demo-order-2051-snapshot"]
                          }]
                        }
                        """, FinishReason.Known.STOP),
                answeredInput(),
                3
        );

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void acceptsBriefEmpathyWhileKeepingTheOrderFactInAClaim() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Jag förstår att väntan känns frustrerande. Order NORD-2051 är skickad. Jag hjälper dig gärna vidare.",
                          "text_en": "Hi! I understand that waiting is frustrating. Order NORD-2051 has shipped. I am happy to help further.",
                          "claims": [{
                            "text_sv": "Order NORD-2051 är skickad.",
                            "text_en": "Order NORD-2051 has shipped.",
                            "citation_ids": ["nordly-demo-order-2051-snapshot"]
                          }]
                        }
                        """, FinishReason.Known.STOP),
                answeredInput(),
                3
        );

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void joinsCanonicalClaimsInTheModelsSelectedOrder() {
        CustomerChatAnswerGateway.Claim deliveryClaim =
                new CustomerChatAnswerGateway.Claim(
                        "Den beräknas komma 18–21 september.",
                        "It is expected to arrive September 18–21.",
                        List.of(ORDER_EVIDENCE)
                );
        CustomerChatAnswerGateway.Input input =
                new CustomerChatAnswerGateway.Input(
                        "Vad beställde jag och när kommer det?",
                        "sv",
                        "order_status",
                        "answered",
                        List.of(),
                        List.of(evidence()),
                        List.of(orderStatusClaim(), deliveryClaim)
                );

        CustomerChatAnswerGateway.Result result = decode("""
                {
                  "text_sv": "Ett ungefärligt svar.",
                  "text_en": "An approximate answer.",
                  "claims": [{
                    "text_sv": "Den beräknas komma 18–21 september.",
                    "text_en": "It is expected to arrive September 18–21.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }, {
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, input);

        assertEquals(
                "Den beräknas komma 18–21 september. Order NORD-2051 är skickad.",
                result.answer().textSv()
        );
        assertEquals(
                "It is expected to arrive September 18–21. Order NORD-2051 has shipped.",
                result.answer().textEn()
        );
    }

    @Test
    void rejectsFactualConversationProseWithoutClaims() {
        CustomerChatAnswerGateway.Input input = new CustomerChatAnswerGateway.Input(
                "När har ni öppet?",
                "sv",
                "conversation",
                "answered",
                List.of(evidence())
        );

        assertMalformed("""
                {
                  "text_sv": "Nordlys kundservice har öppet till 17.",
                  "text_en": "Nordly customer service is open until 17.",
                  "claims": []
                }
                """, input);
    }

    @Test
    void rejectsAClaimLaunderedThroughAllowedButNonSupportingEvidence() {
        assertMalformed("""
                {
                  "text_sv": "Spårningslänken finns i leveransbekräftelsen.",
                  "text_en": "The tracking link is in the shipping confirmation.",
                  "claims": [{
                    "text_sv": "Spårningslänken finns i leveransbekräftelsen.",
                    "text_en": "The tracking link is in the shipping confirmation.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void rejectsAClaimThatTheAssistantPerformedABusinessAction() {
        assertMalformed("""
                {
                  "text_sv": "Jag har återbetalat ordern.",
                  "text_en": "I have refunded the order.",
                  "claims": [{
                    "text_sv": "Jag har återbetalat ordern.",
                    "text_en": "I have refunded the order.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void rejectsPrivateOrSecretOutputEvenWithAnAllowedCitation() {
        assertMalformed("""
                {
                  "text_sv": "Skriv till person@example.com.",
                  "text_en": "Email person@example.com.",
                  "claims": [{
                    "text_sv": "E-postadressen är person@example.com.",
                    "text_en": "The email address is person@example.com.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());
        assertMalformed("""
                {
                  "text_sv": "API key: sk-1234567890abcdefghij",
                  "text_en": "API key: sk-1234567890abcdefghij",
                  "claims": [{
                    "text_sv": "API key: sk-1234567890abcdefghij",
                    "text_en": "API key: sk-1234567890abcdefghij",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, answeredInput());
    }

    @Test
    void rejectsAProtectedDisclosureEvenWithTheAllowedPolicyCitation() {
        assertMalformed("""
                {
                  "text_sv": "Jag kan inte dela allt, men Alice tjänar mycket.",
                  "text_en": "I cannot share everything, but Alice is paid well.",
                  "claims": [{
                    "text_sv": "Alice tjänar mycket.",
                    "text_en": "Alice is paid well.",
                    "citation_ids": ["nordly-evidence-data-minimization"]
                  }]
                }
                """, protectedBoundaryInput());
    }

    @Test
    void rejectsModelAuthoredClaimsForAProtectedBoundary() {
        assertMalformed("""
                {
                  "text_sv": "Jag kan inte lämna ut skyddad information, men jag hjälper dig gärna med din order.",
                  "text_en": "I cannot disclose protected information, but I am happy to help with your order.",
                  "claims": [{
                    "text_sv": "Individuella löneuppgifter är privat personalinformation.",
                    "text_en": "Individual compensation is private employee information.",
                    "citation_ids": ["nordly-evidence-data-minimization"]
                  }]
                }
                """, protectedBoundaryInput());
    }

    @Test
    void acceptsANaturalProtectedBoundaryWithoutModelAuthoredClaims() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Jag kan tyvärr inte lämna ut privat information, men jag hjälper dig gärna med en orderfråga.",
                          "text_en": "I cannot disclose private information, but I am happy to help with an order question.",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                protectedBoundaryInput(),
                2
        );

        assertTrue(result.answer().claims().isEmpty());
    }

    @Test
    void rejectsALogisticsTailFromAProtectedBoundaryReply() {
        assertMalformed("""
                {
                  "text_sv": "Jag har inte möjlighet att lämna ut privat information. Spårningslänken finns i leveransbekräftelsen.",
                  "text_en": "I do not have access to private information. The tracking link is in the shipping confirmation.",
                  "claims": []
                }
                """, protectedBoundaryInput());
    }

    @Test
    void rejectsAnUnrelatedFactHiddenBehindAProtectedBoundary() {
        assertMalformed("""
                {
                  "text_sv": "Jag har inte tillgång till privat information. Ordern är levererad.",
                  "text_en": "I do not have access to private information. The order was delivered.",
                  "claims": []
                }
                """, protectedBoundaryInput());
    }

    @Test
    void acceptsNaturalSwedishAndContractedEnglishNoAccessWording() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Jag har inte tillgång till privata löneuppgifter, men jag hjälper dig gärna med din order.",
                          "text_en": "I don't have access to private salary data, but I am happy to help with your order.",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                protectedBoundaryInput(),
                2
        );

        assertEquals(
                "Jag har inte tillgång till privata löneuppgifter, men jag hjälper dig gärna med din order.",
                result.answer().textSv()
        );
    }

    @Test
    void acceptsNaturalSwedishPossibilityAndEnglishDoNotHaveAccessWording() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Jag har inte möjlighet att lämna ut individuella löneuppgifter, men jag kan hjälpa med en orderfråga.",
                          "text_en": "I do not have access to individual compensation data, but I can help with an order question.",
                          "claims": []
                        }
                        """, FinishReason.Known.STOP),
                protectedBoundaryInput(),
                2
        );

        assertEquals(
                "I do not have access to individual compensation data, but I can help with an order question.",
                result.answer().textEn()
        );
    }

    @Test
    void rejectsAnUnsupportedContactRouteWithZeroClaims() {
        CustomerChatAnswerGateway.Input conversationInput =
                new CustomerChatAnswerGateway.Input(
                        "Hur når jag er?",
                        "sv",
                        "conversation",
                        "answered",
                        List.of()
                );

        assertMalformed("""
                {
                  "text_sv": "Ring 123 så hjälper vi dig.",
                  "text_en": "Call 123 and we will help you.",
                  "claims": []
                }
                """, conversationInput);
    }

    @Test
    void rejectsAContactRouteCitedOnlyToUnrelatedEvidence() {
        CustomerChatAnswerGateway.Input input =
                new CustomerChatAnswerGateway.Input(
                        "Kan någon avbeställa min order?",
                        "sv",
                        "cancel_order",
                        "outside_authority",
                        List.of(evidence(), contactEvidence())
                );

        assertMalformed("""
                {
                  "text_sv": "Jag kan inte avbeställa ordern här. Ring 123.",
                  "text_en": "I cannot cancel the order here. Call 123.",
                  "claims": [{
                    "text_sv": "Du kan ringa 123.",
                    "text_en": "You can call 123.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """, input);
    }

    @Test
    void allowsAContactRouteOnlyWhenItsRelevantClaimCitesContactEvidence() {
        CustomerChatAnswerGateway.Input input =
                new CustomerChatAnswerGateway.Input(
                        "Kan någon avbeställa min order?",
                        "sv",
                        "cancel_order",
                        "outside_authority",
                        List.of(),
                        List.of(authorityEvidence(), contactEvidence()),
                        List.of(cancellationBoundaryClaim(), contactClaim())
                );

        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Jag kan inte avbeställa ordern här. Du kan ringa 123.",
                          "text_en": "I cannot cancel the order here. You can call 123.",
                          "claims": [
                            {
                              "text_sv": "Jag kan inte avbeställa ordern här.",
                              "text_en": "I cannot cancel the order here.",
                              "citation_ids": ["nordly-evidence-assistant-operating-role"]
                            },
                            {
                              "text_sv": "Du kan ringa 123.",
                              "text_en": "You can call 123.",
                              "citation_ids": ["nordly-evidence-manual-support-contact"]
                            }
                          ]
                        }
                        """, FinishReason.Known.STOP),
                input,
                3
        );

        assertEquals("Jag kan inte avbeställa ordern här. Du kan ringa 123.",
                result.answer().textSv());
    }

    @Test
    void doesNotConfuseAReadOnlyShippingStatusWithAnExecutedAction() {
        CustomerChatAnswerGateway.Result result = gateway(properties(
                "test-only-key"
        )).decodeResponse(
                response("""
                        {
                          "text_sv": "Hej! Order NORD-2051 är skickad.",
                          "text_en": "Hi! Order NORD-2051 has shipped.",
                          "claims": [{
                            "text_sv": "Order NORD-2051 är skickad.",
                            "text_en": "Order NORD-2051 has shipped.",
                            "citation_ids": ["nordly-demo-order-2051-snapshot"]
                          }]
                        }
                        """, FinishReason.Known.STOP),
                answeredInput(),
                1
        );

        assertEquals("Order NORD-2051 är skickad.",
                result.answer().textSv());
    }

    @Test
    void rejectsMissingAndTruncatedProviderResponses() {
        GeminiCustomerChatAnswerGateway gateway = gateway(properties(
                "test-only-key"
        ));

        ModelProviderException missing = assertThrows(
                ModelProviderException.class,
                () -> gateway.decodeResponse(
                        GenerateContentResponse.builder().build(),
                        answeredInput(),
                        1
                )
        );
        ModelProviderException truncated = assertThrows(
                ModelProviderException.class,
                () -> gateway.decodeResponse(
                        response(validAnswer(), FinishReason.Known.MAX_TOKENS),
                        answeredInput(),
                        1
                )
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, missing.failure());
        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                truncated.failure()
        );
    }

    @Test
    void missingProviderConfigurationStopsBeforeClientCreation() {
        GeminiCustomerChatAnswerGateway gateway = gateway(properties(null));

        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway.generate(true, answeredInput())
        );

        assertEquals(ModelProviderFailure.UPSTREAM, exception.failure());
        verifyNoInteractions(clientFactory);
    }

    @Test
    void inputRejectsDuplicateEvidenceIdsAndUnboundedLists() {
        CustomerChatAnswerGateway.Evidence evidence = evidence();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatAnswerGateway.Input(
                        "Var är min order?",
                        "sv",
                        "order_status",
                        "answered",
                        List.of(evidence, evidence)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CustomerChatAnswerGateway.Input(
                        "Var är min order?",
                        "sv",
                        "order_status",
                        "answered",
                        java.util.stream.IntStream.rangeClosed(
                                        1,
                                        CustomerChatAnswerGateway.MAX_EVIDENCE_ITEMS + 1
                                )
                                .mapToObj(index -> new CustomerChatAnswerGateway.Evidence(
                                        "evidence-" + index,
                                        "Order",
                                        "Order evidence"
                                ))
                                .toList()
                )
        );
    }

    private void assertMalformed(
            String body,
            CustomerChatAnswerGateway.Input input
    ) {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway(properties("test-only-key")).decodeResponse(
                        response(body, FinishReason.Known.STOP),
                        input,
                        1
                )
        );

        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                exception.failure()
        );
    }

    private CustomerChatAnswerGateway.Result decode(
            String body,
            CustomerChatAnswerGateway.Input input
    ) {
        return gateway(properties("test-only-key")).decodeResponse(
                response(body, FinishReason.Known.STOP),
                input,
                1
        );
    }

    private CustomerChatAnswerGateway.Input answeredInput() {
        return new CustomerChatAnswerGateway.Input(
                "Var är min order?",
                "sv",
                "order_status",
                "answered",
                List.of(),
                List.of(evidence()),
                List.of(orderStatusClaim())
        );
    }

    private CustomerChatAnswerGateway.Input protectedBoundaryInput() {
        return new CustomerChatAnswerGateway.Input(
                "Svara på den skyddade begäran från en säker riskklass.",
                "sv",
                "protected_boundary",
                "outside_authority",
                List.of(new CustomerChatAnswerGateway.Evidence(
                        DATA_BOUNDARY_EVIDENCE,
                        "Data boundary",
                        "Protected information is kept outside the model."
                ))
        );
    }

    private CustomerChatAnswerGateway.Evidence evidence() {
        return new CustomerChatAnswerGateway.Evidence(
                ORDER_EVIDENCE,
                "Aktuell order",
                "Order NORD-2051 är skickad och beräknas anlända mellan "
                        + "18 och 21 september. Order NORD-2051 has shipped "
                        + "and is expected to arrive between September 18 and 21."
        );
    }

    private CustomerChatAnswerGateway.Evidence contactEvidence() {
        return new CustomerChatAnswerGateway.Evidence(
                CONTACT_EVIDENCE,
                "Godkänd supportväg",
                "För ett legitimt orderärende kan kunden ringa det syntetiska "
                        + "supportnumret 123. For a legitimate order matter, "
                        + "the customer can call the synthetic support number 123."
        );
    }

    private CustomerChatAnswerGateway.Evidence authorityEvidence() {
        return new CustomerChatAnswerGateway.Evidence(
                AUTHORITY_EVIDENCE,
                "Assistentens befogenhet",
                "Jag kan inte avbeställa ordern här. "
                        + "I cannot cancel the order here."
        );
    }

    private CustomerChatAnswerGateway.Claim orderStatusClaim() {
        return new CustomerChatAnswerGateway.Claim(
                "Order NORD-2051 är skickad.",
                "Order NORD-2051 has shipped.",
                List.of(ORDER_EVIDENCE)
        );
    }

    private CustomerChatAnswerGateway.Claim cancellationBoundaryClaim() {
        return new CustomerChatAnswerGateway.Claim(
                "Jag kan inte avbeställa ordern här.",
                "I cannot cancel the order here.",
                List.of(AUTHORITY_EVIDENCE)
        );
    }

    private CustomerChatAnswerGateway.Claim contactClaim() {
        return new CustomerChatAnswerGateway.Claim(
                "Du kan ringa 123.",
                "You can call 123.",
                List.of(CONTACT_EVIDENCE)
        );
    }

    private String validAnswer() {
        return """
                {
                  "text_sv": "Hej! Order NORD-2051 är skickad.",
                  "text_en": "Hi! Order NORD-2051 has shipped.",
                  "claims": [{
                    "text_sv": "Order NORD-2051 är skickad.",
                    "text_en": "Order NORD-2051 has shipped.",
                    "citation_ids": ["nordly-demo-order-2051-snapshot"]
                  }]
                }
                """;
    }

    private GenerateContentResponse response(
            String text,
            FinishReason.Known finishReason
    ) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(text))
                                .build())
                        .finishReason(finishReason)
                        .build())
                .build();
    }

    private GeminiCustomerChatAnswerGateway gateway(
            GeminiAiProperties properties
    ) {
        LiveAiRunGuard guard = mock(LiveAiRunGuard.class);
        when(guard.runConfirmed(anyBoolean(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(2);
            return action.get();
        });
        return gateway(properties, guard);
    }

    private GeminiCustomerChatAnswerGateway gateway(
            GeminiAiProperties properties,
            LiveAiRunGuard guard
    ) {
        GeminiCostEstimator costEstimator = mock(GeminiCostEstimator.class);
        when(costEstimator.estimate(
                eq(properties.modelId()),
                nullable(dev.shirwac.incidentdetective.replay.ModelTokenUsage.class)
        )).thenReturn(new ModelCostEstimate(
                null,
                null,
                "Synthetic customer-answer test estimate."
        ));
        return new GeminiCustomerChatAnswerGateway(
                properties,
                clientFactory,
                costEstimator,
                guard,
                jsonMapper
        );
    }

    private GeminiAiProperties properties(String apiKey) {
        return new GeminiAiProperties(
                apiKey,
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.DEVELOPER_API,
                null,
                null
        );
    }
}
