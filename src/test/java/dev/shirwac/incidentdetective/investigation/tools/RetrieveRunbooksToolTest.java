package dev.shirwac.incidentdetective.investigation.tools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RetrieveRunbooksToolTest {

    @Autowired
    private RetrieveRunbooksTool tool;

    @Test
    void retrievesOnlyMatchingRunbooksInsideTheScenario() {
        RetrieveRunbooksResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new RetrieveRunbooksArguments("payment timeout request trace", 4)
        );

        assertEquals(ToolName.RETRIEVE_RUNBOOKS, tool.name());
        assertEquals("deterministic fixture retrieval", tool.safeModeDescription());
        assertTrue(tool.limitation().contains("not pgvector"));
        assertEquals(1, result.returnedCount());
        assertEquals(
                "cpt-v1-runbook-timeout-precedence",
                result.evidence().getFirst().evidenceId()
        );
        assertEquals(
                "rb-payment-provider-timeouts",
                result.evidence().getFirst().content().documentId()
        );
        assertTrue(result.availableDocumentIds().stream()
                .noneMatch(documentId -> documentId.contains("service-contract")));
    }

    @Test
    void returnsNoEvidenceWhenTheQueryDoesNotMatch() {
        RetrieveRunbooksResult result = tool.execute(
                "checkout-cart-segment-failures-v1",
                new RetrieveRunbooksArguments("payment latency timeout", 2)
        );

        assertTrue(result.evidence().isEmpty());
        assertEquals(0, result.returnedCount());
    }

    @Test
    void rejectsUnsearchableOrOutOfRangeArguments() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new RetrieveRunbooksArguments("--", 4)
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new RetrieveRunbooksArguments("timeout", 5)
                )
        );
    }
}
