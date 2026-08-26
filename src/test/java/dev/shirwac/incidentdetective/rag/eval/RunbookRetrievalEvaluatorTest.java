package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.ClasspathRunbookCorpus;
import dev.shirwac.incidentdetective.rag.EmbeddingGateway;
import dev.shirwac.incidentdetective.rag.EmbeddingResult;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import dev.shirwac.incidentdetective.rag.RunbookIndexReadiness;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunbookRetrievalEvaluatorTest {

    private static final RagProperties PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.0
    );

    @Test
    void evaluatesEachCaseOnceAndKeepsUnknownProviderUsageNull() {
        TestContext context = context(0.0);

        RunbookRetrievalEvalReport report = context.evaluator().evaluate("abcdef123456");

        assertEquals(14, context.embeddings().queries.size());
        assertEquals(14, context.store().searchCalls);
        assertEquals(1.0, report.development().hitAtK());
        assertEquals(1.0, report.development().noMatchAccuracy());
        assertEquals(1.0, report.heldOut().hitAtK());
        assertEquals(1.0, report.heldOut().noMatchAccuracy());
        assertEquals(14, report.embeddingUsage().calls());
        assertFalse(report.embeddingUsage().providerUsageMetadataComplete());
        assertNull(report.embeddingUsage().providerBillableCharacters());
        assertNull(report.embeddingUsage().providerInputTokens());
        assertNull(report.costEstimate().usd());
        assertFalse(report.adversarialSynthesisSafetyEvaluated());
        assertTrue(report.adversarialSafetyStatus().startsWith("not_evaluated"));
        assertEquals(14, report.cases().size());
        assertTrue(report.suiteSha256().matches("[0-9a-f]{64}"));
        assertTrue(report.corpusContentSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void heldOutScoresCannotChangeTheDevelopmentThreshold() {
        double baseline = context(0.0).evaluator()
                .evaluate("abcdef1")
                .calibration()
                .frozenThreshold();
        double changedHeldOut = context(0.19).evaluator()
                .evaluate("abcdef1")
                .calibration()
                .frozenThreshold();

        assertEquals(baseline, changedHeldOut);
    }

    private TestContext context(double heldOutScoreOffset) {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        ClasspathRunbookCorpus corpus = new ClasspathRunbookCorpus(mapper, validator);
        ClasspathRunbookRetrievalEvalSuite suite =
                new ClasspathRunbookRetrievalEvalSuite(
                        mapper,
                        validator,
                        corpus,
                        PROFILE
                );
        FakeEmbeddings embeddings = new FakeEmbeddings(suite.suite());
        FakeStore store = new FakeStore(
                corpus,
                suite.suite(),
                heldOutScoreOffset
        );
        RunbookIndexReadiness readiness = new RunbookIndexReadiness(
                corpus,
                store,
                PROFILE
        );
        return new TestContext(
                new RunbookRetrievalEvaluator(
                        suite,
                        corpus,
                        store,
                        embeddings,
                        PROFILE,
                        readiness
                ),
                embeddings,
                store
        );
    }

    private record TestContext(
            RunbookRetrievalEvaluator evaluator,
            FakeEmbeddings embeddings,
            FakeStore store
    ) {
    }

    private static final class FakeEmbeddings implements EmbeddingGateway {

        private final Map<String, Integer> queryIndexes = new HashMap<>();
        private final List<String> queries = new ArrayList<>();

        private FakeEmbeddings(RunbookRetrievalEvalSuite suite) {
            for (int index = 0; index < suite.cases().size(); index++) {
                queryIndexes.put(suite.cases().get(index).query(), index);
            }
        }

        @Override
        public EmbeddingResult embedQuery(String query) {
            queries.add(query);
            List<Float> values = new ArrayList<>(Collections.nCopies(768, 0.0f));
            values.set(0, (queryIndexes.get(query) + 1) / 100.0f);
            return new EmbeddingResult(values, query.length(), null, null, 3);
        }

        @Override
        public EmbeddingResult embedDocument(String title, String text) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeStore implements RunbookVectorStore {

        private final ClasspathRunbookCorpus corpus;
        private final RunbookRetrievalEvalSuite suite;
        private final double heldOutScoreOffset;
        private int searchCalls;

        private FakeStore(
                ClasspathRunbookCorpus corpus,
                RunbookRetrievalEvalSuite suite,
                double heldOutScoreOffset
        ) {
            this.corpus = corpus;
            this.suite = suite;
            this.heldOutScoreOffset = heldOutScoreOffset;
        }

        @Override
        public boolean containsCurrent(
                String corpusVersion,
                RunbookCorpusEntry entry,
                RagProperties profile
        ) {
            return true;
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
            searchCalls++;
            assertEquals(-1.0, minimumSimilarity);
            int caseIndex = Math.round(queryEmbedding.getFirst() * 100) - 1;
            RunbookRetrievalEvalSuite.EvalCase evalCase = suite.cases().get(caseIndex);
            double offset = "held_out".equals(evalCase.split())
                    ? heldOutScoreOffset
                    : 0.0;
            List<RunbookSearchHit> hits = new ArrayList<>();
            if (!evalCase.relevantEvidenceIds().isEmpty()) {
                hits.add(hit(evalCase.relevantEvidenceIds().getFirst(), 0.80 - offset));
            }
            hits.add(hit("runbook-tax-rounding", 0.38 + offset));
            hits.add(hit("runbook-cdn-origin-failure", 0.34 + offset));
            hits.sort(java.util.Comparator.comparingDouble(
                    RunbookSearchHit::cosineSimilarity
            ).reversed());
            return hits.stream().limit(topK).toList();
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
            return corpus.entries().size();
        }

        private RunbookSearchHit hit(String evidenceId, double similarity) {
            RunbookCorpusEntry entry = corpus.entries().stream()
                    .filter(candidate -> evidenceId.equals(candidate.evidenceId()))
                    .findFirst()
                    .orElseThrow();
            return new RunbookSearchHit(entry, similarity);
        }
    }
}
