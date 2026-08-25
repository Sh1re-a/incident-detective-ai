package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public final class RetrieveRunbooksTool {

    private final InvestigationDataCatalog catalog;
    private final Validator validator;

    public RetrieveRunbooksTool(
            InvestigationDataCatalog catalog,
            Validator validator
    ) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public ToolName name() {
        return ToolName.RETRIEVE_RUNBOOKS;
    }

    public RetrieveRunbooksResult execute(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    ) {
        validate(arguments);
        InvestigationData data = catalog.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        List<RunbookEvidence> runbooks = data.evidenceInventory().stream()
                .filter(RunbookEvidence.class::isInstance)
                .map(RunbookEvidence.class::cast)
                .toList();
        List<String> queryTokens = tokens(arguments.query());

        List<ScoredRunbook> matches = runbooks.stream()
                .map(runbook -> new ScoredRunbook(runbook, score(runbook, queryTokens)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredRunbook::score)
                        .reversed()
                        .thenComparing(match -> match.runbook().evidenceId()))
                .toList();
        List<RunbookEvidence> evidence = matches.stream()
                .limit(arguments.maxResults())
                .map(ScoredRunbook::runbook)
                .toList();

        return new RetrieveRunbooksResult(
                runbooks.stream()
                        .map(runbook -> runbook.content().documentId())
                        .distinct()
                        .sorted()
                        .toList(),
                evidence,
                evidence.size(),
                matches.size() > evidence.size()
        );
    }

    private void validate(RetrieveRunbooksArguments arguments) {
        if (arguments == null) {
            throw new InvalidToolArgumentsException(name(), "arguments are required");
        }
        Set<ConstraintViolation<RetrieveRunbooksArguments>> violations =
                validator.validate(arguments);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidToolArgumentsException(name(), details);
        }
        if (tokens(arguments.query()).isEmpty()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "query must contain a searchable letter or digit"
            );
        }
    }

    private int score(RunbookEvidence runbook, List<String> queryTokens) {
        String searchable = String.join(" ",
                runbook.displaySummary(),
                runbook.content().documentId(),
                runbook.content().chunkId(),
                runbook.content().text()
        ).toLowerCase(Locale.ROOT);
        return (int) queryTokens.stream()
                .filter(searchable::contains)
                .count();
    }

    private List<String> tokens(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 3)
                .distinct()
                .toList();
    }

    private record ScoredRunbook(RunbookEvidence runbook, int score) {
    }
}
