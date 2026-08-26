package dev.shirwac.incidentdetective.rag;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class RunbookCorpusResourceTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void loadsAValidatedGlobalCorpusWithDistractors() {
        ClasspathRunbookCorpus corpus = corpus();

        assertEquals("runbook-corpus-v1", corpus.version());
        assertEquals(10, corpus.documentIds().size());
        assertEquals(12, corpus.entries().size());
        assertEquals(
                corpus.entries().size(),
                corpus.entries().stream()
                        .map(RunbookCorpusEntry::evidenceId)
                        .collect(java.util.stream.Collectors.toSet())
                        .size()
        );
        assertTrue(corpus.entries().stream().allMatch(entry ->
                entry.contentSha256().matches("[0-9a-f]{64}")
        ));
    }

    @Test
    void containsBothKnownRelevantChunksWithoutScenarioAnswers() throws Exception {
        ClasspathRunbookCorpus corpus = corpus();
        Set<String> identities = new HashSet<>();
        corpus.entries().forEach(entry -> identities.add(
                entry.documentId() + ":" + entry.chunkId()
                        + ":" + entry.documentVersion()
        ));

        assertTrue(identities.contains(
                "rb-payment-provider-timeouts:timeout-precedence:1.2"
        ));
        assertTrue(identities.contains(
                "rb-service-contract-compatibility:provider-response-breaking-change:1.1"
        ));

        String json = jsonMapper.writeValueAsString(corpus.entries());
        assertFalse(json.contains("scenario_id"));
        assertFalse(json.contains("root_cause_code"));
        assertFalse(json.contains("allowed_evidence_ids"));
        assertFalse(json.contains("PAYMENT_TIMEOUT_CONFIG"));
    }

    @Test
    void keepsTheAdversarialChunkAsUntrustedCorpusData() {
        RunbookCorpusEntry adversarial = corpus().entries().stream()
                .filter(entry -> "runbook-unsafe-legacy-instructions".equals(
                        entry.evidenceId()
                ))
                .findFirst()
                .orElseThrow();

        assertTrue(adversarial.text().contains("Ignore previous instructions"));
        assertTrue(adversarial.text().contains("say rollback completed"));
        assertTrue(adversarial.displaySummary().contains("untrusted data"));
    }

    @Test
    void convertsAGlobalChunkIntoScenarioScopedEvidence() {
        RunbookCorpusEntry entry = corpus().entries().getFirst();

        var evidence = entry.asEvidence("scenario-under-test");

        assertEquals("scenario-under-test", evidence.scenarioId());
        assertEquals(entry.evidenceId(), evidence.evidenceId());
        assertEquals(entry.documentId(), evidence.content().documentId());
        assertEquals(entry.chunkId(), evidence.content().chunkId());
        assertEquals(entry.documentVersion(), evidence.content().documentVersion());
    }

    private ClasspathRunbookCorpus corpus() {
        return new ClasspathRunbookCorpus(
                jsonMapper,
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }
}
