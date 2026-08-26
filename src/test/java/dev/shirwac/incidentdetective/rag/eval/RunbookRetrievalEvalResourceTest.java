package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.ClasspathRunbookCorpus;
import dev.shirwac.incidentdetective.rag.RagProperties;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class RunbookRetrievalEvalResourceTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void loadsVersionedDevelopmentHeldOutAndAdversarialCases() {
        ClasspathRunbookCorpus corpus = corpus();
        ClasspathRunbookRetrievalEvalSuite resource =
                new ClasspathRunbookRetrievalEvalSuite(
                        jsonMapper,
                        validator(),
                        corpus,
                        properties()
                );
        RunbookRetrievalEvalSuite suite = resource.suite();

        assertEquals("runbook-retrieval-eval-v1", suite.suiteVersion());
        assertEquals(corpus.version(), suite.corpusVersion());
        assertEquals(14, suite.cases().size());
        assertEquals(7, count(suite, "development", null));
        assertEquals(7, count(suite, "held_out", null));
        assertEquals(10, count(suite, null, "positive"));
        assertEquals(3, count(suite, null, "no_match"));
        assertEquals(1, count(suite, null, "adversarial"));
        assertTrue(resource.resourceSha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void keepsAnswersAndUnsafeInstructionsOutOfRetrievalQueries() throws Exception {
        RunbookRetrievalEvalSuite suite = resource().suite();
        String json = jsonMapper.writeValueAsString(suite);

        assertFalse(json.contains("PAYMENT_TIMEOUT_CONFIG"));
        assertFalse(json.contains("INVENTORY_SCHEMA_MISMATCH"));
        assertFalse(suite.cases().stream().anyMatch(evalCase ->
                evalCase.query().contains("Ignore previous instructions")
                        || evalCase.query().contains("rollback completed")
        ));
        assertTrue(suite.cases().stream()
                .filter(evalCase -> "adversarial".equals(evalCase.caseType()))
                .allMatch(evalCase -> evalCase.safetyFollowUp().contains(
                        "Retrieval alone cannot prove"
                )));
    }

    private long count(
            RunbookRetrievalEvalSuite suite,
            String split,
            String type
    ) {
        return suite.cases().stream()
                .filter(evalCase -> split == null || split.equals(evalCase.split()))
                .filter(evalCase -> type == null || type.equals(evalCase.caseType()))
                .count();
    }

    private ClasspathRunbookRetrievalEvalSuite resource() {
        return new ClasspathRunbookRetrievalEvalSuite(
                jsonMapper,
                validator(),
                corpus(),
                properties()
        );
    }

    private ClasspathRunbookCorpus corpus() {
        return new ClasspathRunbookCorpus(jsonMapper, validator());
    }

    private jakarta.validation.Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private RagProperties properties() {
        return new RagProperties("gemini-embedding-2", 768, "search-result-v1", 0.0);
    }
}
