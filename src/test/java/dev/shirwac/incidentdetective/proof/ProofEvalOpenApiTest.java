package dev.shirwac.incidentdetective.proof;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProofEvalOpenApiTest {

    private static final String RETRIEVAL_GET =
            "$.paths['/api/v1/proof/evals/retrieval'].get";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void documentsReadOnlyProofContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(RETRIEVAL_GET + ".summary")
                        .value("Get published retrieval eval proof"))
                .andExpect(jsonPath(
                        RETRIEVAL_GET
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/RetrievalEvalProofResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/proof/evals/retrieval'].post"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/proof/evals/diagnosis']"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.RetrievalConfiguration.required.length()"
                ).value(11))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderUsage.required"
                ).value(containsInAnyOrder(
                        "embedding_calls", "local_input_characters",
                        "provider_billable_characters", "provider_input_tokens",
                        "provider_usage_metadata_complete", "provider_call_latency_ms",
                        "evaluation_latency_ms", "estimated_list_price_cost_usd",
                        "cost_status"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.ProviderUsage"
                                + ".properties.provider_input_tokens.type"
                ).value(containsInAnyOrder("number", "null")));
    }
}
