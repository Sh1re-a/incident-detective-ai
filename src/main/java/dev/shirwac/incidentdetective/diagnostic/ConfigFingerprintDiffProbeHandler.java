package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Compares fingerprints only; raw configuration values are never returned.
 */
@Component
public final class ConfigFingerprintDiffProbeHandler
        implements DiagnosticProbeHandler {

    @Override
    public DiagnosticProbeId probeId() {
        return DiagnosticProbeId.CONFIG_FINGERPRINT_DIFF;
    }

    @Override
    public DiagnosticProbeReceipt inspect(InvestigationData data) {
        List<DiagnosticProbeFinding> diffs = data.evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .filter(this::hasComparableValues)
                .map(this::diffFinding)
                .toList();
        if (!diffs.isEmpty()) {
            return DiagnosticProbeSupport.observed(
                    data.scenario().scenarioId(),
                    probeId(),
                    "Compared hashed configuration values from request-local audit evidence.",
                    diffs,
                    diffs.size() > DiagnosticProbeReceipt.MAX_FINDINGS
            );
        }

        List<DiagnosticProbeFinding> missingAudit = data.evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .filter(log -> present(
                        log.content().attributes().get("missing_evidence")
                ))
                .map(log -> DiagnosticProbeSupport.finding(
                        "config_fingerprint_diff",
                        log.content().service(),
                        "audit_missing",
                        "The case reports missing configuration audit evidence.",
                        List.of(log.evidenceId())
                ))
                .toList();
        return DiagnosticProbeSupport.unavailable(
                data.scenario().scenarioId(),
                probeId(),
                missingAudit.isEmpty()
                        ? "No request-local configuration audit is available for comparison."
                        : "The case explicitly reports that configuration audit evidence is missing.",
                missingAudit,
                missingAudit.size() > DiagnosticProbeReceipt.MAX_FINDINGS
        );
    }

    private boolean hasComparableValues(LogEvidence log) {
        Map<String, String> attributes = log.content().attributes();
        return present(attributes.get("previous_value"))
                && present(attributes.get("new_value"));
    }

    private DiagnosticProbeFinding diffFinding(LogEvidence log) {
        String previous = log.content().attributes().get("previous_value");
        String current = log.content().attributes().get("new_value");
        return DiagnosticProbeSupport.finding(
                "config_fingerprint_diff",
                log.content().service(),
                previous.equals(current) ? "unchanged" : "changed",
                "Previous sha256: "
                        + DiagnosticProbeSupport.shortSha256(previous)
                        + "; current sha256: "
                        + DiagnosticProbeSupport.shortSha256(current) + ".",
                List.of(log.evidenceId())
        );
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
