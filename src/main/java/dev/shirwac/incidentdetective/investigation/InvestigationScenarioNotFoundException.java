package dev.shirwac.incidentdetective.investigation;

public final class InvestigationScenarioNotFoundException extends RuntimeException {

    public InvestigationScenarioNotFoundException(String scenarioId) {
        super("Investigation scenario not found: " + scenarioId);
    }
}
