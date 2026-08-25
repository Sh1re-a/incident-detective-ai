package dev.shirwac.incidentdetective.replay;

import jakarta.validation.Validation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class RecordedReplayServiceTest {

    @Autowired
    private JsonMapper jsonMapper;

    @ParameterizedTest
    @CsvSource({
            "checkout-orders-at-risk-v1, PAYMENT_TIMEOUT_CONFIG, cpt-v1-log-inventory-noise",
            "checkout-cart-segment-failures-v1, INVENTORY_SCHEMA_MISMATCH, cic-v1-log-catalog-noise"
    })
    void assemblesAnHonestReplayFromToolEvents(
            String scenarioId,
            String expectedRootCause,
            String unseenNoiseEvidenceId
    ) {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var repository = new ClasspathRecordedScenarioRepository(jsonMapper, validator);
        var service = new RecordedReplayService(
                repository,
                validator,
                Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC)
        );

        RecordedReplayResult result = service.play(scenarioId);

        assertNotNull(result.runId());
        assertEquals(scenarioId, result.scenarioId());
        assertEquals(RunMode.RECORDED_REPLAY, result.mode());
        assertEquals(RecordedReplayService.TRUTH_LABEL, result.truthLabel());
        assertEquals(ReplayRunStatus.COMPLETED, result.status());
        assertEquals(4, result.toolEvents().size());
        assertEquals(0, result.latencyMs());
        assertTrue(result.verification().hardErrors().isEmpty());
        assertTrue(result.verification().citationValidity().valid());
        assertEquals(expectedRootCause, result.comparison().expectedRootCauseCode());
        assertTrue(result.comparison().rootCauseCorrect());
        assertTrue(result.comparison().affectedServiceCorrect());

        Set<String> returnedEvidenceIds = result.toolEvents().stream()
                .flatMap(event -> event.evidence().stream())
                .map(evidence -> evidence.evidenceId())
                .collect(Collectors.toSet());
        assertFalse(returnedEvidenceIds.contains(unseenNoiseEvidenceId));

        assertNull(result.modelId());
        assertNull(result.promptVersion());
        assertNull(result.tokenUsage());
        assertNull(result.estimatedCostUsd());
    }
}
