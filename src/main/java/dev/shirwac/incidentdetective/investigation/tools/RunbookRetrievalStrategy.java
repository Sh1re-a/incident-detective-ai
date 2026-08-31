package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.investigation.InvestigationData;

public interface RunbookRetrievalStrategy {

    RetrieveRunbooksResult retrieve(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    );

    default RetrieveRunbooksResult retrieve(
            InvestigationData data,
            RetrieveRunbooksArguments arguments
    ) {
        return retrieve(data.scenario().scenarioId(), arguments);
    }

    RunbookRetrievalBackend backend();

    String safeModeDescription();

    String limitation();
}
