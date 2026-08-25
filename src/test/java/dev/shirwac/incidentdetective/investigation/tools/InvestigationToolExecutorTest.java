package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class InvestigationToolExecutorTest {

    @Autowired
    private InvestigationToolExecutor executor;

    @Test
    void executesEveryAllowlistedToolThroughStrictArguments() {
        String scenarioId = "checkout-orders-at-risk-v1";
        List<ToolExecution> executions = List.of(
                executor.execute(scenarioId, call(
                        "call-metrics",
                        ToolName.GET_METRICS,
                        Map.of(
                                "metric_names", List.of("checkout_failure_ratio"),
                                "start", "2026-08-25T09:55:00Z",
                                "end", "2026-08-25T10:15:00Z"
                        )
                )),
                executor.execute(scenarioId, call(
                        "call-logs",
                        ToolName.SEARCH_LOGS,
                        Map.of(
                                "services", List.of("PAYMENT_ADAPTER"),
                                "levels", List.of(),
                                "query", "timeout",
                                "start", "2026-08-25T09:55:00Z",
                                "end", "2026-08-25T10:15:00Z"
                        )
                )),
                executor.execute(scenarioId, call(
                        "call-trace",
                        ToolName.GET_TRACE,
                        Map.of("trace_id", "cpt-trace-4821")
                )),
                executor.execute(scenarioId, call(
                        "call-runbook",
                        ToolName.RETRIEVE_RUNBOOKS,
                        Map.of("query", "payment timeout", "max_results", 4)
                ))
        );

        assertEquals(4, executions.size());
        assertEquals(
                List.of(
                        ToolName.GET_METRICS,
                        ToolName.SEARCH_LOGS,
                        ToolName.GET_TRACE,
                        ToolName.RETRIEVE_RUNBOOKS
                ),
                executions.stream().map(ToolExecution::toolName).toList()
        );
        assertEquals(List.of(
                        "cpt-v1-metric-checkout-failure-rate",
                        "cpt-v1-log-timeout-config",
                        "cpt-v1-log-timeout-error",
                        "cpt-v1-trace-failed-checkout",
                        "cpt-v1-runbook-timeout-precedence"
                ),
                executions.stream()
                        .flatMap(execution -> execution.evidence().stream())
                        .map(evidence -> evidence.evidenceId())
                        .toList()
        );
    }

    @Test
    void rejectsUnknownFieldsBeforeAReadOnlyToolRuns() {
        CollectionToolCall call = call(
                "call-invalid",
                ToolName.GET_TRACE,
                Map.of(
                        "trace_id", "cpt-trace-4821",
                        "ground_truth", true
                )
        );

        assertThrows(
                InvalidToolArgumentsException.class,
                () -> executor.execute("checkout-orders-at-risk-v1", call)
        );
    }

    private CollectionToolCall call(
            String id,
            ToolName toolName,
            Map<String, Object> arguments
    ) {
        return new CollectionToolCall(id, toolName, arguments);
    }
}
