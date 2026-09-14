package dev.shirwac.incidentdetective.incidentlab;

final class InvalidIncidentLabPlanException extends RuntimeException {

    InvalidIncidentLabPlanException() {
        super("The submitted incident plan is not the canonical runnable v1 plan");
    }
}
