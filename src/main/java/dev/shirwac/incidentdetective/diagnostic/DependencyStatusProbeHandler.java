package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.springframework.stereotype.Component;

import java.util.List;

/** Reports bounded dependency-span state from traces already in the case. */
@Component
public final class DependencyStatusProbeHandler implements DiagnosticProbeHandler {

    @Override
    public DiagnosticProbeId probeId() {
        return DiagnosticProbeId.DEPENDENCY_STATUS;
    }

    @Override
    public DiagnosticProbeReceipt inspect(InvestigationData data) {
        List<DiagnosticProbeFinding> findings = data.evidenceInventory().stream()
                .filter(TraceEvidence.class::isInstance)
                .map(TraceEvidence.class::cast)
                .flatMap(trace -> trace.content().spans().stream()
                        .map(span -> DiagnosticProbeSupport.finding(
                                "dependency_status",
                                span.service() + "/" + span.operation(),
                                DiagnosticProbeSupport.normalizedStatus(
                                        span.status()
                                ),
                                "Observed duration: " + span.durationMs()
                                        + " ms.",
                                List.of(trace.evidenceId())
                        )))
                .toList();
        if (findings.isEmpty()) {
            return DiagnosticProbeSupport.unavailable(
                    data.scenario().scenarioId(),
                    probeId(),
                    "No request-local trace evidence is available for this case.",
                    List.of(),
                    false
            );
        }
        return DiagnosticProbeSupport.observed(
                data.scenario().scenarioId(),
                probeId(),
                "Read dependency status from request-local trace spans.",
                findings,
                findings.size() > DiagnosticProbeReceipt.MAX_FINDINGS
        );
    }
}
