package dev.shirwac.incidentdetective.incidentlab.replay;

final class IncidentLabReplayUnavailableException extends RuntimeException {

    IncidentLabReplayUnavailableException() {
        super("No verified recorded Incident Lab run has been published yet.");
    }
}
