package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Dispatches only allowlisted probes after enforcing request-local isolation. */
@Service
public final class DiagnosticProbeService {

    private final Map<DiagnosticProbeId, DiagnosticProbeHandler> handlers;

    public DiagnosticProbeService(List<DiagnosticProbeHandler> handlers) {
        if (handlers == null) {
            throw new IllegalArgumentException("handlers must not be null");
        }
        EnumMap<DiagnosticProbeId, DiagnosticProbeHandler> byId =
                new EnumMap<>(DiagnosticProbeId.class);
        handlers.forEach(handler -> {
            if (handler == null) {
                throw new IllegalArgumentException("handlers must not contain null");
            }
            DiagnosticProbeHandler previous = byId.put(
                    handler.probeId(),
                    handler
            );
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate diagnostic handler for "
                                + handler.probeId().wireValue()
                );
            }
        });
        if (byId.size() != DiagnosticProbeId.values().length) {
            throw new IllegalArgumentException(
                    "every diagnostic probe must have exactly one handler"
            );
        }
        this.handlers = Map.copyOf(byId);
    }

    /**
     * Uses only {@link GeneratedCase#investigationData()}; hidden ground truth
     * is never passed to a handler.
     */
    public DiagnosticProbeReceipt execute(
            GeneratedCase generatedCase,
            DiagnosticProbeRequest request
    ) {
        if (generatedCase == null) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.INVALID_CASE,
                    "A generated case is required."
            );
        }
        return execute(generatedCase.investigationData(), request);
    }

    public DiagnosticProbeReceipt execute(
            InvestigationData data,
            DiagnosticProbeRequest request
    ) {
        if (data == null || data.scenario() == null
                || data.evidenceInventory() == null) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.INVALID_CASE,
                    "Complete request-local investigation data is required."
            );
        }
        if (request == null) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.INVALID_CASE,
                    "A diagnostic probe request is required."
            );
        }
        String scenarioId = data.scenario().scenarioId();
        if (!request.scenarioId().equals(scenarioId)) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.SCENARIO_MISMATCH,
                    "The probe request does not belong to this generated case."
            );
        }
        boolean containsCrossCaseEvidence = data.evidenceInventory().stream()
                .anyMatch(evidence -> evidence == null
                        || !scenarioId.equals(evidence.scenarioId()));
        if (containsCrossCaseEvidence) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.CROSS_CASE_EVIDENCE,
                    "The investigation data contains evidence from another case."
            );
        }
        long startedAt = System.nanoTime();
        DiagnosticProbeReceipt receipt = handlers.get(request.probeId())
                .inspect(data);
        validateHandlerReceipt(data, request, receipt);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
                Math.max(0, System.nanoTime() - startedAt)
        );
        return receipt.withDurationMs(durationMs);
    }

    private void validateHandlerReceipt(
            InvestigationData data,
            DiagnosticProbeRequest request,
            DiagnosticProbeReceipt receipt
    ) {
        if (receipt == null) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.INVALID_CASE,
                    "The diagnostic handler returned no receipt."
            );
        }
        if (!request.scenarioId().equals(receipt.scenarioId())) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.SCENARIO_MISMATCH,
                    "The diagnostic receipt does not belong to this generated case."
            );
        }
        if (request.probeId() != receipt.probeId()) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.PROBE_MISMATCH,
                    "The diagnostic receipt does not match the requested probe."
            );
        }
        Set<String> caseEvidenceIds = data.evidenceInventory().stream()
                .map(evidence -> evidence.evidenceId())
                .collect(Collectors.toUnmodifiableSet());
        boolean containsCrossCaseEvidence = receipt.findings().stream()
                .flatMap(finding -> finding.evidenceIds().stream())
                .anyMatch(evidenceId -> !caseEvidenceIds.contains(evidenceId));
        if (containsCrossCaseEvidence) {
            throw rejection(
                    DiagnosticProbeRejectedException.Code.CROSS_CASE_EVIDENCE,
                    "The diagnostic receipt cites evidence outside this generated case."
            );
        }
    }

    private DiagnosticProbeRejectedException rejection(
            DiagnosticProbeRejectedException.Code code,
            String safeMessage
    ) {
        return new DiagnosticProbeRejectedException(code, safeMessage);
    }
}
