package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.investigation.InvestigationData;

/** Internal fixed-function handler that can inspect request-local evidence only. */
public interface DiagnosticProbeHandler {

    DiagnosticProbeId probeId();

    DiagnosticProbeReceipt inspect(InvestigationData data);
}
