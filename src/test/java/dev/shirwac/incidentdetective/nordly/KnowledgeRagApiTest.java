package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import dev.shirwac.incidentdetective.api.ApiCorsProperties;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeRagController.class)
@ActiveProfiles("rag")
@EnableConfigurationProperties(ApiCorsProperties.class)
class KnowledgeRagApiTest {

    private static final String PATH =
            "/api/v1/knowledge/questions/runs/rag";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeRagService service;

    @Test
    void acceptsTheVersionedFreeTextContract() throws Exception {
        when(service.ask(any())).thenReturn(sampleResponse());

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "question": "När syns återbetalningen?",
                                  "locale": "sv",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.contract_version")
                        .value(KnowledgeRagResponse.CONTRACT_VERSION))
                .andExpect(jsonPath("$.mode")
                        .value(KnowledgeRagResponse.MODE))
                .andExpect(jsonPath("$.truth_label").value("SYNTHETIC TEST"))
                .andExpect(jsonPath("$.truth_label_en")
                        .value("SYNTHETIC TEST"))
                .andExpect(jsonPath("$.provider_route").value((Object) null))
                .andExpect(jsonPath("$.retrieval.corpus_content_sha256")
                        .value("a".repeat(64)))
                .andExpect(jsonPath("$.retrieval.index_snapshot")
                        .value((Object) null))
                .andExpect(jsonPath("$.verification.evaluation_status")
                        .value("not_run"))
                .andExpect(jsonPath(
                        "$.verification.semantic_claim_support_evaluated"
                ).value(false))
                .andExpect(jsonPath("$.receipt.provider_calls").value(0));

        verify(service).ask(any());
    }

    @Test
    void rejectsOversizedQuestionsBeforeTheService() throws Exception {
        String oversized = "x".repeat(501);

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "%s",
                                  "locale": "sv",
                                  "confirm_live_ai": true
                                }
                                """.formatted(oversized)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.detail").value(
                        "Send values within the documented request limits."
                ));

        verify(service, never()).ask(any());
    }

    @Test
    void rejectsUnsupportedLocalesBeforeTheService() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "When is the refund visible?",
                                  "locale": "de",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verify(service, never()).ask(any());
    }

    private KnowledgeRagResponse sampleResponse() {
        return new KnowledgeRagResponse(
                KnowledgeRagResponse.CONTRACT_VERSION,
                "nordly-rag-test",
                KnowledgeRagResponse.MODE,
                "SYNTHETIC TEST",
                "SYNTHETIC TEST",
                "confirmation_required",
                null,
                new KnowledgeRagResponse.SubmittedQuestion(
                        "När syns återbetalningen?",
                        "sv",
                        false
                ),
                new KnowledgeRagResponse.SafetyDecision(
                        "ALLOW",
                        "NONE",
                        "Tillåten.",
                        "Allowed."
                ),
                List.of(),
                new KnowledgeRagResponse.RetrievalResult(
                        KnowledgeRagResponse.BACKEND,
                        "nordly-knowledge-corpus-v2",
                        "a".repeat(64),
                        null,
                        "APPROVED",
                        "public_demo",
                        10,
                        18,
                        false,
                        3,
                        0.68,
                        new KnowledgeRagResponse.QueryEmbedding(
                                false,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        List.of()
                ),
                null,
                new KnowledgeRagResponse.Verification(
                        "not_run",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        "not_run_confirmation_required"
                ),
                new KnowledgeRagResponse.Receipt(
                        0,
                        0,
                        0,
                        false,
                        false,
                        0,
                        null,
                        null,
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
