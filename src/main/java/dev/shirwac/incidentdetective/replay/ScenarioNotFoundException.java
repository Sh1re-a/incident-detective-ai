package dev.shirwac.incidentdetective.replay;

public final class ScenarioNotFoundException extends RuntimeException {

    public ScenarioNotFoundException(String scenarioId) {
        super("Recorded scenario not found: " + scenarioId);
    }
}
