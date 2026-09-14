package dev.shirwac.incidentdetective.planning;

/**
 * AI seam only: implementations propose plans but never execute incidents.
 * Callers must apply live-call admission and {@link IncidentPlanValidator}
 * before any synthetic generator is invoked.
 */
public interface IncidentPlannerGateway {

    IncidentPlannerResponse propose(IncidentPlanningRequest request);
}
