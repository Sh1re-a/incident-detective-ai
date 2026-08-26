package dev.shirwac.incidentdetective.investigation.tools;

public interface RunbookRetrievalStrategy {

    RetrieveRunbooksResult retrieve(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    );

    String safeModeDescription();

    String limitation();
}
