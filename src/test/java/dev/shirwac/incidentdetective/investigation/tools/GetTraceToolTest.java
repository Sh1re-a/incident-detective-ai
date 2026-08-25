package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetTraceToolTest {

    @Autowired
    private GetTraceTool tool;

    @Autowired
    private InvestigationDataCatalog catalog;

    @Autowired
    private Validator validator;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void returnsOnlyTheExactRequestedTrace() {
        GetTraceResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetTraceArguments("cpt-trace-4821")
        );

        assertEquals(ToolName.GET_TRACE, tool.name());
        assertTrue(result.found());
        assertEquals("cpt-trace-4821", result.traceId());
        assertEquals(
                "cpt-v1-trace-failed-checkout",
                result.evidence().evidenceId()
        );
        assertEquals(
                "checkout-orders-at-risk-v1",
                result.evidence().scenarioId()
        );
    }

    @Test
    void doesNotTreatPrefixesOrEvidenceIdsAsTraceIds() {
        GetTraceResult prefixResult = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetTraceArguments("cpt-trace")
        );
        GetTraceResult evidenceIdResult = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetTraceArguments("cpt-v1-trace-failed-checkout")
        );

        assertFalse(prefixResult.found());
        assertNull(prefixResult.evidence());
        assertFalse(evidenceIdResult.found());
        assertNull(evidenceIdResult.evidence());
    }

    @Test
    void doesNotReturnATraceFromAnotherScenario() {
        GetTraceResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetTraceArguments("cic-trace-7712")
        );

        assertFalse(result.found());
        assertNull(result.evidence());
    }

    @Test
    void missingResultDoesNotExposeTheScenarioTraceInventory()
            throws Exception {
        GetTraceResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetTraceArguments("missing-trace-0000")
        );

        String json = jsonMapper.writeValueAsString(result);
        assertTrue(json.contains("missing-trace-0000"));
        assertFalse(json.contains("cpt-trace-4821"));
        assertFalse(json.contains("cpt-v1-trace-failed-checkout"));
        assertFalse(json.contains("available_trace"));
    }

    @Test
    void rejectsInvalidArgumentsBeforeReadingEvidence() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute("checkout-orders-at-risk-v1", null)
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetTraceArguments("")
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetTraceArguments("CPT trace 4821")
                )
        );
    }

    @Test
    void rejectsAnUnknownScenario() {
        assertThrows(
                InvestigationScenarioNotFoundException.class,
                () -> tool.execute(
                        "missing-scenario",
                        new GetTraceArguments("cpt-trace-4821")
                )
        );
    }

    @Test
    void choosesDeterministicallyWhenInventoryContainsDuplicateTraceIds() {
        InvestigationData original = catalog.findById(
                "checkout-orders-at-risk-v1"
        ).orElseThrow();
        TraceEvidence later = trace(
                "trace-evidence-z",
                original.scenario().scenarioId(),
                "duplicate-trace",
                Instant.parse("2026-08-25T10:05:00Z")
        );
        TraceEvidence earlier = trace(
                "trace-evidence-a",
                original.scenario().scenarioId(),
                "duplicate-trace",
                Instant.parse("2026-08-25T10:04:00Z")
        );
        GetTraceTool boundedTool = new GetTraceTool(
                scenarioId -> Optional.of(new InvestigationData(
                        original.scenario(),
                        List.of(later, earlier)
                )),
                validator
        );

        GetTraceResult result = boundedTool.execute(
                original.scenario().scenarioId(),
                new GetTraceArguments("duplicate-trace")
        );

        assertEquals("trace-evidence-a", result.evidence().evidenceId());
    }

    @Test
    void ignoresMislabeledCrossScenarioEvidenceInsideTheCatalogBoundary() {
        InvestigationData original = catalog.findById(
                "checkout-orders-at-risk-v1"
        ).orElseThrow();
        TraceEvidence foreignTrace = trace(
                "foreign-trace-evidence",
                "checkout-cart-segment-failures-v1",
                "foreign-trace",
                Instant.parse("2026-08-25T10:04:00Z")
        );
        GetTraceTool isolatedTool = new GetTraceTool(
                scenarioId -> Optional.of(new InvestigationData(
                        original.scenario(),
                        List.of(foreignTrace)
                )),
                validator
        );

        GetTraceResult result = isolatedTool.execute(
                original.scenario().scenarioId(),
                new GetTraceArguments("foreign-trace")
        );

        assertFalse(result.found());
        assertNull(result.evidence());
    }

    private TraceEvidence trace(
            String evidenceId,
            String scenarioId,
            String traceId,
            Instant observedAt
    ) {
        return new TraceEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                "Synthetic trace for tool ordering test",
                "traces/" + traceId,
                new TraceEvidence.TraceContent(
                        traceId,
                        List.of(new TraceEvidence.TraceSpan(
                                "span-001",
                                "CHECKOUT_API",
                                "test-operation",
                                10,
                                "OK"
                        ))
                )
        );
    }
}
