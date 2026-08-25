package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
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
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public final class SearchLogsTool {

    static final int MAX_EVIDENCE_ITEMS = 20;

    private static final Pattern SCENARIO_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]{1,127}$");
    private static final Pattern SEARCHABLE_QUERY_PATTERN =
            Pattern.compile("[A-Za-z0-9]");

    private final InvestigationDataCatalog catalog;
    private final Validator validator;

    public SearchLogsTool(InvestigationDataCatalog catalog, Validator validator) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public ToolName name() {
        return ToolName.SEARCH_LOGS;
    }

    public SearchLogsResult execute(
            String scenarioId,
            SearchLogsArguments arguments
    ) {
        validate(arguments);
        validateScenarioId(scenarioId);

        InvestigationData data = catalog.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        requireMatchingScenario(scenarioId, data);
        requireScenarioWindow(data.scenario().timeWindow(), arguments);

        List<LogEvidence> scenarioLogs = scenarioLogs(data);
        List<String> availableServices = availableServices(scenarioLogs);
        List<String> availableLevels = availableLevels(scenarioLogs);
        Set<String> requestedServices = new HashSet<>(arguments.services());
        Set<String> requestedLevels = new HashSet<>(arguments.levels());

        List<String> unknownServices = unknownValues(
                requestedServices,
                availableServices
        );
        List<String> unknownLevels = unknownValues(
                requestedLevels,
                availableLevels
        );

        String normalizedQuery = arguments.query().strip()
                .toLowerCase(Locale.ROOT);
        List<LogEvidence> matches = scenarioLogs.stream()
                .filter(log -> requestedServices.isEmpty()
                        || requestedServices.contains(log.content().service()))
                .filter(log -> requestedLevels.isEmpty()
                        || requestedLevels.contains(log.content().level()))
                .filter(log -> insideRequestedWindow(log.observedAt(), arguments))
                .filter(log -> containsQuery(log, normalizedQuery))
                .sorted(Comparator.comparing(LogEvidence::observedAt)
                        .thenComparing(LogEvidence::evidenceId))
                .toList();
        List<LogEvidence> evidence = matches.stream()
                .limit(MAX_EVIDENCE_ITEMS)
                .toList();

        return new SearchLogsResult(
                availableServices,
                availableLevels,
                unknownServices,
                unknownLevels,
                evidence,
                evidence.size(),
                matches.size() > evidence.size()
        );
    }

    private void validate(SearchLogsArguments arguments) {
        if (arguments == null) {
            throw new InvalidToolArgumentsException(name(), "arguments are required");
        }

        Set<ConstraintViolation<SearchLogsArguments>> violations =
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
        if (!SEARCHABLE_QUERY_PATTERN.matcher(arguments.query()).find()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "query must contain a searchable letter or digit"
            );
        }
        requireNoDuplicates(arguments.services(), "services");
        requireNoDuplicates(arguments.levels(), "levels");
    }

    private void validateScenarioId(String scenarioId) {
        if (scenarioId == null || !SCENARIO_ID_PATTERN.matcher(scenarioId).matches()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "scenario_id must use lowercase letters, numbers, and hyphens"
            );
        }
    }

    private void requireNoDuplicates(List<String> values, String fieldName) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    fieldName + " must not contain duplicates"
            );
        }
    }

    private void requireMatchingScenario(
            String scenarioId,
            InvestigationData data
    ) {
        if (!scenarioId.equals(data.scenario().scenarioId())) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "catalog returned a different scenario_id"
            );
        }
    }

    private void requireScenarioWindow(
            TimeWindow scenarioWindow,
            SearchLogsArguments arguments
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
            SearchLogsArguments arguments
    ) {
        return !observedAt.isBefore(arguments.start())
                && !observedAt.isAfter(arguments.end());
    }

    private boolean containsQuery(LogEvidence log, String normalizedQuery) {
        if (log.content().message().toLowerCase(Locale.ROOT)
                .contains(normalizedQuery)) {
            return true;
        }
        return log.content().attributes().entrySet().stream()
                .anyMatch(attribute -> attribute.getKey()
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedQuery)
                        || attribute.getValue()
                        .toLowerCase(Locale.ROOT)
                        .contains(normalizedQuery));
    }

    private List<LogEvidence> scenarioLogs(InvestigationData data) {
        String scenarioId = data.scenario().scenarioId();
        return data.evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .filter(log -> scenarioId.equals(log.scenarioId()))
                .toList();
    }

    private List<String> availableServices(List<LogEvidence> logs) {
        return logs.stream()
                .map(log -> log.content().service())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> availableLevels(List<LogEvidence> logs) {
        return logs.stream()
                .map(log -> log.content().level())
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> unknownValues(
            Set<String> requestedValues,
            List<String> availableValues
    ) {
        Set<String> available = Set.copyOf(availableValues);
        return requestedValues.stream()
                .filter(value -> !available.contains(value))
                .sorted()
                .toList();
    }
}
