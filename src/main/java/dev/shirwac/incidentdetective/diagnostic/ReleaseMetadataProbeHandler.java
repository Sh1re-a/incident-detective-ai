package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Reads only the allowlisted release and service fields already in evidence. */
@Component
public final class ReleaseMetadataProbeHandler implements DiagnosticProbeHandler {

    @Override
    public DiagnosticProbeId probeId() {
        return DiagnosticProbeId.RELEASE_METADATA;
    }

    @Override
    public DiagnosticProbeReceipt inspect(InvestigationData data) {
        Map<ReleaseKey, LinkedHashSet<String>> releases = new LinkedHashMap<>();
        data.evidenceInventory().forEach(evidence -> {
            if (evidence instanceof LogEvidence log) {
                add(
                        releases,
                        log.content().service(),
                        log.content().attributes().get("release"),
                        log.evidenceId()
                );
            } else if (evidence instanceof MetricEvidence metric) {
                add(
                        releases,
                        metric.content().labels().get("service"),
                        metric.content().labels().get("release"),
                        metric.evidenceId()
                );
            }
        });

        List<DiagnosticProbeFinding> findings = releases.entrySet().stream()
                .map(entry -> DiagnosticProbeSupport.finding(
                        "release_metadata",
                        entry.getKey().service(),
                        "observed",
                        "Release: " + entry.getKey().release() + ".",
                        new ArrayList<>(entry.getValue())
                ))
                .toList();
        if (findings.isEmpty()) {
            return DiagnosticProbeSupport.unavailable(
                    data.scenario().scenarioId(),
                    probeId(),
                    "No request-local release metadata is available for this case.",
                    List.of(),
                    false
            );
        }
        boolean truncated = findings.size() > DiagnosticProbeReceipt.MAX_FINDINGS
                || releases.values().stream().anyMatch(ids ->
                ids.size() > DiagnosticProbeFinding.MAX_EVIDENCE_IDS
        );
        return DiagnosticProbeSupport.observed(
                data.scenario().scenarioId(),
                probeId(),
                "Read allowlisted release metadata from request-local evidence.",
                findings,
                truncated
        );
    }

    private void add(
            Map<ReleaseKey, LinkedHashSet<String>> releases,
            String service,
            String release,
            String evidenceId
    ) {
        if (service == null || service.isBlank()
                || release == null || release.isBlank()) {
            return;
        }
        releases.computeIfAbsent(
                new ReleaseKey(service, release),
                ignored -> new LinkedHashSet<>()
        ).add(evidenceId);
    }

    private record ReleaseKey(String service, String release) {
    }
}
