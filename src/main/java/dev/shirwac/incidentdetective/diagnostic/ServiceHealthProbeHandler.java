package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Summarizes failure signals without claiming that absent evidence means healthy. */
@Component
public final class ServiceHealthProbeHandler implements DiagnosticProbeHandler {

    @Override
    public DiagnosticProbeId probeId() {
        return DiagnosticProbeId.SERVICE_HEALTH;
    }

    @Override
    public DiagnosticProbeReceipt inspect(InvestigationData data) {
        Map<String, Signals> byService = new LinkedHashMap<>();
        data.scenario().affectedServices().forEach(
                service -> byService.put(service, new Signals())
        );

        data.evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .forEach(log -> addLogSignal(byService, log));
        data.evidenceInventory().stream()
                .filter(TraceEvidence.class::isInstance)
                .map(TraceEvidence.class::cast)
                .forEach(trace -> addTraceSignals(byService, trace));

        List<DiagnosticProbeFinding> findings = byService.entrySet().stream()
                .map(entry -> finding(entry.getKey(), entry.getValue()))
                .toList();
        boolean truncated = findings.size() > DiagnosticProbeReceipt.MAX_FINDINGS
                || byService.values().stream().anyMatch(Signals::evidenceTruncated);
        return DiagnosticProbeSupport.observed(
                data.scenario().scenarioId(),
                probeId(),
                "Inspected request-local failure signals for "
                        + byService.size() + " affected service(s).",
                findings,
                truncated
        );
    }

    private void addLogSignal(
            Map<String, Signals> byService,
            LogEvidence log
    ) {
        Signals signals = byService.get(log.content().service());
        if (signals == null) {
            return;
        }
        if ("ERROR".equalsIgnoreCase(log.content().level())) {
            signals.errors++;
            signals.evidenceIds.add(log.evidenceId());
        } else if ("WARN".equalsIgnoreCase(log.content().level())) {
            signals.warnings++;
            signals.evidenceIds.add(log.evidenceId());
        }
    }

    private void addTraceSignals(
            Map<String, Signals> byService,
            TraceEvidence trace
    ) {
        trace.content().spans().forEach(span -> {
            Signals signals = byService.get(span.service());
            if (signals != null
                    && !DiagnosticProbeSupport.isSuccessfulTraceStatus(
                    span.status()
            )) {
                signals.nonSuccessSpans++;
                signals.evidenceIds.add(trace.evidenceId());
            }
        });
    }

    private DiagnosticProbeFinding finding(String service, Signals signals) {
        String status;
        if (signals.errors > 0 || signals.nonSuccessSpans > 0) {
            status = "degraded";
        } else if (signals.warnings > 0) {
            status = "warning";
        } else {
            status = "no_failure_signal_in_case";
        }
        return DiagnosticProbeSupport.finding(
                "service_health",
                service,
                status,
                signals.errors + " error log(s), "
                        + signals.warnings + " warning log(s), and "
                        + signals.nonSuccessSpans
                        + " non-success trace span(s).",
                new ArrayList<>(signals.evidenceIds)
        );
    }

    private static final class Signals {
        private int errors;
        private int warnings;
        private int nonSuccessSpans;
        private final LinkedHashSet<String> evidenceIds = new LinkedHashSet<>();

        private boolean evidenceTruncated() {
            return evidenceIds.size() > DiagnosticProbeFinding.MAX_EVIDENCE_IDS;
        }
    }
}
