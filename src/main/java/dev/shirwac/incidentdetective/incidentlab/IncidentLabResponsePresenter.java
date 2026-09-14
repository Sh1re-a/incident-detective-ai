package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.adk.AdkAgentRuntime;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimValueTaxonomy;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse.AnswerState;

/** Builds Swedish UI copy only from Java-owned or released structured facts. */
final class IncidentLabResponsePresenter {

    Presentation present(
            Scenario scenario,
            List<LogEvidence> backendLogs,
            SignalAlarmReceipt alarm,
            AdkAgentTurnResponse agentTurn
    ) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        backendLogs = backendLogs == null ? List.of() : List.copyOf(backendLogs);

        if (alarm == null) {
            return notStarted(scenario);
        }
        requireSafeControlReceipt(agentTurn);
        if (!released(agentTurn, scenario, alarm)) {
            return withheld(scenario, backendLogs, alarm, agentTurn);
        }

        Diagnosis diagnosis = agentTurn.diagnosis();
        return diagnosis.status() == DiagnosisStatus.DIAGNOSED
                ? diagnosed(scenario, backendLogs, alarm, agentTurn, diagnosis)
                : insufficient(scenario, backendLogs, alarm, agentTurn, diagnosis);
    }

    static AdkAgentTurnResponse sanitizeAgentTurn(
            AdkAgentTurnResponse agentTurn,
            AnswerState answerState
    ) {
        Objects.requireNonNull(answerState, "answerState must not be null");
        if (agentTurn == null) {
            return null;
        }
        return new AdkAgentTurnResponse(
                agentTurn.contractVersion(),
                agentTurn.runId(),
                agentTurn.sessionId(),
                agentTurn.turnId(),
                agentTurn.mode(),
                agentTurn.truthLabel(),
                agentTurn.outcome(),
                agentTurn.providerRoute(),
                agentTurn.scenario(),
                agentTurn.safety(),
                agentTurn.runtime(),
                agentTurn.workflow(),
                sanitizeEvents(agentTurn.events()),
                agentTurn.toolEvents(),
                agentTurn.diagnosticProbe(),
                null,
                agentTurn.verification(),
                null,
                agentTurn.verificationEvent(),
                agentTurn.receipt(),
                agentTurn.limitations()
        );
    }

    private static List<AdkAgentTurnResponse.RuntimeEvent> sanitizeEvents(
            List<AdkAgentTurnResponse.RuntimeEvent> events
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return events.stream()
                .filter(Objects::nonNull)
                .map(event -> new AdkAgentTurnResponse.RuntimeEvent(
                        event.sequence(),
                        event.eventId(),
                        event.invocationId(),
                        event.author(),
                        event.type(),
                        event.observedAt(),
                        event.finalResponse(),
                        event.contentWithheld() || event.text() != null,
                        null,
                        event.functionCalls(),
                        event.functionResponses(),
                        event.tokenUsage(),
                        event.providerModelVersion()
                ))
                .toList();
    }

    private Presentation diagnosed(
            Scenario scenario,
            List<LogEvidence> backendLogs,
            SignalAlarmReceipt alarm,
            AdkAgentTurnResponse agentTurn,
            Diagnosis diagnosis
    ) {
        List<IncidentLabRunResponse.VerifiedClaim> claims = claims(diagnosis);
        String rootCause = rootCause(diagnosis.rootCauseCode());
        String service = service(diagnosis.affectedService());
        String nextRead = diagnosedNextRead(diagnosis.rootCauseCode());
        String alarmText = alarmText(alarm);
        return new Presentation(
                AnswerState.DIAGNOSED,
                new IncidentLabRunResponse.BusinessResponse(
                        "Rotorsaken är verifierad i det syntetiska fallet",
                        alarmText,
                        impact(scenario, alarm.incidentFamily()),
                        List.of(
                                alarmText,
                                "Verifierad orsak: " + rootCause + " i "
                                        + service + "."
                        ),
                        List.of(
                                "Om samma mönster finns utanför det syntetiska fallet är inte undersökt."
                        ),
                        nextRead,
                        "Verifierad i det syntetiska fallet",
                        true
                ),
                new IncidentLabRunResponse.DeveloperResponse(
                        "Java frisläppte diagnosen efter verifiering. Rotorsakskod "
                                + diagnosis.rootCauseCode() + " gäller tjänsten "
                                + diagnosis.affectedService() + ". "
                                + receiptSummary(agentTurn),
                        diagnosis.rootCauseCode(),
                        diagnosis.affectedService(),
                        claims,
                        highlightedLogs(backendLogs, alarm, claims),
                        missingEvidenceCodes(diagnosis),
                        List.of(),
                        nextRead
                ),
                actionReceipt(agentTurn, "proposed_only", nextRead)
        );
    }

    private Presentation insufficient(
            Scenario scenario,
            List<LogEvidence> backendLogs,
            SignalAlarmReceipt alarm,
            AdkAgentTurnResponse agentTurn,
            Diagnosis diagnosis
    ) {
        List<IncidentLabRunResponse.VerifiedClaim> claims = claims(diagnosis);
        List<String> missingCodes = missingEvidenceCodes(diagnosis);
        List<String> known = new ArrayList<>();
        known.add(alarmText(alarm));
        diagnosis.claims().stream()
                .filter(claim -> claim.claimCode() == ClaimCode.OBSERVED_SYMPTOM)
                .map(claim -> "Observerat: " + symptom(claim.claimValueCode()) + ".")
                .distinct()
                .forEach(known::add);
        List<String> unknown = new ArrayList<>();
        unknown.add("Rotorsaken är inte fastställd.");
        missingCodes.stream()
                .map(code -> "Saknat underlag: " + missingEvidence(code) + ".")
                .forEach(unknown::add);
        String nextRead = insufficientNextRead(missingCodes);
        return new Presentation(
                AnswerState.INSUFFICIENT_EVIDENCE,
                new IncidentLabRunResponse.BusinessResponse(
                        "Larm utlöst – rotorsaken är inte fastställd",
                        alarmText(alarm),
                        impact(scenario, alarm.incidentFamily()),
                        known,
                        unknown,
                        nextRead,
                        "Begränsad – rotorsaken är inte fastställd",
                        true
                ),
                new IncidentLabRunResponse.DeveloperResponse(
                        "Java frisläppte endast ett svar med otillräckligt underlag. "
                                + "Ingen rotorsak eller berörd tjänst returneras som faktum. "
                                + receiptSummary(agentTurn),
                        null,
                        null,
                        claims,
                        highlightedLogs(backendLogs, alarm, claims),
                        missingCodes,
                        List.of(),
                        nextRead
                ),
                actionReceipt(agentTurn, "proposed_only", nextRead)
        );
    }

    private Presentation withheld(
            Scenario scenario,
            List<LogEvidence> backendLogs,
            SignalAlarmReceipt alarm,
            AdkAgentTurnResponse agentTurn
    ) {
        List<String> failures = failedChecks(agentTurn, scenario, alarm);
        String nextRead = "Granska verifieringskvittot och de befintliga "
                + "läskvittona; kör ingen ändring från detta svar.";
        return new Presentation(
                AnswerState.WITHHELD,
                new IncidentLabRunResponse.BusinessResponse(
                        "Svar undanhållet efter kontroll",
                        alarmText(alarm),
                        impact(scenario, alarm.incidentFamily()),
                        List.of(
                                alarmText(alarm),
                                "Java har inte frisläppt någon diagnos."
                        ),
                        List.of(
                                "Rotorsak och berörd tjänst är inte fastställda."
                        ),
                        nextRead,
                        "Ingen frisläppt slutsats",
                        true
                ),
                new IncidentLabRunResponse.DeveloperResponse(
                        "Agentsvaret frisläpptes inte. Underkända eller saknade kontroller: "
                                + String.join(", ", failures) + ". "
                                + receiptSummary(agentTurn),
                        null,
                        null,
                        List.of(),
                        highlightedLogs(backendLogs, alarm, List.of()),
                        List.of(),
                        failures,
                        nextRead
                ),
                actionReceipt(agentTurn, "not_proposed", null)
        );
    }

    private Presentation notStarted(Scenario scenario) {
        String nextRead = "Fortsätt samla syntetisk telemetri och starta "
                + "agenten först när en deterministisk larmregel löser ut.";
        return new Presentation(
                AnswerState.NOT_STARTED,
                new IncidentLabRunResponse.BusinessResponse(
                        "Ingen utredning startades",
                        "Ingen deterministisk larmregel löste ut, därför anropades ingen agent.",
                        impact(scenario, null),
                        List.of("Det syntetiska scenariot skapades utan ett utlöst larm."),
                        List.of("Rotorsaken har inte utretts."),
                        nextRead,
                        "Inte bedömd",
                        true
                ),
                new IncidentLabRunResponse.DeveloperResponse(
                        "Ingen alarm_receipt finns och ADK kördes inte.",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        nextRead
                ),
                actionReceipt(null, "not_proposed", null)
        );
    }

    private boolean released(
            AdkAgentTurnResponse agentTurn,
            Scenario scenario,
            SignalAlarmReceipt alarm
    ) {
        if (agentTurn == null
                || !"completed".equals(agentTurn.outcome())
                || agentTurn.verificationEvent() == null
                || !agentTurn.verificationEvent().answerReleased()
                || agentTurn.diagnosis() == null
                || !safeControlReceipt(agentTurn.receipt())
                || agentTurn.scenario() == null
                || !scenario.scenarioId().equals(agentTurn.scenario().scenarioId())
                || !scenario.scenarioId().equals(alarm.scenarioId())
                || !validClaims(agentTurn.diagnosis())) {
            return false;
        }
        Diagnosis diagnosis = agentTurn.diagnosis();
        if (diagnosis.status() == DiagnosisStatus.DIAGNOSED) {
            return validCode(ClaimCode.ROOT_CAUSE, diagnosis.rootCauseCode())
                    && validCode(ClaimCode.AFFECTED_SERVICE, diagnosis.affectedService());
        }
        return diagnosis.status() == DiagnosisStatus.INSUFFICIENT_EVIDENCE
                && diagnosis.rootCauseCode() == null
                && diagnosis.affectedService() == null;
    }

    private boolean validClaims(Diagnosis diagnosis) {
        return diagnosis.claims() != null && diagnosis.claims().stream().allMatch(
                claim -> claim != null
                        && claim.claimCode() != null
                        && validCode(claim.claimCode(), claim.claimValueCode())
                        && claim.evidenceIds() != null
                        && !claim.evidenceIds().isEmpty()
                        && claim.evidenceIds().stream()
                        .allMatch(id -> id != null && !id.isBlank())
        );
    }

    private boolean validCode(ClaimCode code, String value) {
        return ClaimValueTaxonomy.contains(code, value);
    }

    private List<IncidentLabRunResponse.VerifiedClaim> claims(
            Diagnosis diagnosis
    ) {
        return diagnosis.claims().stream()
                .sorted(Comparator
                        .comparing((Claim claim) -> claim.claimCode().ordinal())
                        .thenComparing(Claim::claimValueCode))
                .map(claim -> new IncidentLabRunResponse.VerifiedClaim(
                        claim.claimCode().wireValue(),
                        claim.claimValueCode(),
                        claim.evidenceIds().stream().sorted().toList()
                ))
                .toList();
    }

    private List<String> highlightedLogs(
            List<LogEvidence> backendLogs,
            SignalAlarmReceipt alarm,
            List<IncidentLabRunResponse.VerifiedClaim> claims
    ) {
        Set<String> cited = new LinkedHashSet<>(alarm.evidenceIds());
        claims.stream().flatMap(claim -> claim.evidenceIds().stream())
                .forEach(cited::add);
        return backendLogs.stream()
                .map(LogEvidence::evidenceId)
                .filter(cited::contains)
                .distinct()
                .toList();
    }

    private List<String> missingEvidenceCodes(Diagnosis diagnosis) {
        return diagnosis.claims().stream()
                .filter(claim -> claim.claimCode() == ClaimCode.MISSING_EVIDENCE)
                .map(Claim::claimValueCode)
                .distinct()
                .sorted()
                .toList();
    }

    private List<String> failedChecks(
            AdkAgentTurnResponse agentTurn,
            Scenario scenario,
            SignalAlarmReceipt alarm
    ) {
        List<String> failed = new ArrayList<>();
        if (agentTurn == null) {
            return List.of("agent_turn_missing", "answer_not_released");
        }
        if (!"completed".equals(agentTurn.outcome())) {
            failed.add("agent_outcome_not_completed");
        }
        AdkAgentTurnResponse.VerificationEvent event = agentTurn.verificationEvent();
        if (event == null) {
            failed.add("verification_event_missing");
        } else {
            addFailed(failed, event.schemaValid(), "schema");
            addFailed(failed, event.citationsValid(), "citations");
            addFailed(failed, event.directEvidenceSupportValid(), "direct_evidence_support");
            addFailed(failed, event.factualResultMatchesGroundTruth(), "factual_match");
            addFailed(failed, event.agentSequenceValid(), "agent_sequence");
            addFailed(failed, event.evidenceHandoffValid(), "evidence_handoff");
            addFailed(failed, event.toolBoundaryValid(), "tool_boundary");
            addFailed(failed, event.finalAuthorValid(), "final_author");
            addFailed(failed, event.answerReleased(), "answer_not_released");
        }
        if (agentTurn.scenario() == null
                || !scenario.scenarioId().equals(agentTurn.scenario().scenarioId())
                || !scenario.scenarioId().equals(alarm.scenarioId())) {
            failed.add("scenario_identity");
        }
        if (agentTurn.receipt() == null) {
            failed.add("control_receipt_missing");
        }
        return failed.isEmpty() ? List.of("released_answer_incomplete") : List.copyOf(failed);
    }

    private void addFailed(List<String> failed, boolean passed, String code) {
        if (!passed) {
            failed.add(code);
        }
    }

    private IncidentLabRunResponse.ActionReceipt actionReceipt(
            AdkAgentTurnResponse agentTurn,
            String status,
            String proposedNextStep
    ) {
        int reads = agentTurn == null || agentTurn.receipt() == null
                ? 0
                : agentTurn.receipt().readOperations();
        String summary = reads == 0
                ? "Ingen åtgärd kördes. Inga skrivverktyg var tillgängliga "
                        + "och varje ändring kräver mänskligt godkännande."
                : "Endast " + reads + " läsoperationer utfördes. Ingen "
                        + "åtgärd kördes och varje ändring kräver mänskligt "
                        + "godkännande.";
        return new IncidentLabRunResponse.ActionReceipt(
                status,
                reads,
                false,
                false,
                true,
                proposedNextStep,
                summary
        );
    }

    private void requireSafeControlReceipt(AdkAgentTurnResponse agentTurn) {
        if (agentTurn == null) {
            return;
        }
        if (!safeControlReceipt(agentTurn.receipt())) {
            throw new IllegalStateException(
                    "Incident Lab received a missing or unsafe ADK control receipt"
            );
        }
    }

    private boolean safeControlReceipt(
            AdkAgentTurnResponse.ControlReceipt receipt
    ) {
        return receipt != null
                && !receipt.writeToolsAvailable()
                && !receipt.actionExecuted()
                && receipt.humanApprovalRequired()
                && receipt.modelCalls() >= 0
                && receipt.adkToolCalls() >= 0
                && receipt.readOperations() >= 0
                && receipt.embeddingCalls() >= 0
                && receipt.totalLatencyMs() >= 0
                && List.of(AdkAgentRuntime.TOOL_NAME)
                .equals(receipt.registeredTools());
    }

    private String alarmText(SignalAlarmReceipt alarm) {
        String window = alarm.signal().lookbackSeconds() == null
                ? ""
                : " under " + alarm.signal().lookbackSeconds() + " sekunder";
        return "Larmet löste ut när " + signal(alarm.incidentFamily())
                + " uppmättes till " + number(alarm.signal().observedValue())
                + " " + unit(alarm.signal().unit()) + window
                + "; gränsen är minst "
                + number(alarm.signal().thresholdValue()) + " "
                + unit(alarm.signal().unit()) + ".";
    }

    private String receiptSummary(AdkAgentTurnResponse agentTurn) {
        int tools = agentTurn.toolEvents() == null ? 0 : agentTurn.toolEvents().size();
        String probe = agentTurn.diagnosticProbe() == null
                ? "ingen diagnostisk probe"
                : "probe " + agentTurn.diagnosticProbe().probeId().name()
                        + " med utfallet "
                        + agentTurn.diagnosticProbe().outcome().name();
        return "Kvittot visar " + tools + " verktygshändelser och " + probe + ".";
    }

    private String impact(Scenario scenario, GeneratedIncidentFamily family) {
        if (family == null) {
            return "Det syntetiska scenariots påverkan finns kvar i scenario-kvittot; ingen orsak är bedömd.";
        }
        return switch (family) {
            case PAYMENT_TIMEOUT -> "I det syntetiska fallet misslyckades betalningar i kassan.";
            case CATALOG_CACHE_INVALIDATION -> "I det syntetiska fallet visades inaktuella priser eller lagersaldon.";
            case ORDER_EVENT_BACKLOG -> "I det syntetiska fallet fördröjdes "
                    + "orderbekräftelser och efterföljande orderhantering.";
            case ORDER_IDEMPOTENCY_FAILURE -> "I det syntetiska fallet skapades dubbla ordrar efter ett nytt försök.";
        };
    }

    private String signal(GeneratedIncidentFamily family) {
        return switch (family) {
            case PAYMENT_TIMEOUT -> "antalet HTTP 5xx-svar";
            case CATALOG_CACHE_INVALIDATION -> "antalet avvikande katalogversioner";
            case ORDER_EVENT_BACKLOG -> "orderkonsumentens fördröjning";
            case ORDER_IDEMPOTENCY_FAILURE -> "antalet dubbelskapade ordrar";
        };
    }

    private String rootCause(String code) {
        return switch (code) {
            case "PAYMENT_TIMEOUT_CONFIG" -> "betalningsadapterns timeoutkonfiguration";
            case "INVENTORY_SCHEMA_MISMATCH" -> "en schemakrock i lagertjänsten";
            case "CHECKOUT_DB_POOL_EXHAUSTION" -> "en uttömd databasanslutningspool i kassan";
            case "CATALOG_CACHE_INVALIDATION_FAILURE" -> "utebliven invalidering av katalogcachen";
            case "ORDER_EVENT_CONSUMER_BACKLOG" -> "köbildning i orderkonsumenten";
            case "ORDER_IDEMPOTENCY_FAILURE" -> "fel i ordertjänstens idempotenshantering";
            default -> "rotorsakskod " + code;
        };
    }

    private String service(String code) {
        return switch (code) {
            case "PAYMENT_ADAPTER" -> "betalningsadaptern";
            case "INVENTORY_SERVICE" -> "lagertjänsten";
            case "CHECKOUT_API" -> "kassans API";
            case "CATALOG_SERVICE" -> "katalogtjänsten";
            case "ORDER_EVENT_CONSUMER" -> "orderkonsumenten";
            case "ORDER_SERVICE" -> "ordertjänsten";
            default -> "tjänsten " + code;
        };
    }

    private String symptom(String code) {
        return switch (code) {
            case "PAYMENT_LATENCY_SPIKE" -> "kraftigt ökad svarstid för betalningar";
            case "INVENTORY_CONTRACT_VALIDATION_ERRORS" -> "valideringsfel i lagerkontraktet";
            case "DATABASE_POOL_WAIT_SPIKE" -> "kraftigt ökad väntetid i databasanslutningspoolen";
            case "CATALOG_VERSION_DIVERGENCE" -> "avvikande katalogversioner";
            case "ORDER_CONSUMER_LAG" -> "fördröjning i orderkonsumenten";
            case "DUPLICATE_ORDER_CREATION" -> "dubbelskapade ordrar";
            default -> "symtomkod " + code;
        };
    }

    private String missingEvidence(String code) {
        return switch (code) {
            case "PAYMENT_PROVIDER_RESPONSE" -> "betalningsleverantörens svar";
            case "PAYMENT_TIMEOUT_CONFIG_AUDIT" -> "revision av betalningsadapterns timeoutkonfiguration";
            case "CATALOG_SOURCE_OF_TRUTH_VERSION" -> "katalogens källversion";
            case "CATALOG_TAX_CALCULATION_TRACE" -> "spårning av katalogens skatteberäkning";
            case "ORDER_CONSUMER_CONFIG_AUDIT" -> "revision av orderkonsumentens konfiguration";
            case "ORDER_IDEMPOTENCY_STORAGE_AUDIT" -> "revision av ordertjänstens idempotenslagring";
            default -> "underlag med kod " + code;
        };
    }

    private String diagnosedNextRead(String rootCauseCode) {
        return switch (rootCauseCode) {
            case "PAYMENT_TIMEOUT_CONFIG" -> "Granska det citerade "
                    + "timeoutkonfigurationskvittot och förbered en separat ändringsplan.";
            case "CATALOG_CACHE_INVALIDATION_FAILURE" -> "Granska de citerade "
                    + "cache- och versionskvittorna och förbered en separat ändringsplan.";
            case "ORDER_EVENT_CONSUMER_BACKLOG" -> "Granska det citerade "
                    + "konfigurationskvittot för orderkonsumenten och förbered "
                    + "en separat ändringsplan.";
            case "ORDER_IDEMPOTENCY_FAILURE" -> "Granska det citerade "
                    + "lagringskvittot för idempotens och förbered en separat ändringsplan.";
            default -> "Granska de citerade evidens-ID:na och förbered en separat ändringsplan.";
        };
    }

    private String insufficientNextRead(List<String> missingCodes) {
        if (missingCodes.isEmpty()) {
            return "Hämta mer skrivskyddat underlag innan en rotorsak bedöms.";
        }
        return "Hämta nästa skrivskyddade kontroll för "
                + missingEvidence(missingCodes.getFirst()) + ".";
    }

    private String unit(String raw) {
        return switch (raw) {
            case "count" -> "händelser";
            case "versions" -> "versioner";
            case "seconds" -> "sekunder";
            default -> raw;
        };
    }

    private String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
                .replace('.', ',');
    }

    record Presentation(
            AnswerState answerState,
            IncidentLabRunResponse.BusinessResponse businessResponse,
            IncidentLabRunResponse.DeveloperResponse developerResponse,
            IncidentLabRunResponse.ActionReceipt actionReceipt
    ) {
    }
}
