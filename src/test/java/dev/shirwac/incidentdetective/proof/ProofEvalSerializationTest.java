package dev.shirwac.incidentdetective.proof;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ProofEvalSerializationTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ProofEvalSummaryService summaries;

    @Test
    void loadsAndSerializesRetrievalSummaryWithExplicitUnknownUsage() {
        RetrievalEvalProofResponse response = summaries.retrieval();

        assertEquals(
                new BigDecimal("0.6620781500197453"),
                response.retrieval().minimumSimilarity()
        );
        assertFalse(response.providerUsage().providerUsageMetadataComplete());
        assertNull(response.providerUsage().providerInputTokens());
        assertNull(response.providerUsage().estimatedListPriceCostUsd());

        JsonNode json = jsonMapper.valueToTree(response);
        assertTrue(json.has("summary_version"));
        assertFalse(json.has("summaryVersion"));
        assertTrue(json.at(
                "/provider_usage/provider_input_tokens"
        ).isNull());
    }

    @Test
    void publishedSummariesRemainInternallyConsistent() {
        RetrievalEvalProofResponse retrieval = summaries.retrieval();
        assertTrue(retrieval.provenance().historicalFrozenRun());
        assertTrue(
                retrieval.development().positiveHits()
                        <= retrieval.development().positiveCases()
        );
        assertTrue(
                retrieval.heldOut().positiveHits()
                        <= retrieval.heldOut().positiveCases()
        );

    }
}
