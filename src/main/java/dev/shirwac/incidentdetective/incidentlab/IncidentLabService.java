package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnService;
import dev.shirwac.incidentdetective.alarm.AlarmReceipt;
import dev.shirwac.incidentdetective.alarm.Http5xxBurstRule;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import dev.shirwac.incidentdetective.planning.IncidentBlastRadius;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import dev.shirwac.incidentdetective.planning.IncidentPlanDecision;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposal;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposalStatus;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidationResult;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidator;
import dev.shirwac.incidentdetective.planning.IncidentPlannerGateway;
import dev.shirwac.incidentdetective.planning.IncidentPlannerResponse;
import dev.shirwac.incidentdetective.planning.IncidentPlanningRequest;
import dev.shirwac.incidentdetective.planning.IncidentService;
import dev.shirwac.incidentdetective.planning.IncidentSeverity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Profile("rag")
public final class IncidentLabService {

    static final String TRUSTED_AGENT_MESSAGE =
            "Investigate the triggered synthetic PAYMENT_TIMEOUT alarm. "
                    + "Use only the bounded read-only evidence and runbooks. "
                    + "Return a diagnosis and a safe next step that requires human approval.";

    private static final List<String> PLAN_LIMITATIONS = List.of(
            "The model proposes a plan; deterministic Java owns approval.",
            "Only PAYMENT_TIMEOUT on PAYMENT_ADAPTER is runnable in v1.",
            "This endpoint does not execute an incident or call an agent.",
            "The handoff is stateless; the run endpoint revalidates every canonical plan field."
    );
    private static final List<String> RUN_LIMITATIONS = List.of(
            "All telemetry and business impact are synthetic and request-local.",
            "Events are returned after the synchronous run; this API does not stream live logs or hidden reasoning.",
            "The ADK agents are invoked only when the deterministic Java alarm fires.",
            "No write tool or automated remediation is available; a human owns the next action."
    );
    private static final Comparator<LogEvidence> LOG_ORDER = Comparator
            .comparing(LogEvidence::observedAt)
            .thenComparing(LogEvidence::evidenceId);
    private static final Pattern EXPLICIT_REAL_SCOPE = Pattern.compile(
            "\\b(?:produktion(?:ssystem(?:et)?)?|production(?: system)?|prod|"
                    + "live[- ]?system(?:et)?|verklig(?:a)? miljo|real environment|"
                    + "(?:riktig|verklig)(?:a)? kund(?:er|erna|ernas)?|real customers?)\\b"
    );

    private final KnowledgeRagSafetyGate safetyGate;
    private final LiveAiRunGuard liveAiRunGuard;
    private final IncidentPlannerGateway planner;
    private final IncidentPlanValidator planValidator;
    private final GeneratedCaseFactory generatedCases;
    private final Http5xxBurstRule alarmRule;
    private final AdkAgentTurnService adkAgent;

    public IncidentLabService(
            KnowledgeRagSafetyGate safetyGate,
            LiveAiRunGuard liveAiRunGuard,
            IncidentPlannerGateway planner,
            GeneratedCaseFactory generatedCases,
            AdkAgentTurnService adkAgent
    ) {
        this.safetyGate = safetyGate;
        this.liveAiRunGuard = liveAiRunGuard;
        this.planner = planner;
        this.generatedCases = generatedCases;
        this.adkAgent = adkAgent;
        planValidator = new IncidentPlanValidator();
        alarmRule = new Http5xxBurstRule();
    }

    public IncidentLabPlanResponse createPlan(IncidentLabPlanRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        IncidentPlanningRequest planningRequest = new IncidentPlanningRequest(
                request.instruction()
        );
        if (requestsRealScope(planningRequest.instruction())) {
            return blockedRealScopePlan();
        }
        KnowledgeRagSafetyGate.Decision safety = safetyGate.evaluate(
                planningRequest.instruction()
        );
        if (!safety.allowed()) {
            return blockedPlan(safety);
        }

        return liveAiRunGuard.runConfirmed(
                request.confirmLiveAi(),
                () -> proposeAndValidate(planningRequest, safety)
        );
    }

    public IncidentLabRunResponse run(IncidentLabRunRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        IncidentPlan canonicalPlan = requireCanonicalPlan(request.plan());
        GeneratedCase generated = generatedCases.create(new GeneratedCaseRequest(
                request.seed(),
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        ));
        List<LogEvidence> backendLogs = generated.investigationData()
                .evidenceInventory()
                .stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .sorted(LOG_ORDER)
                .toList();
        Optional<AlarmReceipt> alarm = alarmRule.evaluate(backendLogs);
        AdkAgentTurnResponse agentTurn = alarm.isPresent()
                ? adkAgent.runGeneratedCase(
                        generated,
                        TRUSTED_AGENT_MESSAGE,
                        request.confirmLiveAi()
                )
                : null;

        return new IncidentLabRunResponse(
                IncidentLabRunResponse.CONTRACT_VERSION,
                runOutcome(alarm, agentTurn),
                IncidentLabRunResponse.DELIVERY,
                IncidentLabRunResponse.TRUTH_LABEL,
                canonicalPlan,
                generated.scenario(),
                backendLogs,
                alarm.orElse(null),
                agentTurn,
                RUN_LIMITATIONS
        );
    }

