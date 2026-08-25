package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.scenario.TimeWindow;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public final class GetMetricsTool {

    static final int MAX_EVIDENCE_ITEMS = 20;

    private final InvestigationDataCatalog catalog;
    private final Validator validator;

    public GetMetricsTool(InvestigationDataCatalog catalog, Validator validator) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public ToolName name() {
        return ToolName.GET_METRICS;
    }

    public GetMetricsResult execute(
            String scenarioId,
            GetMetricsArguments arguments
    ) {
        validate(arguments);
        InvestigationData data = catalog.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        requireScenarioWindow(data.scenario().timeWindow(), arguments);

        List<String> availableMetricNames = availableMetricNames(data);
        Set<String> availableNames = Set.copyOf(availableMetricNames);
        Set<String> requestedNames = new HashSet<>(arguments.metricNames());
        List<String> unknownMetricNames = requestedNames.stream()
                .filter(name -> !availableNames.contains(name))
                .sorted()
                .toList();

        List<MetricEvidence> matches = data.evidenceInventory().stream()
                .filter(MetricEvidence.class::isInstance)
                .map(MetricEvidence.class::cast)
                .filter(metric -> requestedNames.contains(metric.content().metricName()))
                .filter(metric -> insideRequestedWindow(metric.observedAt(), arguments))
                .sorted(Comparator.comparing(MetricEvidence::observedAt)
                        .thenComparing(MetricEvidence::evidenceId))
                .toList();
        List<MetricEvidence> evidence = matches.stream()
                .limit(MAX_EVIDENCE_ITEMS)
                .toList();

        return new GetMetricsResult(
                availableMetricNames,
                unknownMetricNames,
                evidence,
                evidence.size(),
                matches.size() > evidence.size()
        );
    }

    public List<String> availableMetricNames(String scenarioId) {
        InvestigationData data = catalog.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        return availableMetricNames(data);
    }

    private void validate(GetMetricsArguments arguments) {
        if (arguments == null) {
            throw new InvalidToolArgumentsException(name(), "arguments are required");
        }

        Set<ConstraintViolation<GetMetricsArguments>> violations =
                validator.validate(arguments);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidToolArgumentsException(name(), details);
        }
        if (!arguments.start().isBefore(arguments.end())) {
            throw new InvalidToolArgumentsException(name(), "start must be before end");
        }
        if (new HashSet<>(arguments.metricNames()).size()
                != arguments.metricNames().size()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "metric_names must not contain duplicates"
            );
        }
    }

    private void requireScenarioWindow(
            TimeWindow scenarioWindow,
            GetMetricsArguments arguments
    ) {
        if (arguments.start().isBefore(scenarioWindow.start())
                || arguments.end().isAfter(scenarioWindow.end())) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "requested window must stay inside the scenario window"
            );
        }
    }

    private boolean insideRequestedWindow(
            Instant observedAt,
            GetMetricsArguments arguments
    ) {
        return !observedAt.isBefore(arguments.start())
                && !observedAt.isAfter(arguments.end());
    }

    private List<String> availableMetricNames(InvestigationData data) {
        return data.evidenceInventory().stream()
                .filter(MetricEvidence.class::isInstance)
                .map(MetricEvidence.class::cast)
                .map(metric -> metric.content().metricName())
                .distinct()
                .sorted()
                .toList();
    }
}
