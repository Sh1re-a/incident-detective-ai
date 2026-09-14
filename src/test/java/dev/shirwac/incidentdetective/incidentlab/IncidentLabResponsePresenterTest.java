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
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
        assertNull(sanitized.comparison());
        assertNull(sanitized.diagnosis());
        assertNull(sanitized.events().getFirst().text());
        assertTrue(sanitized.events().getFirst().contentWithheld());
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
    void missingControlReceiptStopsTheIncidentLabResponse() {
        Diagnosis diagnosis = diagnosed(alarm.evidenceIds().getFirst());
        AdkAgentTurnResponse response = agentTurn(diagnosis, true);
        when(response.receipt()).thenReturn(null);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
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
                IllegalStateException.class,
                () -> presenter.present(generated.scenario(), logs, alarm, response)
        );
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
        when(response.receipt()).thenReturn(new AdkAgentTurnResponse.ControlReceipt(
                2,
                1,
                4,
                1,
                false,
                false,
                true,
                List.of("inspect_incident_evidence"),
                null,
                null,
                "test",
                10
        ));
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
                        "diagnosis_agent",
                        "text",
                        Instant.parse("2026-09-01T08:20:00Z"),
                        true,
                        false,
                        UNTRUSTED_PROSE,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test"
                )
        ));
        when(response.toolEvents()).thenReturn(List.of());
        when(response.limitations()).thenReturn(List.of());
        return response;
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
                + result.actionReceipt();
    }
}
