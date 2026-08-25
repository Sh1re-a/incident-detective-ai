package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class GetMetricsToolTest {

    private static final Instant START = Instant.parse("2026-08-25T09:55:00Z");
    private static final Instant END = Instant.parse("2026-08-25T10:15:00Z");

    @Autowired
    private GetMetricsTool tool;

    @Autowired
    private InvestigationDataCatalog catalog;

    @Autowired
    private Validator validator;

    @Test
    void returnsOnlyRequestedMetricsInDeterministicOrder() {
        GetMetricsResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetMetricsArguments(
                        List.of("failed_checkout_attempts", "checkout_failure_ratio"),
                        START,
                        END
                )
        );

        assertEquals(ToolName.GET_METRICS, tool.name());
        assertEquals(
                List.of(
                        "cpt-v1-metric-checkout-failure-rate",
                        "cpt-v1-metric-failed-checkouts"
                ),
                result.evidence().stream().map(metric -> metric.evidenceId()).toList()
        );
        assertTrue(result.evidence().stream()
                .allMatch(metric -> metric.scenarioId()
                        .equals("checkout-orders-at-risk-v1")));
        assertEquals(2, result.returnedCount());
        assertTrue(result.unknownMetricNames().isEmpty());
        assertTrue(result.availableMetricNames()
                .contains("checkout_failure_ratio"));
        assertFalse(result.truncated());
    }

    @Test
    void appliesTheRequestedTimeWindow() {
        GetMetricsResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetMetricsArguments(
                        List.of("payment_authorization_duration_p95"),
                        Instant.parse("2026-08-25T10:04:00Z"),
                        Instant.parse("2026-08-25T10:06:00Z")
                )
        );

        assertEquals(
                List.of("cpt-v1-metric-payment-p95"),
                result.evidence().stream().map(metric -> metric.evidenceId()).toList()
        );
    }

    @Test
    void returnsAnEmptyResultForAnUnknownMetric() {
        GetMetricsResult result = tool.execute(
                "checkout-orders-at-risk-v1",
                new GetMetricsArguments(List.of("unknown_metric"), START, END)
        );

        assertTrue(result.evidence().isEmpty());
        assertEquals(List.of("unknown_metric"), result.unknownMetricNames());
        assertEquals(
                List.of(
                        "checkout_failure_ratio",
                        "failed_checkout_attempts",
                        "payment_authorization_duration_p95"
                ),
                result.availableMetricNames()
        );
        assertEquals(0, result.returnedCount());
        assertFalse(result.truncated());
    }

    @Test
    void rejectsInvalidArgumentsBeforeReadingEvidence() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetMetricsArguments(List.of(), START, END)
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetMetricsArguments(
                                List.of(
                                        "checkout_failure_ratio",
                                        "checkout_failure_ratio"
                                ),
                                START,
                                END
                        )
                )
        );
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetMetricsArguments(List.of("checkout_failure_ratio"), END, START)
                )
        );
    }

    @Test
    void rejectsWindowsOutsideTheScenario() {
        assertThrows(
                InvalidToolArgumentsException.class,
                () -> tool.execute(
                        "checkout-orders-at-risk-v1",
                        new GetMetricsArguments(
                                List.of("checkout_failure_ratio"),
                                Instant.parse("2026-08-25T09:54:59Z"),
                                END
                        )
                )
        );
    }

    @Test
    void rejectsAnUnknownScenario() {
        assertThrows(
                InvestigationScenarioNotFoundException.class,
                () -> tool.execute(
                        "missing-scenario",
                        new GetMetricsArguments(
                                List.of("checkout_failure_ratio"),
                                START,
                                END
                        )
                )
        );
    }

    @Test
    void capsDenseResultsAndReportsTruncation() {
        InvestigationData original = catalog.findById("checkout-orders-at-risk-v1")
                .orElseThrow();
        List<MetricEvidence> denseEvidence = IntStream
                .range(0, GetMetricsTool.MAX_EVIDENCE_ITEMS + 5)
                .mapToObj(index -> new MetricEvidence(
                        "dense-metric-%02d".formatted(index),
                        original.scenario().scenarioId(),
                        START.plusSeconds(index),
                        "Synthetic dense metric point " + index,
                        "fixture://dense-metrics/" + index,
                        new MetricEvidence.MetricContent(
                                "checkout_failure_ratio",
                                index,
                                "ratio",
                                Map.of("service", "CHECKOUT_API")
                        )
                ))
                .toList();
        GetMetricsTool boundedTool = new GetMetricsTool(
                scenarioId -> Optional.of(new InvestigationData(
                        original.scenario(),
                        List.copyOf(denseEvidence)
                )),
                validator
        );

        GetMetricsResult result = boundedTool.execute(
                original.scenario().scenarioId(),
                new GetMetricsArguments(
                        List.of("checkout_failure_ratio"),
                        START,
                        END
                )
        );

        assertEquals(GetMetricsTool.MAX_EVIDENCE_ITEMS, result.returnedCount());
        assertEquals(GetMetricsTool.MAX_EVIDENCE_ITEMS, result.evidence().size());
        assertTrue(result.truncated());
    }
}
