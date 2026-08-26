package dev.shirwac.incidentdetective.rag;

import dev.shirwac.incidentdetective.domain.scenario.InitialSymptom;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.scenario.TimeWindow;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.RetrieveRunbooksArguments;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgvectorRunbookRetrievalStrategyTest {

    private static final String SCENARIO_ID = "scenario-1";
    private static final RagProperties PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.42
    );

    @Test
    void searchesTheGlobalIndexAndScopesReturnedEvidenceToTheScenario() {
        ClasspathRunbookCorpus corpus = corpus();
        FakeStore store = new FakeStore(corpus);
        CapturingEmbeddings embeddings = new CapturingEmbeddings();
        PgvectorRunbookRetrievalStrategy strategy = new PgvectorRunbookRetrievalStrategy(
                scenarios(),
                corpus,
                store,
                embeddings,
                PROFILE
        );

        var result = strategy.retrieve(
                SCENARIO_ID,
                new RetrieveRunbooksArguments("payment timeout", 2)
        );

        assertEquals(List.of("payment timeout"), embeddings.queries);
        assertEquals(0.42, store.minimumSimilarity);
        assertEquals(2, result.returnedCount());
        assertTrue(result.evidence().stream().allMatch(evidence ->
                SCENARIO_ID.equals(evidence.scenarioId())
        ));
        assertEquals(
                "runbook-payment-timeout-precedence",
                result.evidence().getFirst().evidenceId()
        );
        assertTrue(strategy.safeModeDescription().contains("pgvector"));
    }

    @Test
    void refusesPaidQueryEmbeddingUntilTheCurrentCorpusIsComplete() {
        ClasspathRunbookCorpus corpus = corpus();
        FakeStore store = new FakeStore(corpus);
        store.indexedChunks = corpus.entries().size() - 1;
        CapturingEmbeddings embeddings = new CapturingEmbeddings();
        PgvectorRunbookRetrievalStrategy strategy = new PgvectorRunbookRetrievalStrategy(
                scenarios(),
                corpus,
                store,
                embeddings,
                PROFILE
        );

        RunbookIndexNotReadyException exception = assertThrows(
                RunbookIndexNotReadyException.class,
                () -> strategy.retrieve(
                        SCENARIO_ID,
                        new RetrieveRunbooksArguments("payment timeout", 2)
                )
        );

        assertEquals(11, exception.indexedChunks());
        assertEquals(12, exception.expectedChunks());
        assertTrue(embeddings.queries.isEmpty());
    }

    @Test
    void validatesTheScenarioBeforeAccessingTheIndex() {
        ClasspathRunbookCorpus corpus = corpus();
        FakeStore store = new FakeStore(corpus);
        PgvectorRunbookRetrievalStrategy strategy = new PgvectorRunbookRetrievalStrategy(
                scenarios(),
                corpus,
                store,
                new CapturingEmbeddings(),
                PROFILE
        );

        assertThrows(
                InvestigationScenarioNotFoundException.class,
                () -> strategy.retrieve(
                        "unknown",
                        new RetrieveRunbooksArguments("payment timeout", 2)
                )
        );
        assertEquals(0, store.countCalls);
    }

    private ClasspathRunbookCorpus corpus() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new ClasspathRunbookCorpus(
                mapper,
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    private InvestigationScenarioCatalog scenarios() {
        Scenario scenario = new Scenario(
                SCENARIO_ID,
                "Checkout incident",
                "Synthetic checkout incident",
                Instant.parse("2026-08-26T09:00:00Z"),
                new TimeWindow(
                        Instant.parse("2026-08-26T09:00:00Z"),
                        Instant.parse("2026-08-26T09:15:00Z")
                ),
                List.of("CHECKOUT_API"),
                "Orders are at risk.",
                List.of(new InitialSymptom(
                        "CHECKOUT_ERRORS",
                        "Checkout errors increased.",
                        Instant.parse("2026-08-26T09:05:00Z")
                )),
                1
        );
        return new InvestigationScenarioCatalog() {
            @Override
            public Optional<Scenario> findById(String scenarioId) {
                return SCENARIO_ID.equals(scenarioId)
                        ? Optional.of(scenario)
                        : Optional.empty();
            }

            @Override
            public List<Scenario> findAll() {
                return List.of(scenario);
            }
        };
    }

    private static final class CapturingEmbeddings implements EmbeddingGateway {

        private final List<String> queries = new ArrayList<>();

        @Override
        public EmbeddingResult embedQuery(String query) {
            queries.add(query);
            return new EmbeddingResult(
                    java.util.Collections.nCopies(768, 0.25f),
                    10,
                    3.0,
                    5
            );
        }

        @Override
        public EmbeddingResult embedDocument(String title, String text) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeStore implements RunbookVectorStore {

        private final ClasspathRunbookCorpus corpus;
        private long indexedChunks;
        private int countCalls;
        private double minimumSimilarity;

        private FakeStore(ClasspathRunbookCorpus corpus) {
            this.corpus = corpus;
            indexedChunks = corpus.entries().size();
        }

        @Override
        public boolean containsCurrent(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void upsert(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile,
                EmbeddingResult embedding
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RunbookSearchHit> search(
                String corpusVersion,
                RagProperties profile,
                List<Float> queryEmbedding,
                int topK,
                double minimumSimilarity
        ) {
            this.minimumSimilarity = minimumSimilarity;
            return List.of(
                    new RunbookSearchHit(corpus.entries().getFirst(), 0.91),
                    new RunbookSearchHit(corpus.entries().get(1), 0.76)
            ).stream().limit(topK).toList();
        }

        @Override
        public List<String> documentIds(
                String corpusVersion,
                RagProperties profile
        ) {
            return corpus.documentIds();
        }

        @Override
        public long count(String corpusVersion, RagProperties profile) {
            countCalls++;
            return indexedChunks;
        }
    }
}
