package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.groundtruth.RunbookReference;
import dev.shirwac.incidentdetective.domain.verification.DeterministicVerifier;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class RecordedScenarioResourceTest {

    private static Validator validator;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "checkout-orders-at-risk-v1",
            "checkout-cart-segment-failures-v1"
    })
    void loadsAConsistentRecordedScenario(String scenarioId) throws Exception {
        RecordedScenarioFixture fixture = readRecordedFixture(scenarioId);
        GroundTruth groundTruth = readGroundTruth(scenarioId);

        assertTrue(validator.validate(fixture).isEmpty());
        assertTrue(validator.validate(groundTruth).isEmpty());
        assertEquals(scenarioId, fixture.scenario().scenarioId());
        assertEquals(scenarioId, groundTruth.scenarioId());

        Map<String, Evidence> evidenceById = uniqueEvidenceIndex(fixture);
        assertTrue(fixture.evidenceInventory().stream()
                .allMatch(evidence -> scenarioId.equals(evidence.scenarioId())));

        Set<String> seenEvidenceIds = seenEvidenceIds(fixture, evidenceById);
        assertTrue(seenEvidenceIds.containsAll(citedEvidenceIds(fixture)));
        assertTrue(evidenceById.keySet().containsAll(supportedEvidenceIds(groundTruth)));
        assertTrue(evidenceById.keySet().stream()
                .anyMatch(evidenceId -> !seenEvidenceIds.contains(evidenceId)));
        assertRunbookReferencesExist(groundTruth, fixture.evidenceInventory());

        VerificationReport report = new DeterministicVerifier().verify(
                fixture.recordedDiagnosis(),
                seenEvidenceIds,
                groundTruth
        );

        assertTrue(report.diagnosisSchemaPass());
        assertTrue(report.groundTruthSchemaPass());
        assertTrue(report.citationValidity().valid());
        assertEquals(1.0, report.evidencePrecision().score());
        assertTrue(report.diagnosisCorrectness().rootCauseCorrect());
        assertTrue(report.diagnosisCorrectness().affectedServiceCorrect());
        assertTrue(report.hardErrors().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "checkout-orders-at-risk-v1",
            "checkout-cart-segment-failures-v1"
    })
    void publicScenarioJsonDoesNotRevealEvidenceOrGroundTruth(String scenarioId)
            throws Exception {
        RecordedScenarioFixture fixture = readRecordedFixture(scenarioId);

        String publicScenarioJson = jsonMapper.writeValueAsString(fixture.scenario());

        assertFalse(publicScenarioJson.contains("root_cause"));
        assertFalse(publicScenarioJson.contains("evidence_id"));
        assertFalse(publicScenarioJson.contains("ground_truth"));
        assertFalse(publicScenarioJson.contains("claim_support"));
    }

    private RecordedScenarioFixture readRecordedFixture(String scenarioId) throws IOException {
        return readResource(
                "/fixtures/recorded/" + scenarioId + ".json",
                RecordedScenarioFixture.class
        );
    }

    private GroundTruth readGroundTruth(String scenarioId) throws IOException {
        return readResource(
                "/fixtures/ground-truth/" + scenarioId + ".json",
                GroundTruth.class
        );
    }

    private <T> T readResource(String path, Class<T> type) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource " + path);
            return jsonMapper.readValue(input, type);
        }
    }

    private Map<String, Evidence> uniqueEvidenceIndex(RecordedScenarioFixture fixture) {
        Map<String, Evidence> evidenceById = fixture.evidenceInventory().stream()
                .collect(Collectors.toMap(Evidence::evidenceId, Function.identity()));
        assertEquals(fixture.evidenceInventory().size(), evidenceById.size());
        return evidenceById;
    }

    private Set<String> seenEvidenceIds(
            RecordedScenarioFixture fixture,
            Map<String, Evidence> evidenceById
    ) {
        Set<String> eventIds = new HashSet<>();
        Set<String> seenEvidenceIds = new HashSet<>();
        for (RecordedToolEvent event : fixture.toolEvents()) {
            assertTrue(eventIds.add(event.eventId()), "Duplicate tool event " + event.eventId());
            for (String evidenceId : event.evidenceIds()) {
                assertTrue(evidenceById.containsKey(evidenceId), "Unknown evidence " + evidenceId);
                seenEvidenceIds.add(evidenceId);
            }
        }
        return Set.copyOf(seenEvidenceIds);
    }

    private Set<String> citedEvidenceIds(RecordedScenarioFixture fixture) {
        return fixture.recordedDiagnosis().claims().stream()
                .map(Claim::evidenceIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    private Set<String> supportedEvidenceIds(GroundTruth groundTruth) {
        return groundTruth.claimSupport().stream()
                .map(ClaimSupport::allowedEvidenceIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }

    private void assertRunbookReferencesExist(
            GroundTruth groundTruth,
            List<Evidence> evidenceInventory
    ) {
        List<RunbookEvidence.RunbookContent> runbooks = evidenceInventory.stream()
                .filter(RunbookEvidence.class::isInstance)
                .map(RunbookEvidence.class::cast)
                .map(RunbookEvidence::content)
                .toList();

        for (RunbookReference reference : groundTruth.relevantRunbooks()) {
            assertTrue(runbooks.stream().anyMatch(runbook -> Objects.equals(
                    runbook.documentId(),
                    reference.documentId()
            ) && Objects.equals(
                    runbook.chunkId(),
                    reference.chunkId()
            ) && Objects.equals(
                    runbook.documentVersion(),
                    reference.documentVersion()
            )));
        }
    }
}
