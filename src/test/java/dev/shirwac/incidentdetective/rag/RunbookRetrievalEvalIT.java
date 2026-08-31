package dev.shirwac.incidentdetective.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Small opt-in RAG evaluation lane. It deliberately lives in test scope so the
 * web application does not ship an evaluation framework.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("rag")
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "run.rag.eval", matches = "true")
class RunbookRetrievalEvalIT {

    private static final String IMAGE =
            "pgvector/pgvector:0.8.6-pg17-bookworm";
    private static final String SUITE_RESOURCE =
            "evals/runbook-retrieval-eval-v1.json";

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "incident-detective.rag.database.url",
                POSTGRES::getJdbcUrl
        );
        registry.add(
                "incident-detective.rag.database.username",
                POSTGRES::getUsername
        );
        registry.add(
                "incident-detective.rag.database.password",
                POSTGRES::getPassword
        );
    }

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private RunbookCorpusImporter importer;

    @Autowired
    private ClasspathRunbookCorpus corpus;

    @Autowired
    private RunbookVectorStore store;

    @Autowired
    private EmbeddingGateway embeddings;

    @Autowired
    private RagProperties properties;

    @Test
    void measuresRealGeminiEmbeddingAndPgvectorRetrieval() throws Exception {
        RunbookImportReport imported = importer.importMissingOrChanged();
        assertEquals(corpus.entries().size(), imported.importedChunks());

        EvalSuite suite = readSuite();
        assertEquals(4, suite.retrievalContract().topK());
        List<CaseResult> results = suite.cases().stream()
                .map(this::evaluate)
                .toList();

        long developmentHits = hits(results, "development", "positive");
        long developmentCases = cases(results, "development", "positive");
        long heldOutHits = hits(results, "held_out", "positive");
        long heldOutCases = cases(results, "held_out", "positive");
        long correctNoMatches = results.stream()
                .filter(result -> "no_match".equals(result.evalCase().caseType()))
                .filter(CaseResult::expectationMet)
                .count();
        long noMatchCases = results.stream()
                .filter(result -> "no_match".equals(result.evalCase().caseType()))
                .count();

        assertEquals(5, developmentCases);
        assertEquals(5, developmentHits);
        assertEquals(5, heldOutCases);
        assertTrue(heldOutHits >= 4, "held-out Hit@4 regressed below 4/5");
        assertEquals(noMatchCases, correctNoMatches);

        System.out.printf(
                Locale.ROOT,
                "RAG_EVAL_OK suite=%s model=%s dimensions=%d "
                        + "development_hit_at_4=%d/%d held_out_hit_at_4=%d/%d "
                        + "no_match=%d/%d threshold=%.16f%n",
                suite.suiteVersion(),
                properties.embeddingModel(),
                properties.embeddingDimensions(),
                developmentHits,
                developmentCases,
                heldOutHits,
                heldOutCases,
                correctNoMatches,
                noMatchCases,
                properties.minimumSimilarity()
        );
    }

    private CaseResult evaluate(EvalCase evalCase) {
        EmbeddingResult embedding = embeddings.embedQuery(evalCase.query());
        List<RunbookSearchHit> hits = store.search(
                corpus.version(),
                properties,
                embedding.values(),
                4,
                properties.minimumSimilarity()
        );
        Set<String> returnedEvidenceIds = hits.stream()
                .map(hit -> hit.entry().evidenceId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean expectationMet = evalCase.expectedEmpty()
                ? hits.isEmpty()
                : returnedEvidenceIds.stream().anyMatch(
                        evalCase.relevantEvidenceIds()::contains
                );
        return new CaseResult(evalCase, expectationMet);
    }

    private EvalSuite readSuite() throws Exception {
        try (InputStream input = new ClassPathResource(SUITE_RESOURCE)
                .getInputStream()) {
            return jsonMapper.readValue(input, EvalSuite.class);
        }
    }

    private static long hits(
            List<CaseResult> results,
            String split,
            String caseType
    ) {
        return results.stream()
                .filter(result -> split.equals(result.evalCase().split()))
                .filter(result -> caseType.equals(result.evalCase().caseType()))
                .filter(CaseResult::expectationMet)
                .count();
    }

    private static long cases(
            List<CaseResult> results,
            String split,
            String caseType
    ) {
        return results.stream()
                .filter(result -> split.equals(result.evalCase().split()))
                .filter(result -> caseType.equals(result.evalCase().caseType()))
                .count();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EvalSuite(
            String suiteVersion,
            RetrievalContract retrievalContract,
            List<EvalCase> cases
    ) {
        private EvalSuite {
            cases = List.copyOf(cases);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RetrievalContract(int topK) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EvalCase(
            String caseId,
            String split,
            String caseType,
            String query,
            List<String> relevantEvidenceIds,
            boolean expectedEmpty
    ) {
        private EvalCase {
            relevantEvidenceIds = List.copyOf(relevantEvidenceIds);
        }
    }

    private record CaseResult(EvalCase evalCase, boolean expectationMet) {
    }
}
