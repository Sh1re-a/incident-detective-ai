package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NordlyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTheSyntheticNordlyWorldFromValidatedResources() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/demo-world"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version")
                        .value(DemoWorldResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.truth_label")
                        .value(org.hamcrest.Matchers.containsString(
                                "FIKTIVT FÖRETAG"
                        )))
                .andExpect(jsonPath("$.truth_label_en")
                        .value(org.hamcrest.Matchers.containsString(
                                "FICTIONAL COMPANY"
                        )))
                .andExpect(jsonPath("$.company.id").value("nordly-commerce"))
                .andExpect(jsonPath("$.company.display_name")
                        .value("Nordly Commerce AB"))
                .andExpect(jsonPath("$.company.storefront_name")
                        .value("Nordly Market"))
                .andExpect(jsonPath("$.company.description_en")
                        .value(org.hamcrest.Matchers.containsString(
                                "controlled AI system"
                        )))
                .andExpect(jsonPath("$.company.markets")
                        .value(contains("Sverige", "Danmark", "Finland")))
                .andExpect(jsonPath("$.company.markets_en")
                        .value(contains("Sweden", "Denmark", "Finland")))
                .andExpect(jsonPath("$.company.industry_en")
                        .value("Nordic design retail online"))
                .andExpect(jsonPath("$.services.length()").value(6))
                .andExpect(jsonPath("$.services[*].display_name_en")
                        .value(contains(
                                "Storefront",
                                "Cart",
                                "Checkout",
                                "Payment",
                                "Inventory",
                                "Orders"
                        )))
                .andExpect(jsonPath("$.services[*].role_en")
                        .value(everyItem(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.blankString()
                        ))))
                .andExpect(jsonPath("$.questions.length()").value(4))
                .andExpect(jsonPath("$.rag_examples.length()").value(11))
                .andExpect(jsonPath("$.rag_examples[0].expected_boundary")
                        .value("rag"))
                .andExpect(jsonPath("$.rag_examples[5].expected_boundary")
                        .value("blocked_before_provider"))
                .andExpect(jsonPath("$.rag_examples[6].category")
                        .value("EMPLOYEE_COMPENSATION"))
                .andExpect(jsonPath("$.rag_examples[6].prompt_en")
                        .value("Can I find out how much someone at Nordly is paid?"))
                .andExpect(jsonPath("$.questions[*].replay_available")
                        .value(everyItem(is(true))))
                .andExpect(jsonPath("$.featured_scenario_id")
                        .value("checkout-orders-at-risk-v1"))
                .andExpect(jsonPath("$.corpus.version")
                        .value("nordly-knowledge-corpus-v2"))
                .andExpect(jsonPath("$.corpus.document_count").value(16))
                .andExpect(jsonPath("$.corpus.chunk_count").value(30))
                .andExpect(jsonPath("$.corpus.approved_documents").value(14))
                .andExpect(jsonPath("$.corpus.deprecated_documents").value(1))
                .andExpect(jsonPath("$.corpus.untrusted_documents").value(1))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("api_key"));
        assertFalse(body.contains("database_url"));
        assertFalse(body.contains("card_number"));
    }

    @Test
    void replaysDuplicateAuthorizationWithTwoApprovedCitations()
            throws Exception {
        assertReplayBoundary("duplicate-authorization")
                .andExpect(jsonPath("$.answer.status").value("answered"))
                .andExpect(jsonPath("$.retrieval.ranked_matches.length()").value(2))
                .andExpect(jsonPath("$.retrieval.ranked_matches[*].rank")
                        .value(contains(1, 2)))
                .andExpect(jsonPath("$.retrieval.ranked_matches[*].status")
                        .value(everyItem(is("APPROVED"))))
                .andExpect(jsonPath("$.retrieval.ranked_matches[*].similarity")
                        .value(everyItem(nullValue())))
                .andExpect(jsonPath("$.answer.claims.length()").value(2))
                .andExpect(jsonPath("$.answer.claims[0].citation_ids[0]")
                        .value("nordly-evidence-payment-reservation-vs-capture"))
                .andExpect(jsonPath("$.answer.claims[1].citation_ids[0]")
                        .value("nordly-evidence-duplicate-reservation-check"))
                .andExpect(jsonPath("$.verification.citations_seen").value(2))
                .andExpect(jsonPath("$.verification.action_within_scope")
                        .value(true))
                .andExpect(jsonPath("$.verification.overall_outcome")
                        .value("answered_with_grounded_citations"));
    }

    @Test
    void replaysRefundTimingWithAnApprovedSource() throws Exception {
        assertReplayBoundary("refund-timing")
                .andExpect(jsonPath("$.answer.status").value("answered"))
                .andExpect(jsonPath("$.retrieval.ranked_matches.length()").value(1))
                .andExpect(jsonPath("$.retrieval.ranked_matches[0].document_id")
                        .value("kb-returns-refunds"))
                .andExpect(jsonPath("$.retrieval.ranked_matches[0].chunk_id")
                        .value("refund-timing-card"))
                .andExpect(jsonPath("$.verification.citations_seen").value(1));
    }

    @Test
    void refusesARefundActionBeforeRetrievalOrExecution() throws Exception {
        assertReplayBoundary("refund-customer-action")
                .andExpect(jsonPath("$.answer.status").value("refused"))
                .andExpect(jsonPath("$.answer.claims.length()").value(0))
                .andExpect(jsonPath("$.retrieval.ranked_matches.length()").value(0))
                .andExpect(jsonPath("$.verification.citations_seen").value(0))
                .andExpect(jsonPath("$.verification.action_within_scope")
                        .value(false))
                .andExpect(jsonPath("$.verification.overall_outcome")
                        .value("refused_out_of_scope_action"));
    }

    @Test
    void abstainsWhenTheApprovedCorpusHasNoAnswer() throws Exception {
        assertReplayBoundary("unsupported-gift-card-extension")
                .andExpect(jsonPath("$.answer.status")
                        .value("insufficient_evidence"))
                .andExpect(jsonPath("$.answer.claims.length()").value(0))
                .andExpect(jsonPath("$.retrieval.ranked_matches.length()").value(0))
                .andExpect(jsonPath("$.verification.action_within_scope")
                        .value(true))
                .andExpect(jsonPath("$.verification.overall_outcome")
                        .value("abstained_insufficient_evidence"));
    }

    @Test
    void returnsProblemDetailsForAnUnknownKnowledgeQuestion()
            throws Exception {
        mockMvc.perform(post(
                        "/api/v1/knowledge/questions/{questionId}/runs/recorded-replay",
                        "unknown-question"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                        .value("Nordly knowledge question not found"))
                .andExpect(jsonPath("$.code")
                        .value("KNOWLEDGE_QUESTION_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(
                        "Nordly knowledge question not found: unknown-question"
                ));
    }

    @Test
    void doesNotServeNordlyClasspathResourcesDirectly() throws Exception {
        List<String> privatePaths = List.of(
                "/demo/nordly-demo-world-v1.json",
                "/knowledge/nordly-knowledge-corpus-v2.json",
                "/knowledge/replays/nordly-knowledge-replays-v1.json"
        );

        for (String path : privatePaths) {
            mockMvc.perform(get(path)).andExpect(status().isNotFound());
        }
    }

    @Test
    void exposesEverySyntheticDocumentWithAnExplicitRagBoundary()
            throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/documents"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version").value(
                        KnowledgeDocumentLibraryResponse.CONTRACT_VERSION
                ))
                .andExpect(jsonPath("$.mode").value("read_only_corpus"))
                .andExpect(jsonPath("$.synthetic_only").value(true))
                .andExpect(jsonPath("$.current_vector_search").value(false))
                .andExpect(jsonPath("$.corpus_version")
                        .value("nordly-knowledge-corpus-v2"))
                .andExpect(jsonPath("$.document_count").value(16))
                .andExpect(jsonPath("$.chunk_count").value(30))
                .andExpect(jsonPath("$.eligible_document_count").value(13))
                .andExpect(jsonPath("$.eligible_chunk_count").value(27))
                .andExpect(jsonPath("$.eligibility_rule.required_lifecycle")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.eligibility_rule.required_access_scope")
                        .value("public_demo"))
                .andExpect(jsonPath("$.embedding_profile.model_id")
                        .value("gemini-embedding-2"))
                .andExpect(jsonPath("$.documents.length()").value(16))
                .andExpect(jsonPath("$.documents[0].rag_eligibility.eligible")
                        .value(true))
                .andExpect(jsonPath("$.documents[0].display_filename")
                        .value("NLY-COMP-001_service-map-and-ownership.md"))
                .andExpect(jsonPath("$.documents[0].title_sv")
                        .value("Nordlys tjänstekarta och systemägarskap"))
                .andExpect(jsonPath("$.documents[0].content_visible")
                        .value(true))
                .andExpect(jsonPath("$.documents[0].rag_eligibility.reason_code")
                        .value("APPROVED_PUBLIC_DEMO"))
                .andExpect(jsonPath("$.documents[0].chunks[0].text")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.blankString()
                        )))
                .andExpect(jsonPath("$.documents[13].id")
                        .value("kb-employee-compensation-register"))
                .andExpect(jsonPath("$.documents[13].rag_eligibility.eligible")
                        .value(false))
                .andExpect(jsonPath("$.documents[13].rag_eligibility.reason_code")
                        .value("PUBLIC_DEMO_SCOPE_MISSING"))
                .andExpect(jsonPath("$.documents[13].content_visible")
                        .value(false))
                .andExpect(jsonPath("$.documents[13].chunks[0].text")
                        .value(nullValue()))
                .andExpect(jsonPath("$.documents[14].id")
                        .value("kb-legacy-refund-playbook"))
                .andExpect(jsonPath("$.documents[14].lifecycle")
                        .value("DEPRECATED"))
                .andExpect(jsonPath("$.documents[14].rag_eligibility.eligible")
                        .value(false))
                .andExpect(jsonPath("$.documents[14].rag_eligibility.reason_code")
                        .value("LIFECYCLE_NOT_APPROVED"))
                .andExpect(jsonPath("$.documents[14].chunks[0].text")
                        .value(nullValue()))
                .andExpect(jsonPath("$.documents[15].id")
                        .value("kb-untrusted-shortcuts"))
                .andExpect(jsonPath("$.documents[15].lifecycle")
                        .value("UNTRUSTED"))
                .andExpect(jsonPath("$.documents[15].rag_eligibility.eligible")
                        .value(false))
                .andExpect(jsonPath("$.documents[15].content_visible")
                        .value(false))
                .andExpect(jsonPath("$.documents[15].chunks[0].text")
                        .value(nullValue()));
    }

    private org.springframework.test.web.servlet.ResultActions
    assertReplayBoundary(String questionId) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/knowledge/questions/{questionId}/runs/recorded-replay",
                        questionId
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.contract_version")
                        .value(KnowledgeReplayResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.mode")
                        .value(KnowledgeReplayResponse.MODE))
                .andExpect(jsonPath("$.truth_label")
                        .value(org.hamcrest.Matchers.containsString(
                                "INGEN MODELL- ELLER VECTORSÖKNING KÖRS NU"
                        )))
                .andExpect(jsonPath("$.model_backed").value(false))
                .andExpect(jsonPath("$.current_vector_search").value(false))
                .andExpect(jsonPath("$.question.id").value(questionId))
                .andExpect(jsonPath("$.retrieval.backend")
                        .value("recorded_snapshot"))
                .andExpect(jsonPath("$.retrieval.corpus_version")
                        .value("nordly-knowledge-corpus-v2"))
                .andExpect(jsonPath("$.retrieval.embedding_profile.provider")
                        .value("google_genai"))
                .andExpect(jsonPath("$.retrieval.embedding_profile.model_id")
                        .value("gemini-embedding-2"))
                .andExpect(jsonPath("$.retrieval.embedding_profile.dimensions")
                        .value(768))
                .andExpect(jsonPath(
                        "$.retrieval.query_embedding.executed_in_this_run"
                ).value(false))
                .andExpect(jsonPath("$.retrieval.query_embedding.latency_ms")
                        .value(nullValue()))
                .andExpect(jsonPath("$.verification.schema_pass").value(true))
                .andExpect(jsonPath("$.verification.approved_documents_only")
                        .value(true))
                .andExpect(jsonPath("$.verification.claim_support_pass")
                        .value(true))
                .andExpect(jsonPath("$.receipt.tool_calls.length()").value(0))
                .andExpect(jsonPath("$.receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.receipt.action_executed").value(false))
                .andExpect(jsonPath("$.receipt.latency_ms").value(nullValue()))
                .andExpect(jsonPath("$.receipt.total_tokens").value(nullValue()))
                .andExpect(jsonPath("$.receipt.estimated_cost_usd")
                        .value(nullValue()))
                .andExpect(jsonPath("$.receipt.cost_status")
                        .value("not_incurred"));
    }
}
