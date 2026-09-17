package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NordlyResourceCatalogTest {

    @Autowired
    private NordlyResourceCatalog catalog;

    @Test
    void loadsOnlyCrossValidatedWorldCorpusAndReplayResources() {
        DemoWorldResponse world = catalog.world();

        assertEquals(DemoWorldResponse.CONTRACT_VERSION, world.contractVersion());
        assertTrue(world.truthLabel().contains("FIKTIVT FÖRETAG"));
        assertTrue(world.truthLabelEn().contains("FICTIONAL COMPANY"));
        assertFalse(world.company().descriptionEn().isBlank());
        assertEquals(
                List.of("Sweden", "Denmark", "Finland"),
                world.company().marketsEn()
        );
        assertEquals("Nordic design retail online", world.company().industryEn());
        assertEquals(world.company().markets().size(), world.company().marketsEn().size());
        assertEquals(6, world.services().size());
        assertTrue(world.services().stream()
                .allMatch(service -> !service.displayNameEn().isBlank()
                        && !service.roleEn().isBlank()));
        assertEquals(17, world.corpus().documentCount());
        assertEquals(35, world.corpus().chunkCount());
        assertEquals(
                List.of(
                        "duplicate-authorization",
                        "refund-timing",
                        "refund-customer-action",
                        "unsupported-gift-card-extension"
                ),
                world.questions().stream()
                        .map(DemoWorldResponse.DemoQuestion::id)
                        .toList()
        );

        for (DemoWorldResponse.DemoQuestion question : world.questions()) {
            KnowledgeReplayResponse replay = catalog.replay(question.id());
            assertEquals(question.id(), replay.question().id());
            assertEquals(question.outcome(), replay.answer().status());
            assertFalse(replay.modelBacked());
            assertFalse(replay.currentVectorSearch());
            assertFalse(replay.retrieval().queryEmbedding().executedInThisRun());
            assertNull(replay.retrieval().queryEmbedding().latencyMs());
            assertTrue(replay.retrieval().rankedMatches().stream()
                    .allMatch(match -> "APPROVED".equals(match.status())));
            assertTrue(replay.verification().schemaPass());
            assertTrue(replay.verification().approvedDocumentsOnly());
            assertTrue(replay.verification().claimSupportPass());
            assertTrue(replay.receipt().toolCalls().isEmpty());
            assertFalse(replay.receipt().writeToolsAvailable());
            assertFalse(replay.receipt().actionExecuted());
            assertNull(replay.receipt().latencyMs());
            assertNull(replay.receipt().totalTokens());
            assertNull(replay.receipt().estimatedCostUsd());
        }
    }
}
