package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.groundtruth.RunbookReference;
import dev.shirwac.incidentdetective.domain.verification.DeterministicVerifier;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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

    @Test
    void paymentImpactMetricsMatchTheirCompletedMeasurementWindow() throws Exception {
        RecordedScenarioFixture fixture = readRecordedFixture(
                "checkout-orders-at-risk-v1"
        );
        MetricEvidence ratio = evidence(
                fixture,
                "cpt-v1-metric-checkout-failure-rate",
                MetricEvidence.class
        );
        MetricEvidence failures = evidence(
                fixture,
                "cpt-v1-metric-failed-checkouts",
                MetricEvidence.class
        );

        assertEquals(147.0 / 800.0, ratio.content().value(), 0.0005);
        assertEquals("800", ratio.content().labels().get("attempts"));
        assertEquals("10:02-10:12", ratio.content().labels().get("window"));
        assertEquals(Instant.parse("2026-08-25T10:12:00Z"), ratio.observedAt());
        assertEquals(147.0, failures.content().value());
        assertEquals(ratio.observedAt(), failures.observedAt());
    }

    @Test
    void inventoryEvidenceExplainsTheMultiItemRollout() throws Exception {
        RecordedScenarioFixture fixture = readRecordedFixture(
                "checkout-cart-segment-failures-v1"
        );
        MetricEvidence ratio = evidence(
                fixture,
                "cic-v1-metric-checkout-failure-rate",
                MetricEvidence.class
        );
        MetricEvidence failures = evidence(
                fixture,
                "cic-v1-metric-failed-checkouts",
                MetricEvidence.class
        );
        MetricEvidence contractErrors = evidence(
                fixture,
                "cic-v1-metric-contract-errors",
                MetricEvidence.class
        );
        LogEvidence release = evidence(
                fixture,
                "cic-v1-log-inventory-release",
                LogEvidence.class
        );
        LogEvidence mismatch = evidence(
                fixture,
                "cic-v1-log-schema-mismatch",
                LogEvidence.class
        );
        TraceEvidence trace = evidence(
                fixture,
                "cic-v1-trace-contract-failure",
                TraceEvidence.class
        );

        assertEquals(61.0 / 670.0, ratio.content().value(), 0.0005);
        assertEquals("670", ratio.content().labels().get("attempts"));
        assertEquals("multi_item", ratio.content().labels().get("cart_segment"));
        assertEquals("11:02-11:12", ratio.content().labels().get("window"));
        assertEquals(Instant.parse("2026-08-25T11:12:00Z"), ratio.observedAt());
        assertEquals(61.0, failures.content().value());
        assertEquals(ratio.observedAt(), failures.observedAt());
        assertEquals(61.0, contractErrors.content().value());
        assertEquals(ratio.observedAt(), contractErrors.observedAt());
        assertEquals(
                "multi_item_reservation",
                release.content().attributes().get("rollout_scope")
        );
        assertEquals("multi_item", mismatch.content().attributes().get("cart_segment"));
        assertEquals("3", mismatch.content().attributes().get("item_count"));
        assertTrue(trace.content().spans().stream().anyMatch(span ->
                "reserve-multi-item".equals(span.operation())
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "checkout-orders-at-risk-v1",
            "checkout-cart-segment-failures-v1"
    })
    void generalRunbooksAreNotScoredAsDirectRootCauseProof(String scenarioId)
            throws Exception {
        RecordedScenarioFixture fixture = readRecordedFixture(scenarioId);
        GroundTruth groundTruth = readGroundTruth(scenarioId);
        Set<String> runbookIds = fixture.evidenceInventory().stream()
                .filter(RunbookEvidence.class::isInstance)
                .map(Evidence::evidenceId)
                .collect(Collectors.toSet());

        Claim rootCause = fixture.recordedDiagnosis().claims().stream()
                .filter(claim -> "root_cause".equals(claim.claimCode().wireValue()))
                .findFirst()
                .orElseThrow();
        ClaimSupport rootCauseSupport = groundTruth.claimSupport().stream()
                .filter(support -> "root_cause".equals(
                        support.claimCode().wireValue()
                ))
                .findFirst()
                .orElseThrow();

        assertTrue(rootCause.evidenceIds().stream().noneMatch(runbookIds::contains));
        assertTrue(rootCauseSupport.allowedEvidenceIds().stream()
                .noneMatch(runbookIds::contains));
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

    private <T extends Evidence> T evidence(
            RecordedScenarioFixture fixture,
            String evidenceId,
            Class<T> type
    ) {
        Evidence evidence = fixture.evidenceInventory().stream()
                .filter(candidate -> evidenceId.equals(candidate.evidenceId()))
                .findFirst()
                .orElseThrow();
        return type.cast(evidence);
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
