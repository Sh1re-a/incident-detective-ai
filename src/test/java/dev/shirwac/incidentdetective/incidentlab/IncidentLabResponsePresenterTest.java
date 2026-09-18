package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.alarm.GeneratedIncidentAlarmEvaluator;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeFinding;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeId;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeOutcome;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeReceipt;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IncidentLabResponsePresenterTest {

    private static final String UNTRUSTED_PROSE = "MODEL_PROSE_MUST_NOT_SURVIVE";

    private IncidentLabResponsePresenter presenter;
    private GeneratedCase generated;
    private SignalAlarmReceipt alarm;
    private List<LogEvidence> logs;

    @BeforeEach
    void createCase() {
        presenter = new IncidentLabResponsePresenter();
        generated = new PaymentTimeoutGeneratedCaseGenerator().generate(
                new GeneratedCaseRequest(
                        42L,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        GeneratedNoiseLevel.LOW
                )
        );
        alarm = new GeneratedIncidentAlarmEvaluator().evaluate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                generated.investigationData()
        ).orElseThrow();
        logs = generated.investigationData().evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .toList();
    }

    @Test
    void diagnosedUsesReleasedCodesAndNeverModelProse() {
        String evidenceId = alarm.evidenceIds().getFirst();
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                UNTRUSTED_PROSE,
                UNTRUSTED_PROSE,
                List.of(
                        claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", evidenceId),
                        claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", evidenceId),
                        claim(ClaimCode.CUSTOMER_IMPACT, "CHECKOUT_PAYMENT_FAILURES", evidenceId)
                ),
                new SafeNextStep(UNTRUSTED_PROSE, true)
        );
        AdkAgentTurnResponse raw = agentTurn(diagnosis, true);
        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(),
                logs,
                alarm,
                raw
        );
        AdkAgentTurnResponse sanitized =
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        raw,
                        result.answerState()
                );

        assertEquals(IncidentLabRunResponse.AnswerState.DIAGNOSED,
                result.answerState());
        assertEquals("PAYMENT_TIMEOUT_CONFIG",
                result.developerResponse().rootCauseCode());
        assertEquals("PAYMENT_ADAPTER",
                result.developerResponse().affectedService());
        assertFalse(allText(result).contains(UNTRUSTED_PROSE));
        assertFalse(result.actionReceipt().actionExecuted());
        assertTrue(result.actionReceipt().humanApprovalRequired());
        assertEquals(
                result.businessResponse(),
                result.localizedPresentations().sv().businessResponse()
        );
        assertEquals(
                result.developerResponse(),
                result.localizedPresentations().sv().developerResponse()
        );
        assertEquals(
                result.actionReceipt(),
                result.localizedPresentations().sv().actionReceipt()
        );
        IncidentLabRunResponse.LocalizedPresentation english =
                result.localizedPresentations().en();
        assertEquals(
                "I found the most likely explanation",
                english.businessResponse().headline()
        );
        assertEquals(
                result.developerResponse().rootCauseCode(),
                english.developerResponse().rootCauseCode()
        );
        assertEquals(
                result.developerResponse().verifiedClaims(),
                english.developerResponse().verifiedClaims()
        );
        assertEquals(
                result.developerResponse().highlightedLogEvidenceIds(),
                english.developerResponse().highlightedLogEvidenceIds()
        );
        assertEquals(result.actionReceipt().readOperations(),
                english.actionReceipt().readOperations());
        assertEquals(result.actionReceipt().status(),
                english.actionReceipt().status());
        assertFalse(english.actionReceipt().actionExecuted());
        assertTrue(english.actionReceipt().humanApprovalRequired());
        assertNull(sanitized.comparison());
        assertNull(sanitized.diagnosis());
        assertNull(sanitized.events().getLast().text());
        assertTrue(sanitized.events().getLast().contentWithheld());
    }

    @Test
    void publicProjectionDropsRawToolPayloadButRetainsTypedEvidence() {
        AdkAgentTurnResponse raw = agentTurn(
                diagnosed(alarm.evidenceIds().getFirst()),
                true
        );

        AdkAgentTurnResponse sanitized =
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        raw,
                        IncidentLabRunResponse.AnswerState.DIAGNOSED
                );

        Map<String, Object> publicResponse = sanitized.events().get(1)
                .functionResponses().getFirst().response();
        assertEquals(Set.of(
                "status",
                "safe_summary",
                "scenario_id",
                "evidence_ids",
                "source_refs",
                "write_capability",
                "action_executed"
        ), publicResponse.keySet());
        assertFalse(publicResponse.containsKey("operations"));
        assertFalse(publicResponse.containsKey("diagnostic_probe"));
        assertEquals(raw.toolEvents(), sanitized.toolEvents());
        assertEquals(raw.diagnosticProbe(), sanitized.diagnosticProbe());
        assertFalse(sanitized.toolEvents().getFirst().evidence().isEmpty());
    }

    @Test
    void insufficientEvidenceStatesUncertaintyWithoutInventingCause() {
        String evidenceId = alarm.evidenceIds().getFirst();
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                UNTRUSTED_PROSE,
                UNTRUSTED_PROSE,
                List.of(
                        claim(ClaimCode.OBSERVED_SYMPTOM, "PAYMENT_LATENCY_SPIKE", evidenceId),
                        claim(ClaimCode.MISSING_EVIDENCE, "PAYMENT_TIMEOUT_CONFIG_AUDIT", evidenceId)
                ),
                new SafeNextStep(UNTRUSTED_PROSE, true)
        );
        AdkAgentTurnResponse raw = agentTurn(diagnosis, true);

        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(), logs, alarm, raw
        );
        AdkAgentTurnResponse sanitized =
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        raw,
                        result.answerState()
                );

        assertEquals(IncidentLabRunResponse.AnswerState.INSUFFICIENT_EVIDENCE,
                result.answerState());
        assertNull(result.developerResponse().rootCauseCode());
        assertNull(result.developerResponse().affectedService());
        assertEquals(List.of("PAYMENT_TIMEOUT_CONFIG_AUDIT"),
                result.developerResponse().missingEvidenceCodes());
        assertTrue(result.businessResponse().headline()
                .contains("rotorsaken är inte fastställd"));
        assertTrue(result.localizedPresentations().en()
                .businessResponse().headline()
                .contains("root cause is not established"));
        assertEquals(
                result.developerResponse().missingEvidenceCodes(),
                result.localizedPresentations().en()
                        .developerResponse().missingEvidenceCodes()
        );
        assertFalse(allText(result).contains(UNTRUSTED_PROSE));
        assertNull(sanitized.diagnosis());
    }

    @Test
    void withheldDropsCandidateDiagnosisAndGroundTruthComparison() {
        String evidenceId = alarm.evidenceIds().getFirst();
        Diagnosis candidate = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                UNTRUSTED_PROSE,
                UNTRUSTED_PROSE,
                List.of(claim(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG",
                        evidenceId
                )),
                new SafeNextStep(UNTRUSTED_PROSE, true)
        );
        AdkAgentTurnResponse sanitized =
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        agentTurn(candidate, false),
                        IncidentLabRunResponse.AnswerState.WITHHELD
                );

        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(), logs, alarm, sanitized
        );

        assertEquals(IncidentLabRunResponse.AnswerState.WITHHELD,
                result.answerState());
        assertNull(sanitized.comparison());
        assertNull(sanitized.diagnosis());
        assertNull(result.developerResponse().rootCauseCode());
        assertNull(result.developerResponse().affectedService());
        assertTrue(result.developerResponse().verifiedClaims().isEmpty());
        assertTrue(result.developerResponse().failedVerificationChecks()
                .contains("schema"));
        assertFalse(allText(result).contains(UNTRUSTED_PROSE));
        assertEquals("not_proposed", result.actionReceipt().status());
        assertNull(result.actionReceipt().proposedNextStep());
        assertEquals(
                "Answer withheld after verification",
                result.localizedPresentations().en()
                        .businessResponse().headline()
        );
        assertEquals(
                result.developerResponse().failedVerificationChecks(),
                result.localizedPresentations().en()
                        .developerResponse().failedVerificationChecks()
        );
        assertNull(result.localizedPresentations().en()
                .actionReceipt().proposedNextStep());
    }

    @Test
    void withheldStillHighlightsLogsObservedByTheReadOnlyProbe() {
        String logEvidenceId = logs.getFirst().evidenceId();
        AdkAgentTurnResponse response = agentTurn(
                diagnosed(alarm.evidenceIds().getFirst()),
                false
        );
        when(response.diagnosticProbe()).thenReturn(new DiagnosticProbeReceipt(
                generated.scenario().scenarioId(),
                DiagnosticProbeId.SERVICE_HEALTH,
                DiagnosticProbeOutcome.OBSERVED,
                "One request-local error log was observed.",
                List.of(new DiagnosticProbeFinding(
                        "service_health",
                        "PAYMENT_ADAPTER",
                        "degraded",
                        "The read-only probe cited one backend log.",
                        List.of(logEvidenceId)
                )),
                false
        ));

        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(),
                logs,
                alarm,
                response
        );

        assertEquals(IncidentLabRunResponse.AnswerState.WITHHELD,
                result.answerState());
        assertFalse(alarm.evidenceIds().contains(logEvidenceId));
        assertTrue(result.developerResponse().highlightedLogEvidenceIds()
                .contains(logEvidenceId));
        assertEquals(
                result.developerResponse().highlightedLogEvidenceIds(),
                result.localizedPresentations().en()
                        .developerResponse().highlightedLogEvidenceIds()
        );
    }

    @Test
    void scenarioMismatchWithholdsAndSanitizesAnOtherwiseReleasedDiagnosis() {
        String evidenceId = alarm.evidenceIds().getFirst();
        Diagnosis candidate = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                UNTRUSTED_PROSE,
                UNTRUSTED_PROSE,
                List.of(claim(
                        ClaimCode.ROOT_CAUSE,
                        "PAYMENT_TIMEOUT_CONFIG",
                        evidenceId
                )),
                new SafeNextStep(UNTRUSTED_PROSE, true)
        );
        AdkAgentTurnResponse raw = agentTurn(candidate, true);
        GeneratedCase otherCase = new PaymentTimeoutGeneratedCaseGenerator()
                .generate(new GeneratedCaseRequest(
                        43L,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        GeneratedNoiseLevel.LOW
                ));
        when(raw.scenario()).thenReturn(otherCase.scenario());

        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(), logs, alarm, raw
        );
        AdkAgentTurnResponse sanitized =
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        raw,
                        result.answerState()
                );

        assertEquals(IncidentLabRunResponse.AnswerState.WITHHELD,
                result.answerState());
        assertNull(sanitized.diagnosis());
        assertNull(sanitized.comparison());
        assertNull(result.developerResponse().rootCauseCode());
    }

    @Test
    void noAlarmReturnsCompleteBackendOwnedCopyInBothLanguages() {
        IncidentLabResponsePresenter.Presentation result = presenter.present(
                generated.scenario(),
                logs,
                null,
                null
        );

        assertEquals(
                IncidentLabRunResponse.AnswerState.NOT_STARTED,
                result.answerState()
        );
        assertEquals(
                "Ingen utredning startades",
                result.localizedPresentations().sv()
                        .businessResponse().headline()
        );
        assertEquals(
                "No investigation was started",
                result.localizedPresentations().en()
                        .businessResponse().headline()
        );
        assertEquals(
                result.localizedPresentations().sv()
                        .developerResponse().verifiedClaims(),
                result.localizedPresentations().en()
                        .developerResponse().verifiedClaims()
        );
        assertEquals(0, result.localizedPresentations().en()
                .actionReceipt().readOperations());
        assertFalse(result.localizedPresentations().en()
                .actionReceipt().actionExecuted());
    }

    @Test
    void missingControlReceiptStopsTheIncidentLabResponse() {
        Diagnosis diagnosis = diagnosed(alarm.evidenceIds().getFirst());
        AdkAgentTurnResponse response = agentTurn(diagnosis, true);
        when(response.receipt()).thenReturn(null);

        InvalidAdkControlReceiptException failure = assertThrows(
                InvalidAdkControlReceiptException.class,
                () -> presenter.present(generated.scenario(), logs, alarm, response)
        );

        assertTrue(failure.getMessage().contains("control receipt"));
    }

    @Test
    void unsafeOrUnexpectedControlReceiptStopsTheIncidentLabResponse() {
        Diagnosis diagnosis = diagnosed(alarm.evidenceIds().getFirst());
        AdkAgentTurnResponse response = agentTurn(diagnosis, true);
        when(response.receipt()).thenReturn(new AdkAgentTurnResponse.ControlReceipt(
                2,
                1,
                4,
                1,
                true,
                true,
                false,
                List.of("open_terminal"),
                null,
                null,
                "test",
                10
        ));

        assertThrows(
                InvalidAdkControlReceiptException.class,
                () -> presenter.present(generated.scenario(), logs, alarm, response)
        );
    }

    @Test
    void unreconciledControlReceiptCountersStopTheIncidentLabResponse() {
        Diagnosis diagnosis = diagnosed(alarm.evidenceIds().getFirst());
        AdkAgentTurnResponse response = agentTurn(diagnosis, true);
        List<AdkAgentTurnResponse.ControlReceipt> invalid = List.of(
                controlReceipt(1, 1, 2, 0),
                controlReceipt(2, 0, 2, 0),
                controlReceipt(2, 1, 3, 0),
                controlReceipt(2, 1, 2, 1)
        );

        for (AdkAgentTurnResponse.ControlReceipt receipt : invalid) {
            when(response.receipt()).thenReturn(receipt);
            assertThrows(
                    InvalidAdkControlReceiptException.class,
                    () -> presenter.present(
                            generated.scenario(),
                            logs,
                            alarm,
                            response
                    )
            );
        }
    }

    private Diagnosis diagnosed(String evidenceId) {
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                UNTRUSTED_PROSE,
                UNTRUSTED_PROSE,
                List.of(
                        claim(ClaimCode.ROOT_CAUSE, "PAYMENT_TIMEOUT_CONFIG", evidenceId),
                        claim(ClaimCode.AFFECTED_SERVICE, "PAYMENT_ADAPTER", evidenceId)
                ),
                new SafeNextStep(UNTRUSTED_PROSE, true)
        );
    }

    private Claim claim(ClaimCode code, String value, String evidenceId) {
        return new Claim(code, value, UNTRUSTED_PROSE, List.of(evidenceId));
    }

    private AdkAgentTurnResponse agentTurn(
            Diagnosis diagnosis,
            boolean answerReleased
    ) {
        AdkAgentTurnResponse response = mock(AdkAgentTurnResponse.class);
        when(response.outcome()).thenReturn(
                answerReleased ? "completed" : "verification_failed"
        );
        when(response.scenario()).thenReturn(generated.scenario());
        when(response.diagnosis()).thenReturn(diagnosis);
        when(response.verificationEvent()).thenReturn(verification(answerReleased));
        when(response.receipt()).thenReturn(controlReceipt(2, 1, 2, 0));
        when(response.comparison()).thenReturn(new ReplayComparison(
                diagnosis.status(),
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                answerReleased,
                answerReleased,
                diagnosis.status() == DiagnosisStatus.INSUFFICIENT_EVIDENCE
        ));
        when(response.events()).thenReturn(List.of(
                new AdkAgentTurnResponse.RuntimeEvent(
                        1,
                        "event-1",
                        "invocation-1",
                        "nordly_evidence_agent",
                        "tool_call",
                        Instant.parse("2026-09-01T08:20:00Z"),
                        false,
                        false,
                        null,
                        List.of(new AdkAgentTurnResponse.FunctionCallEvent(
                                "call-1",
                                "inspect_incident_evidence",
                                Map.of("log_query", "timeout")
                        )),
                        List.of(),
                        null,
                        "gemini-test"
                ),
                new AdkAgentTurnResponse.RuntimeEvent(
                        2,
                        "event-2",
                        "invocation-1",
                        "nordly_evidence_agent",
                        "tool_result",
                        Instant.parse("2026-09-01T08:20:01Z"),
                        false,
                        false,
                        null,
                        List.of(),
                        List.of(new AdkAgentTurnResponse.FunctionResponseEvent(
                                "call-1",
                                "inspect_incident_evidence",
                                Map.of(
                                        "status", "found",
                                        "safe_summary",
                                        "Returned bounded synthetic evidence.",
                                        "scenario_id",
                                        generated.scenario().scenarioId(),
                                        "evidence_ids",
                                        List.of(logs.getFirst().evidenceId()),
                                        "source_refs",
                                        List.of(logs.getFirst().sourceRef()),
                                        "operations",
                                        List.of(Map.of("raw", "must not survive")),
                                        "diagnostic_probe",
                                        Map.of("raw", "must not survive"),
                                        "write_capability", false,
                                        "action_executed", false
                                )
                        )),
                        null,
                        null
                ),
                new AdkAgentTurnResponse.RuntimeEvent(
                        3,
                        "event-3",
                        "invocation-1",
                        "nordly_diagnosis_agent",
                        "final_response",
                        Instant.parse("2026-09-01T08:20:02Z"),
                        true,
                        false,
                        UNTRUSTED_PROSE,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test"
                )
        ));
        when(response.toolEvents()).thenReturn(List.of(new LiveToolEvent(
                "read-log-1",
                1,
                ToolName.SEARCH_LOGS,
                Map.of("query", "timeout"),
                "Returned one request-local synthetic log.",
                List.of(logs.getFirst()),
                null
        )));
        when(response.diagnosticProbe()).thenReturn(new DiagnosticProbeReceipt(
                generated.scenario().scenarioId(),
                DiagnosticProbeId.SERVICE_HEALTH,
                DiagnosticProbeOutcome.OBSERVED,
                "One request-local error log was observed.",
                List.of(new DiagnosticProbeFinding(
                        "service_health",
                        "PAYMENT_ADAPTER",
                        "degraded",
                        "The read-only probe cited one backend log.",
                        List.of(logs.getFirst().evidenceId())
                )),
                false
        ));
        when(response.limitations()).thenReturn(List.of());
        return response;
    }

    private AdkAgentTurnResponse.ControlReceipt controlReceipt(
            int modelCalls,
            int adkToolCalls,
            int readOperations,
            int embeddingCalls
    ) {
        return new AdkAgentTurnResponse.ControlReceipt(
                modelCalls,
                adkToolCalls,
                readOperations,
                embeddingCalls,
                false,
                false,
                true,
                List.of("inspect_incident_evidence"),
                null,
                null,
                "test",
                10
        );
    }

    private AdkAgentTurnResponse.VerificationEvent verification(
            boolean answerReleased
    ) {
        return new AdkAgentTurnResponse.VerificationEvent(
                "deterministic_java_verifier",
                Instant.parse("2026-09-01T08:20:00Z"),
                answerReleased,
                answerReleased,
                answerReleased,
                answerReleased,
                true,
                true,
                true,
                true,
                answerReleased,
                "Java-owned test receipt"
        );
    }

    private String allText(IncidentLabResponsePresenter.Presentation result) {
        return result.businessResponse().toString()
                + result.developerResponse()
                + result.actionReceipt()
                + result.localizedPresentations();
    }
}
