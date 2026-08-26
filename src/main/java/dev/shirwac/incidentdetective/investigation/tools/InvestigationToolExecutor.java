package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Service
public final class InvestigationToolExecutor {

    private final GetMetricsTool getMetrics;
    private final SearchLogsTool searchLogs;
    private final GetTraceTool getTrace;
    private final RetrieveRunbooksTool retrieveRunbooks;
    private final JsonMapper jsonMapper;

    public InvestigationToolExecutor(
            GetMetricsTool getMetrics,
            SearchLogsTool searchLogs,
            GetTraceTool getTrace,
            RetrieveRunbooksTool retrieveRunbooks,
            JsonMapper jsonMapper
    ) {
        this.getMetrics = getMetrics;
        this.searchLogs = searchLogs;
        this.getTrace = getTrace;
        this.retrieveRunbooks = retrieveRunbooks;
        this.jsonMapper = jsonMapper;
    }

    public List<String> availableMetricNames(String scenarioId) {
        return getMetrics.availableMetricNames(scenarioId);
    }

    public ToolExecution execute(
            String scenarioId,
            CollectionToolCall call
    ) {
        return switch (call.toolName()) {
            case GET_METRICS -> metrics(scenarioId, call);
            case SEARCH_LOGS -> logs(scenarioId, call);
            case GET_TRACE -> trace(scenarioId, call);
            case RETRIEVE_RUNBOOKS -> runbooks(scenarioId, call);
        };
    }

    private ToolExecution metrics(
            String scenarioId,
            CollectionToolCall call
    ) {
        GetMetricsResult result = getMetrics.execute(
                scenarioId,
                decode(call, GetMetricsArguments.class)
        );
        return execution(
                call,
                "Returned " + result.returnedCount()
                        + " bounded metric evidence item(s).",
                List.copyOf(result.evidence())
        );
    }

    private ToolExecution logs(
            String scenarioId,
            CollectionToolCall call
    ) {
        SearchLogsResult result = searchLogs.execute(
                scenarioId,
                decode(call, SearchLogsArguments.class)
        );
        return execution(
                call,
                "Returned " + result.returnedCount()
                        + " bounded log evidence item(s).",
                List.copyOf(result.evidence())
        );
    }

    private ToolExecution trace(
            String scenarioId,
            CollectionToolCall call
    ) {
        GetTraceResult result = getTrace.execute(
                scenarioId,
                decode(call, GetTraceArguments.class)
        );
        List<Evidence> evidence = result.found()
                ? List.of(result.evidence())
                : List.of();
        return execution(
                call,
                result.found()
                        ? "Returned one exact synthetic trace."
                        : "No synthetic trace matched the exact trace ID.",
                evidence
        );
    }

    private ToolExecution runbooks(
            String scenarioId,
            CollectionToolCall call
    ) {
        RetrieveRunbooksResult result = retrieveRunbooks.execute(
                scenarioId,
                decode(call, RetrieveRunbooksArguments.class)
        );
        return execution(
                call,
                "Returned " + result.returnedCount()
                        + " bounded runbook chunk(s) using "
                        + retrieveRunbooks.safeModeDescription() + ".",
                List.copyOf(result.evidence())
        );
    }

    public String runbookRetrievalLimitation() {
        return retrieveRunbooks.limitation();
    }

    private ToolExecution execution(
            CollectionToolCall call,
            String safeSummary,
            List<Evidence> evidence
    ) {
        return new ToolExecution(
                call.callId(),
                call.toolName(),
                call.arguments(),
                safeSummary,
                evidence
        );
    }

    private <T> T decode(CollectionToolCall call, Class<T> type) {
        try {
            ObjectReader reader = jsonMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            return reader.readValue(jsonMapper.writeValueAsString(call.arguments()));
        } catch (Exception exception) {
            throw new InvalidToolArgumentsException(
                    call.toolName(),
                    "model arguments did not match the strict tool schema"
            );
        }
    }
}
