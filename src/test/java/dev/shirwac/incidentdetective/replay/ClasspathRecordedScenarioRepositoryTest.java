package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.groundtruth.RunbookReference;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class ClasspathRecordedScenarioRepositoryTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void loadsBothValidatedPackagesFromTheClasspathIndex() {
        ClasspathRecordedScenarioRepository repository = repository();

        RecordedScenarioPackage paymentScenario = repository
                .findById("checkout-orders-at-risk-v1")
                .orElseThrow();
        RecordedScenarioPackage inventoryScenario = repository
                .findById("checkout-cart-segment-failures-v1")
                .orElseThrow();

        assertEquals(
                "PAYMENT_TIMEOUT_CONFIG",
                paymentScenario.groundTruth().rootCauseCode()
        );
        assertEquals(
                "INVENTORY_SCHEMA_MISMATCH",
                inventoryScenario.groundTruth().rootCauseCode()
        );
        assertTrue(repository.findById("unknown-scenario").isEmpty());
    }

    @Test
    void rejectsDuplicateEvidenceIds() throws Exception {
        ScenarioInputs inputs = paymentInputs();
        List<Evidence> evidence = new ArrayList<>(inputs.fixture().evidenceInventory());
        evidence.add(evidence.getFirst());
        RecordedScenarioFixture invalidFixture = new RecordedScenarioFixture(
                inputs.fixture().scenario(),
                evidence,
                inputs.fixture().toolEvents(),
                inputs.fixture().recordedDiagnosis()
        );

        assertInvalid(
                inputs,
                invalidFixture,
                inputs.groundTruth(),
                "duplicate evidence ID"
        );
    }

    @Test
    void rejectsToolEventsThatReferenceMissingEvidence() throws Exception {
        ScenarioInputs inputs = paymentInputs();
        List<RecordedToolEvent> events = new ArrayList<>(inputs.fixture().toolEvents());
        RecordedToolEvent first = events.getFirst();
        events.set(0, new RecordedToolEvent(
                first.eventId(),
                first.toolName(),
                first.safeSummary(),
                List.of("missing-evidence")
        ));
        RecordedScenarioFixture invalidFixture = new RecordedScenarioFixture(
                inputs.fixture().scenario(),
                inputs.fixture().evidenceInventory(),
                events,
                inputs.fixture().recordedDiagnosis()
        );

        assertInvalid(
                inputs,
                invalidFixture,
                inputs.groundTruth(),
                "tool event references missing evidence"
        );
    }

    @Test
    void rejectsDiagnosisCitationsThatToolsDidNotReturn() throws Exception {
        ScenarioInputs inputs = paymentInputs();
        Diagnosis diagnosis = inputs.fixture().recordedDiagnosis();
        List<Claim> claims = new ArrayList<>(diagnosis.claims());
        Claim rootCause = claims.getFirst();
        List<String> evidenceIds = new ArrayList<>(rootCause.evidenceIds());
        evidenceIds.add("cpt-v1-log-inventory-noise");
        claims.set(0, new Claim(
                rootCause.claimCode(),
                rootCause.claimValueCode(),
                rootCause.displayText(),
                evidenceIds
        ));
        Diagnosis invalidDiagnosis = new Diagnosis(
                diagnosis.status(),
                diagnosis.rootCauseCode(),
                diagnosis.affectedService(),
                diagnosis.businessSummary(),
                diagnosis.technicalSummary(),
                claims,
                diagnosis.safeNextStep()
        );
        RecordedScenarioFixture invalidFixture = new RecordedScenarioFixture(
                inputs.fixture().scenario(),
                inputs.fixture().evidenceInventory(),
                inputs.fixture().toolEvents(),
                invalidDiagnosis
        );

        assertInvalid(
                inputs,
                invalidFixture,
                inputs.groundTruth(),
                "diagnosis cites unseen evidence"
        );
    }

    @Test
    void rejectsGroundTruthReferencesToMissingEvidence() throws Exception {
        ScenarioInputs inputs = paymentInputs();
        GroundTruth groundTruth = inputs.groundTruth();
        List<ClaimSupport> support = new ArrayList<>(groundTruth.claimSupport());
        ClaimSupport rootCause = support.getFirst();
        List<String> evidenceIds = new ArrayList<>(rootCause.allowedEvidenceIds());
        evidenceIds.add("missing-evidence");
        support.set(0, new ClaimSupport(
                rootCause.claimCode(),
                rootCause.claimValueCode(),
                evidenceIds
        ));
        GroundTruth invalidGroundTruth = new GroundTruth(
                groundTruth.scenarioId(),
                groundTruth.expectedStatus(),
                groundTruth.rootCauseCode(),
                groundTruth.affectedService(),
                groundTruth.expectedClaims(),
                support,
                groundTruth.relevantRunbooks()
        );

        assertInvalid(
                inputs,
                inputs.fixture(),
                invalidGroundTruth,
                "ground truth references missing evidence"
        );
    }

    @Test
    void rejectsGroundTruthReferencesToMissingRunbookChunks() throws Exception {
        ScenarioInputs inputs = paymentInputs();
        GroundTruth groundTruth = inputs.groundTruth();
        GroundTruth invalidGroundTruth = new GroundTruth(
                groundTruth.scenarioId(),
                groundTruth.expectedStatus(),
                groundTruth.rootCauseCode(),
                groundTruth.affectedService(),
                groundTruth.expectedClaims(),
                groundTruth.claimSupport(),
                List.of(new RunbookReference("missing-runbook", "missing-chunk", "1.0"))
        );

        assertInvalid(
                inputs,
                inputs.fixture(),
                invalidGroundTruth,
                "ground truth references a missing runbook chunk"
        );
    }

    private ClasspathRecordedScenarioRepository repository() {
        return new ClasspathRecordedScenarioRepository(
                jsonMapper,
                Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    private ScenarioInputs paymentInputs() throws IOException {
        String scenarioId = "checkout-orders-at-risk-v1";
        return new ScenarioInputs(
                new ScenarioFixtureIndexEntry(scenarioId, "unused", "unused"),
                readResource(
                        "/fixtures/recorded/" + scenarioId + ".json",
                        RecordedScenarioFixture.class
                ),
                readResource(
                        "/fixtures/ground-truth/" + scenarioId + ".json",
                        GroundTruth.class
                )
        );
    }

    private void assertInvalid(
            ScenarioInputs inputs,
            RecordedScenarioFixture fixture,
            GroundTruth groundTruth,
            String expectedMessage
    ) {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> repository().assemble(inputs.entry(), fixture, groundTruth)
        );
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    private <T> T readResource(String path, Class<T> type) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource " + path);
            return jsonMapper.readValue(input, type);
        }
    }

    private record ScenarioInputs(
            ScenarioFixtureIndexEntry entry,
            RecordedScenarioFixture fixture,
            GroundTruth groundTruth
    ) {
    }
}
