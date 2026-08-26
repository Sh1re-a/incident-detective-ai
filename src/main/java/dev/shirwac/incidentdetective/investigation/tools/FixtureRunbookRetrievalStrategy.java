package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Profile("!rag")
public final class FixtureRunbookRetrievalStrategy
        implements RunbookRetrievalStrategy {

    private final InvestigationDataCatalog catalog;

    public FixtureRunbookRetrievalStrategy(InvestigationDataCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public RetrieveRunbooksResult retrieve(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    ) {
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

    @Override
    public String safeModeDescription() {
        return "deterministic fixture retrieval";
    }

    @Override
    public String limitation() {
        return "Runbook retrieval uses deterministic fixture matching in this mode, not pgvector.";
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
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 3)
                .distinct()
                .toList();
    }

    private record ScoredRunbook(RunbookEvidence runbook, int score) {
    }
}
