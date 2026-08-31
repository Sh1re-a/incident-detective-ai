package dev.shirwac.incidentdetective.proof;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProofEvalApiTest {

    private static final String RETRIEVAL_PATH =
            "/api/v1/proof/evals/retrieval";
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPublishedRetrievalAggregateWithoutCaseData() throws Exception {
        MvcResult result = mockMvc.perform(get(RETRIEVAL_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.summary_version")
                        .value("retrieval-proof-summary-v1"))
                .andExpect(jsonPath("$.status").value("measured"))
                .andExpect(jsonPath("$.provenance.historical_frozen_run")
                        .value(true))
                .andExpect(jsonPath("$.provenance.executed_at")
                        .value("2026-08-26T11:35:51.512474Z"))
                .andExpect(jsonPath("$.retrieval.backend")
                        .value("pgvector_exact_cosine"))
                .andExpect(jsonPath("$.retrieval.embedding_model")
                        .value("gemini-embedding-2"))
                .andExpect(jsonPath("$.retrieval.embedding_dimensions")
                        .value(768))
                .andExpect(jsonPath("$.retrieval.corpus_document_count")
                        .value(10))
                .andExpect(jsonPath("$.retrieval.corpus_chunk_count")
                        .value(12))
                .andExpect(jsonPath("$.retrieval.minimum_similarity")
                        .value(0.6620781500197453))
                .andExpect(jsonPath("$.development.positive_hits").value(5))
                .andExpect(jsonPath("$.development.hit_at_k").value(1.0))
                .andExpect(jsonPath("$.held_out.positive_hits").value(4))
                .andExpect(jsonPath("$.held_out.hit_at_k").value(0.8))
                .andExpect(jsonPath(
                        "$.provider_usage.provider_usage_metadata_complete"
                ).value(false))
                .andExpect(jsonPath(
                        "$.provider_usage.provider_input_tokens"
                ).value(nullValue()))
                .andExpect(jsonPath(
                        "$.provider_usage.estimated_list_price_cost_usd"
                ).value(nullValue()))
                .andExpect(jsonPath("$.provenance.git_sha")
                        .value("522d90fa514b89cfe4a01e5217e271d6dfccb722"))
                .andReturn();

        assertNoPrivateEvalMaterial(result.getResponse().getContentAsString());
    }

    @Test
    void proofEndpointsCannotStartAnEval() throws Exception {
        mockMvc.perform(post(RETRIEVAL_PATH))
                .andExpect(status().isMethodNotAllowed());
    }

    private static void assertNoPrivateEvalMaterial(String json) {
        assertFalse(json.contains("ground_truth"));
        assertFalse(json.contains("case_id"));
        assertFalse(json.contains("input_resource"));
        assertFalse(json.contains("model_text"));
        assertFalse(json.contains("raw_error"));
        assertFalse(json.contains("prompt_content"));
        assertFalse(json.contains("safe_next_steps"));
    }
}
