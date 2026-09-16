package dev.shirwac.incidentdetective.nordly;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NordlyKnowledgeRetrievalEvalResourceTest {

    private static final String RESOURCE =
            "evals/nordly-knowledge-retrieval-eval-v2.json";

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private NordlyKnowledgeCorpus corpus;

    @Autowired
    private KnowledgeRagSafetyGate safetyGate;

    @Test
    void keepsRetrievalCasesBoundToEligibleCurrentEvidence() throws Exception {
        EvalSuite suite = readSuite();
        Set<String> eligibleEvidence = corpus.entries().stream()
                .map(entry -> entry.evidenceId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals("nordly-knowledge-retrieval-eval-v2", suite.suiteVersion());
        assertEquals(corpus.version(), suite.corpusVersion());
        assertEquals("PENDING_PROVIDER_RUN", suite.measurementStatus());
        assertEquals("APPROVED", suite.retrievalContract().requiredLifecycle());
        assertEquals("public_demo", suite.retrievalContract().requiredAccessScope());
        assertEquals("gemini-embedding-2", suite.retrievalContract().model());
        assertEquals(768, suite.retrievalContract().dimensions());
        assertEquals(3, suite.retrievalContract().topK());
        assertEquals(16, suite.cases().size());
        assertEquals(6, suite.cases().stream()
                .filter(item -> "development".equals(item.split()))
                .count());
        assertEquals(10, suite.cases().stream()
                .filter(item -> "held_out".equals(item.split()))
                .count());
        assertEquals(14, suite.cases().stream()
                .filter(item -> !item.expectedEmpty())
                .count());
        assertEquals(2, suite.cases().stream()
                .filter(RetrievalCase::expectedEmpty)
                .count());
        assertTrue(suite.cases().stream().anyMatch(item -> "sv".equals(item.locale())));
        assertTrue(suite.cases().stream().anyMatch(item -> "en".equals(item.locale())));

        Set<String> caseIds = new HashSet<>();
        for (RetrievalCase evalCase : suite.cases()) {
            assertTrue(caseIds.add(evalCase.caseId()), evalCase.caseId());
            assertTrue(
                    safetyGate.evaluate(evalCase.query()).allowed(),
                    evalCase.caseId() + " must reach bounded retrieval"
            );
            if (evalCase.expectedEmpty()) {
                assertEquals("no_match", evalCase.caseType());
                assertTrue(evalCase.relevantEvidenceIds().isEmpty());
                assertEquals(0, evalCase.minimumRelevantHits());
                continue;
            }
            assertFalse(evalCase.relevantEvidenceIds().isEmpty());
            assertTrue(evalCase.minimumRelevantHits() > 0);
            assertTrue(evalCase.minimumRelevantHits()
                    <= evalCase.relevantEvidenceIds().size());
            assertTrue(
                    eligibleEvidence.containsAll(evalCase.relevantEvidenceIds()),
                    evalCase.caseId()
            );
        }

        assertFalse(eligibleEvidence.contains(
                "nordly-evidence-restricted-compensation"
        ));
        assertFalse(eligibleEvidence.contains(
                "nordly-evidence-legacy-refund-window"
        ));
        assertFalse(eligibleEvidence.contains(
                "nordly-evidence-untrusted-instruction"
        ));
    }

    @Test
    void keepsRiskCasesBlockedBeforeEmbeddingOrGeneration() throws Exception {
        EvalSuite suite = readSuite();

        assertEquals(9, suite.inputGateCases().size());
        Set<String> caseIds = new HashSet<>();
        for (InputGateCase evalCase : suite.inputGateCases()) {
            assertTrue(caseIds.add(evalCase.caseId()), evalCase.caseId());
            KnowledgeRagSafetyGate.Decision decision = safetyGate.evaluate(
                    evalCase.question()
            );
            assertFalse(decision.allowed(), evalCase.caseId());
            assertEquals(
                    evalCase.expectedReasonCode(),
                    decision.reasonCode().name(),
                    evalCase.caseId()
            );
        }
    }

    private EvalSuite readSuite() throws Exception {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return jsonMapper.readValue(input, EvalSuite.class);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EvalSuite(
            String suiteVersion,
            String corpusVersion,
            String measurementStatus,
            RetrievalContract retrievalContract,
            List<RetrievalCase> cases,
            List<InputGateCase> inputGateCases
    ) {
        private EvalSuite {
            cases = List.copyOf(cases);
            inputGateCases = List.copyOf(inputGateCases);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RetrievalContract(
            String requiredLifecycle,
            String requiredAccessScope,
            String model,
            int dimensions,
            int topK
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RetrievalCase(
            String caseId,
            String split,
            String caseType,
            String locale,
            String query,
            List<String> relevantEvidenceIds,
            int minimumRelevantHits,
            boolean expectedEmpty
    ) {
        private RetrievalCase {
            relevantEvidenceIds = List.copyOf(relevantEvidenceIds);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InputGateCase(
            String caseId,
            String locale,
            String question,
            String expectedReasonCode
    ) {
    }
}
