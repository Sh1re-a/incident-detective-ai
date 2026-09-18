package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentFollowUpSnapshotProjectorTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
    private final IncidentFollowUpSnapshotProjector projector =
            new IncidentFollowUpSnapshotProjector();

    @Test
    void diagnosedSnapshotKeepsEvidenceBoundCauseAndPublicSources() throws Exception {
        var root = mapper.readTree(new ClassPathResource(
                "incident-lab/replays/test-golden-v1.json"
        ).getInputStream());
        IncidentLabRunResponse run = mapper.treeToValue(
                root.get("recorded_run"),
                IncidentLabRunResponse.class
        );

        IncidentFollowUpSnapshot snapshot = projector.project(
                run,
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        );

        assertEquals("CATALOG_SERVICE", snapshot.problemService());
        assertTrue(snapshot.sv().cause().startsWith(
                "Starkaste förklaringen i körningen:"
        ));
        assertFalse(snapshot.sv().cause().contains("kan inte fastställas"));
        assertTrue(snapshot.sources().stream().anyMatch(source ->
                "test-log-catalog-cache".equals(source.evidenceId())));
        assertTrue(snapshot.sources().stream().anyMatch(source ->
                "test-runbook-cache".equals(source.evidenceId())
                        && source.targetScene()
                        == IncidentFollowUpResponse.TargetScene.AGENT_RAG));
        assertFalse(snapshot.boundary().writeToolsAvailable());
        assertFalse(snapshot.boundary().actionExecuted());
    }

    @Test
    void withheldSnapshotNeverPromotesUnknownTextIntoCause() throws Exception {
        var root = mapper.readTree(new ClassPathResource(
                "incident-lab/replays/nordly-payment-timeout-withheld-2026-09-16.json"
        ).getInputStream());
        IncidentLabRunResponse run = mapper.treeToValue(
                root.get("recorded_run"),
                IncidentLabRunResponse.class
        );

        IncidentFollowUpSnapshot snapshot = projector.project(
                run,
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        );

        assertEquals(
                "Rotorsaken kan inte fastställas från det verifierade underlaget.",
                snapshot.sv().cause()
        );
        assertTrue(snapshot.sv().unknown().stream().anyMatch(value ->
                value.contains("Rotorsak")));
        assertFalse(snapshot.boundary().javaAnswerReleased());
    }
}
