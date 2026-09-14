package dev.shirwac.incidentdetective.diagnostic;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticProbeServiceTest {

    private final DiagnosticProbeService service = new DiagnosticProbeService(
            List.of(
                    new ServiceHealthProbeHandler(),
                    new DependencyStatusProbeHandler(),
                    new ReleaseMetadataProbeHandler(),
                    new ConfigFingerprintDiffProbeHandler()
            )
    );

    private final PaymentTimeoutGeneratedCaseGenerator generator =
            new PaymentTimeoutGeneratedCaseGenerator();

    @Test
    void executesEveryAllowlistedProbeUsingRequestLocalEvidence() {
        GeneratedCase generated = generated(42L, GeneratedEvidenceMode.DIAGNOSTIC);
        List<String> caseEvidenceIds = generated.investigationData()
                .evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .toList();

        List<DiagnosticProbeReceipt> receipts = Arrays.stream(
                        DiagnosticProbeId.values()
                )
                .map(probeId -> service.execute(
                        generated,
                        new DiagnosticProbeRequest(
                                generated.scenario().scenarioId(),
                                probeId
                        )
                ))
                .toList();

        assertEquals(4, receipts.size());
        assertTrue(receipts.stream().allMatch(receipt ->
                receipt.outcome() == DiagnosticProbeOutcome.OBSERVED
        ));
        assertTrue(receipts.stream().allMatch(receipt ->
                receipt.scenarioId().equals(generated.scenario().scenarioId())
        ));
        assertTrue(receipts.stream().allMatch(receipt ->
                receipt.durationMs() >= 0
                        && receipt.readOnly()
                        && !receipt.actionExecuted()
        ));
        assertTrue(receipts.stream()
                .flatMap(receipt -> receipt.findings().stream())
                .flatMap(finding -> finding.evidenceIds().stream())
                .allMatch(caseEvidenceIds::contains));

        DiagnosticProbeReceipt config = receipts.stream()
                .filter(receipt -> receipt.probeId()
                        == DiagnosticProbeId.CONFIG_FINGERPRINT_DIFF)
                .findFirst()
                .orElseThrow();
        assertEquals("changed", config.findings().getFirst().status());
        assertTrue(config.findings().getFirst().detail().contains("sha256"));
        assertFalse(config.findings().getFirst().detail().contains("5000"));
    }

    @Test
    void reportsMissingConfigAuditWithoutInventingADiff() {
        GeneratedCase generated = generated(
                42L,
                GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE
        );

        DiagnosticProbeReceipt receipt = service.execute(
                generated,
                new DiagnosticProbeRequest(
                        generated.scenario().scenarioId(),
                        DiagnosticProbeId.CONFIG_FINGERPRINT_DIFF
                )
        );

        assertEquals(DiagnosticProbeOutcome.NOT_AVAILABLE, receipt.outcome());
        assertEquals(1, receipt.findings().size());
        assertEquals("audit_missing", receipt.findings().getFirst().status());
        assertFalse(receipt.safeSummary().toLowerCase().contains("changed"));
    }

    @Test
    void rejectsScenarioMismatchBeforeAHandlerRuns() {
        GeneratedCase generated = generated(42L, GeneratedEvidenceMode.DIAGNOSTIC);
        DiagnosticProbeRequest request = new DiagnosticProbeRequest(
                generated(99L, GeneratedEvidenceMode.DIAGNOSTIC)
                        .scenario().scenarioId(),
                DiagnosticProbeId.SERVICE_HEALTH
        );

        DiagnosticProbeRejectedException exception = assertThrows(
                DiagnosticProbeRejectedException.class,
                () -> service.execute(generated, request)
        );

        assertEquals(
                DiagnosticProbeRejectedException.Code.SCENARIO_MISMATCH,
                exception.code()
        );
    }

    @Test
    void rejectsEvidenceMixedAcrossRequestLocalCases() {
        GeneratedCase first = generated(42L, GeneratedEvidenceMode.DIAGNOSTIC);
        GeneratedCase second = generated(99L, GeneratedEvidenceMode.DIAGNOSTIC);
        List<Evidence> mixedEvidence = new ArrayList<>(
                first.investigationData().evidenceInventory()
        );
        mixedEvidence.set(
                0,
                second.investigationData().evidenceInventory().getFirst()
        );
        InvestigationData mixed = new InvestigationData(
                first.scenario(),
                mixedEvidence
        );

        DiagnosticProbeRejectedException exception = assertThrows(
                DiagnosticProbeRejectedException.class,
                () -> service.execute(
                        mixed,
                        new DiagnosticProbeRequest(
                                first.scenario().scenarioId(),
                                DiagnosticProbeId.SERVICE_HEALTH
                        )
                )
        );

        assertEquals(
                DiagnosticProbeRejectedException.Code.CROSS_CASE_EVIDENCE,
                exception.code()
        );
    }

    @Test
    void requestExposesNoCommandPathHostOrUrlInputAndRejectsUnknownProbe() {
        assertEquals(
                List.of("scenarioId", "probeId"),
                Arrays.stream(DiagnosticProbeRequest.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DiagnosticProbeId.fromWireValue("open_terminal")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiagnosticProbeRequest(
                        "../../another-case",
                        DiagnosticProbeId.SERVICE_HEALTH
                )
        );
    }

    @Test
    void receiptBoundsFindingsEvidenceIdsAndText() {
        List<DiagnosticProbeFinding> findings = IntStream.range(0, 10)
                .mapToObj(index -> new DiagnosticProbeFinding(
                        "service_health",
                        "service-" + index,
                        "observed",
                        "x".repeat(DiagnosticProbeFinding.MAX_DETAIL_LENGTH + 20),
                        List.of("one", "two", "three", "four", "five")
                ))
                .toList();

        DiagnosticProbeReceipt receipt = new DiagnosticProbeReceipt(
                "generated-bounded-case",
                DiagnosticProbeId.SERVICE_HEALTH,
                DiagnosticProbeOutcome.OBSERVED,
                "s".repeat(DiagnosticProbeReceipt.MAX_SUMMARY_LENGTH + 20),
                findings,
                false
        );

        assertTrue(receipt.truncated());
        assertEquals(DiagnosticProbeReceipt.MAX_FINDINGS, receipt.findings().size());
        assertEquals(
                DiagnosticProbeFinding.MAX_EVIDENCE_IDS,
                receipt.findings().getFirst().evidenceIds().size()
        );
        assertEquals(
                DiagnosticProbeFinding.MAX_DETAIL_LENGTH,
                receipt.findings().getFirst().detail().length()
        );
        assertEquals(
                DiagnosticProbeReceipt.MAX_SUMMARY_LENGTH,
                receipt.safeSummary().length()
        );
    }

    private GeneratedCase generated(long seed, GeneratedEvidenceMode evidenceMode) {
        return generator.generate(new GeneratedCaseRequest(
                seed,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                evidenceMode,
                GeneratedNoiseLevel.LOW
        ));
    }
}