    private IncidentLabPlanResponse proposeAndValidate(
            IncidentPlanningRequest request,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        IncidentPlannerResponse planned = planner.propose(request);
        IncidentPlanValidationResult validation = planValidator.validate(
                planned.proposal()
        );
        String outcome = validation.accepted()
                ? "plan_ready"
                : "plan_rejected";
        return new IncidentLabPlanResponse(
                IncidentLabPlanResponse.CONTRACT_VERSION,
                outcome,
                IncidentLabPlanResponse.DELIVERY,
                IncidentLabPlanResponse.TRUTH_LABEL,
                safety(safety),
                planned.proposal(),
                validation,
                planned.receipt(),
                PLAN_LIMITATIONS
        );
    }

    private IncidentLabPlanResponse blockedPlan(
            KnowledgeRagSafetyGate.Decision safety
    ) {
        return new IncidentLabPlanResponse(
                IncidentLabPlanResponse.CONTRACT_VERSION,
                "blocked_before_ai",
                IncidentLabPlanResponse.DELIVERY,
                IncidentLabPlanResponse.BLOCKED_TRUTH_LABEL,
                safety(safety),
                null,
                null,
                null,
                List.of(
                        "The request was stopped before Gemini, generation, or ADK ran.",
                        "No fallback plan was fabricated."
                )
        );
    }

    private IncidentLabPlanResponse blockedRealScopePlan() {
        return new IncidentLabPlanResponse(
                IncidentLabPlanResponse.CONTRACT_VERSION,
                "blocked_before_ai",
                IncidentLabPlanResponse.DELIVERY,
                IncidentLabPlanResponse.BLOCKED_TRUTH_LABEL,
                new IncidentLabPlanResponse.SafetyReceipt(
                        "blocked",
                        "real_environment_not_allowed",
                        "Begäran nämner en verklig miljö och stoppades före AI. Incidentlabbet får endast skapa lokal syntetisk testdata.",
                        "The request names a real environment and was stopped before AI. The incident lab may create local synthetic test data only."
                ),
                null,
                null,
                null,
                List.of(
                        "The request was stopped before Gemini, generation, or ADK ran.",
                        "No production system, customer, or persistent data can be targeted."
                )
        );
    }

    private String runOutcome(
            Optional<AlarmReceipt> alarm,
            AdkAgentTurnResponse agentTurn
    ) {
        if (alarm.isEmpty()) {
            return "no_alarm";
        }
        if (agentTurn == null) {
            return "alarm_detected_investigation_not_run";
        }
        return switch (agentTurn.outcome()) {
            case "completed" -> "alarm_investigated";
            case "verification_failed" ->
                    "alarm_detected_investigation_withheld";
            case "blocked_before_ai" ->
                    "alarm_detected_investigation_blocked";
            default -> "alarm_detected_investigation_incomplete";
        };
    }

    private boolean requestsRealScope(String instruction) {
        String decomposed = Normalizer.normalize(
                instruction,
                Normalizer.Form.NFD
        );
        String normalized = decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
        return EXPLICIT_REAL_SCOPE.matcher(normalized).find();
    }

    private IncidentPlan requireCanonicalPlan(IncidentPlan submitted) {
        Objects.requireNonNull(submitted, "plan must not be null");
        IncidentPlanProposal proposal = new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                submitted.summary(),
                submitted.incidentFamily(),
                submitted.severity(),
                submitted.affectedServices(),
                IncidentBlastRadius.SINGLE_SERVICE
        );
        IncidentPlanValidationResult validation = planValidator.validate(proposal);
        if (validation.decision() != IncidentPlanDecision.APPROVED
                || !submitted.equals(validation.plan())
                || submitted.incidentFamily()
                != GeneratedIncidentFamily.PAYMENT_TIMEOUT
                || submitted.severity() != IncidentSeverity.HIGH
                || !submitted.affectedServices().equals(
                List.of(IncidentService.PAYMENT_ADAPTER)
        )) {
            throw new InvalidIncidentLabPlanException();
        }
        return submitted;
    }

    private IncidentLabPlanResponse.SafetyReceipt safety(
            KnowledgeRagSafetyGate.Decision decision
    ) {
        String summarySv = decision.allowed()
                ? "Instruktionen får gå till planeringsmodellen; Java avgör om förslaget blir körbart."
                : decision.summarySv();
        String summaryEn = decision.allowed()
                ? "The instruction may reach the planning model; Java decides whether the proposal is runnable."
                : decision.summaryEn();
        return new IncidentLabPlanResponse.SafetyReceipt(
                decision.allowed() ? "allowed" : "blocked",
                decision.reasonCode().name().toLowerCase(Locale.ROOT),
                summarySv,
                summaryEn
        );
    }
}
