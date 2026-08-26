package dev.shirwac.incidentdetective.rag;

import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.RetrieveRunbooksArguments;
import dev.shirwac.incidentdetective.investigation.tools.RetrieveRunbooksResult;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("rag")
public final class PgvectorRunbookRetrievalStrategy
        implements RunbookRetrievalStrategy {

    private final InvestigationScenarioCatalog scenarios;
    private final ClasspathRunbookCorpus corpus;
    private final RunbookVectorStore store;
    private final EmbeddingGateway embeddings;
    private final RagProperties properties;
    private final RunbookIndexReadiness indexReadiness;

    public PgvectorRunbookRetrievalStrategy(
            InvestigationScenarioCatalog scenarios,
            ClasspathRunbookCorpus corpus,
            RunbookVectorStore store,
            EmbeddingGateway embeddings,
            RagProperties properties,
            RunbookIndexReadiness indexReadiness
    ) {
        this.scenarios = scenarios;
        this.corpus = corpus;
        this.store = store;
        this.embeddings = embeddings;
        this.properties = properties;
        this.indexReadiness = indexReadiness;
    }

    @Override
    public RetrieveRunbooksResult retrieve(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    ) {
        requireScenario(scenarioId);
        RunbookIndexStatus indexStatus = indexReadiness.requireReady();

        EmbeddingResult queryEmbedding = embeddings.embedQuery(arguments.query());
        List<RunbookSearchHit> hits = store.search(
                corpus.version(),
                properties,
                queryEmbedding.values(),
                arguments.maxResults(),
                properties.minimumSimilarity()
        );
        List<RunbookEvidence> evidence = hits.stream()
                .map(hit -> hit.entry().asEvidence(scenarioId))
                .toList();
        return new RetrieveRunbooksResult(
                store.documentIds(corpus.version(), properties),
                evidence,
                evidence.size(),
                evidence.size() == arguments.maxResults()
                        && indexStatus.indexedChunks() > evidence.size()
        );
    }

    @Override
    public String safeModeDescription() {
        return "Gemini embeddings with exact pgvector cosine retrieval";
    }

    @Override
    public String limitation() {
        return "Runbook retrieval searches a small synthetic corpus, not company knowledge.";
    }

    private void requireScenario(String scenarioId) {
        if (scenarios.findById(scenarioId).isEmpty()) {
            throw new InvestigationScenarioNotFoundException(scenarioId);
        }
    }
}
