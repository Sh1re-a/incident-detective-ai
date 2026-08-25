package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SearchLogsToolTest {

    private static final String SCENARIO_ID = "checkout-orders-at-risk-v1";
    private static final Instant START = Instant.parse("2026-08-25T09:55:00Z");
    private static final Instant END = Instant.parse("2026-08-25T10:15:00Z");

    @Autowired
    private SearchLogsTool tool;

    @Autowired
    private InvestigationDataCatalog catalog;

    @Autowired
    private Validator validator;

    @Test
    void filtersSyntheticLogsAndReturnsThemInDeterministicOrder() {
        SearchLogsResult result = tool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("PAYMENT_ADAPTER"),
                        List.of("INFO"),
                        "ReLeAsE",
                        START,
                        END
                )
        );

        assertEquals(ToolName.SEARCH_LOGS, tool.name());
        assertEquals(
                List.of(
                        "cpt-v1-log-release",
                        "cpt-v1-log-timeout-config"
                ),
                result.evidence().stream().map(LogEvidence::evidenceId).toList()
        );
        assertTrue(result.evidence().stream()
                .allMatch(log -> log.scenarioId().equals(SCENARIO_ID)));
        assertTrue(result.evidence().stream()
                .allMatch(log -> log.content().service().equals("PAYMENT_ADAPTER")));
        assertTrue(result.evidence().stream()
                .allMatch(log -> log.content().level().equals("INFO")));
        assertEquals(2, result.returnedCount());
        assertFalse(result.truncated());
    }

    @Test
    void matchesAQueryAgainstAttributeKeysAndValues() {
        SearchLogsResult keyMatch = tool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("PAYMENT_ADAPTER"),
                        List.of("INFO"),
                        "previous_value",
                        START,
                        END
                )
        );
        SearchLogsResult valueMatch = tool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("PAYMENT_ADAPTER"),
                        List.of(),
                        "2000",
                        START,
                        END
                )
        );

        assertEquals(
                List.of("cpt-v1-log-timeout-config"),
                keyMatch.evidence().stream().map(LogEvidence::evidenceId).toList()
        );
        assertEquals(
                List.of(
                        "cpt-v1-log-timeout-config",
                        "cpt-v1-log-timeout-error"
                ),
                valueMatch.evidence().stream().map(LogEvidence::evidenceId).toList()
        );
    }

    @Test
    void appliesTheRequestedTimeWindow() {
        SearchLogsResult result = tool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("PAYMENT_ADAPTER"),
                        List.of(),
                        "timeout",
                        Instant.parse("2026-08-25T10:04:00Z"),
                        Instant.parse("2026-08-25T10:05:00Z")
                )
        );

        assertEquals(
                List.of("cpt-v1-log-timeout-error"),
                result.evidence().stream().map(LogEvidence::evidenceId).toList()
        );
    }

    @Test
    void reportsAvailableAndUnknownFiltersForAnEmptyResult() {
        SearchLogsResult result = tool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("MISSING_SERVICE"),
                        List.of("DEBUG"),
                        "not-present",
                        START,
                        END
                )
        );

        assertTrue(result.evidence().isEmpty());
        assertEquals(
                List.of("INVENTORY_SERVICE", "PAYMENT_ADAPTER"),
                result.availableServices()
        );
        assertEquals(List.of("ERROR", "INFO", "WARN"), result.availableLevels());
        assertEquals(List.of("MISSING_SERVICE"), result.unknownServices());
        assertEquals(List.of("DEBUG"), result.unknownLevels());
        assertEquals(0, result.returnedCount());
        assertFalse(result.truncated());
    }

    @Test
    void rejectsInvalidArgumentsBeforeReadingEvidence() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(SCENARIO_ID, null)
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(List.of(), List.of(), " ", START, END)
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(List.of(), List.of(), "...", START, END)
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(
                                List.of("payment-adapter"),
                                List.of(),
                                "timeout",
                                START,
                                END
                        )
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(
                                List.of(),
                                List.of("error"),
                                "timeout",
                                START,
                                END
                        )
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(
                                List.of("PAYMENT_ADAPTER", "PAYMENT_ADAPTER"),
                                List.of(),
                                "timeout",
                                START,
                                END
                        )
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(
                                List.of(),
                                List.of("ERROR", "ERROR"),
                                "timeout",
                                START,
                                END
                        )
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(List.of(), List.of(), "x".repeat(161), START, END)
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(List.of(), List.of(), "timeout", END, START)
                )
        );
    }

    @Test
    void rejectsInvalidAndUnknownScenarios() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "../hidden-ground-truth",
                        arguments(List.of(), List.of(), "timeout", START, END)
                )
        );
        assertThrows(
                InvestigationScenarioNotFoundException.class,
                () -> tool.execute(
                        "missing-scenario",
                        arguments(List.of(), List.of(), "timeout", START, END)
                )
        );
    }

    @Test
    void rejectsWindowsOutsideTheScenario() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        SCENARIO_ID,
                        arguments(
                                List.of(),
                                List.of(),
                                "timeout",
                                START.minusSeconds(1),
                                END
                        )
                )
        );
    }

    @Test
    void capsDenseResultsAndNeverReturnsOtherEvidenceTypesOrScenarios() {
        InvestigationData original = catalog.findById(SCENARIO_ID).orElseThrow();
        List<Evidence> denseEvidence = new ArrayList<>(IntStream
                .range(0, SearchLogsTool.MAX_EVIDENCE_ITEMS + 5)
                .mapToObj(index -> (Evidence) new LogEvidence(
                        "dense-log-%02d".formatted(index),
                        SCENARIO_ID,
                        START.plusSeconds(index),
                        "Synthetic dense log " + index,
                        "fixture://dense-logs/" + index,
                        new LogEvidence.LogContent(
                                "CHECKOUT_API",
                                "ERROR",
                                "Dense synthetic failure " + index,
                                Map.of("batch", "dense")
                        )
                ))
                .toList());
        denseEvidence.add(new LogEvidence(
                "foreign-log",
                "another-scenario",
                START,
                "Foreign synthetic log",
                "fixture://foreign-log",
                new LogEvidence.LogContent(
                        "CHECKOUT_API",
                        "ERROR",
                        "Dense foreign failure",
                        Map.of()
                )
        ));
        denseEvidence.add(new MetricEvidence(
                "dense-metric",
                SCENARIO_ID,
                START,
                "Synthetic metric decoy",
                "fixture://dense-metric",
                new MetricEvidence.MetricContent(
                        "checkout_failure_ratio",
                        1,
                        "ratio",
                        Map.of()
                )
        ));
        Collections.reverse(denseEvidence);

        SearchLogsTool boundedTool = new SearchLogsTool(
                scenarioId -> Optional.of(new InvestigationData(
                        original.scenario(),
                        denseEvidence
                )),
                validator
        );

        SearchLogsResult result = boundedTool.execute(
                SCENARIO_ID,
                arguments(
                        List.of("CHECKOUT_API"),
                        List.of("ERROR"),
                        "dense",
                        START,
                        END
                )
        );

        assertEquals(SearchLogsTool.MAX_EVIDENCE_ITEMS, result.returnedCount());
        assertEquals(
                IntStream.range(0, SearchLogsTool.MAX_EVIDENCE_ITEMS)
                        .mapToObj(index -> "dense-log-%02d".formatted(index))
                        .toList(),
                result.evidence().stream().map(LogEvidence::evidenceId).toList()
        );
        assertTrue(result.truncated());
    }

    private SearchLogsArguments arguments(
            List<String> services,
            List<String> levels,
            String query,
            Instant start,
            Instant end
    ) {
        return new SearchLogsArguments(services, levels, query, start, end);
    }
}
