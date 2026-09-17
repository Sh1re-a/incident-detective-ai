package dev.shirwac.incidentdetective.observability;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.generated.GeneratedCaseReceipt;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidationResult;
import dev.shirwac.incidentdetective.planning.IncidentPlannerReceipt;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits safe, structured lifecycle events for the synthetic Incident Lab.
 *
 * <p>The public methods accept only server-controlled identifiers, enums,
 * booleans, counts and aggregate provider receipts. Prompts, user text,
 * evidence content, model output and customer data are deliberately absent
 * from this API so they cannot accidentally reach Cloud Logging.</p>
 */
@Component
public final class IncidentLabEventLogger {

    static final String EVENT_PLAN_DECIDED = "incident_lab.plan.decided";
    static final String EVENT_RUN_STARTED = "incident_lab.run.started";
    static final String EVENT_ALARM_EVALUATED =
            "incident_lab.alarm.evaluated";
    static final String EVENT_ADK_STARTED = "incident_lab.adk.started";
    static final String EVENT_ADK_COMPLETED = "incident_lab.adk.completed";
    static final String EVENT_VERIFICATION_COMPLETED =
            "incident_lab.verification.completed";
    static final String EVENT_RUN_COMPLETED = "incident_lab.run.completed";
    static final String EVENT_OPERATION_FAILED =
            "incident_lab.operation.failed";

    private static final Logger LOG = LoggerFactory.getLogger(
            IncidentLabEventLogger.class
    );

    public String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Stable fingerprint of executable plan controls only. The free-text plan
     * summary is intentionally excluded.
     */
    public String planRef(IncidentPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        List<String> services = plan.affectedServiceCodes().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        String canonical = String.join("|",
                plan.contractVersion(),
                plan.incidentFamily().name(),
                plan.severity().name(),
                String.join(",", services),
                Boolean.toString(plan.syntheticOnly()),
                Boolean.toString(plan.writeActionsAllowed()),
                Boolean.toString(plan.humanApprovalRequired())
        );
        return "plan-" + sha256(canonical).substring(0, 16);
    }

    public void planDecided(
            String correlationId,
            String outcome,
            String safetyDecision,
            String safetyReason,
            IncidentPlanValidationResult validation,
            IncidentPlannerReceipt providerReceipt
    ) {
        var event = baseInfo(EVENT_PLAN_DECIDED, correlationId)
                .addKeyValue("operation", "plan")
                .addKeyValue("outcome", safeCode(outcome))
                .addKeyValue("safety_decision", safeCode(safetyDecision))
                .addKeyValue("safety_reason", safeCode(safetyReason))
                .addKeyValue("provider_called", providerReceipt != null);
        if (validation != null) {
            event.addKeyValue(
                    "java_decision",
                    validation.decision().name().toLowerCase(Locale.ROOT)
            );
            if (validation.plan() != null) {
                IncidentPlan plan = validation.plan();
                event.addKeyValue("plan_ref", planRef(plan))
                        .addKeyValue(
                                "incident_family",
                                plan.incidentFamily().wireValue()
                        )
                        .addKeyValue(
                                "severity_code",
                                plan.severity().wireValue()
                        )
                        .addKeyValue(
                                "affected_service_count",
                                plan.affectedServices().size()
                        )
                        .addKeyValue("synthetic_only", plan.syntheticOnly())
                        .addKeyValue(
                                "write_actions_allowed",
                                plan.writeActionsAllowed()
                        )
                        .addKeyValue(
                                "human_approval_required",
                                plan.humanApprovalRequired()
                        );
            }
        }
        if (providerReceipt != null) {
            event.addKeyValue(
                    "provider_transport",
                    safeCode(providerReceipt.transport())
            ).addKeyValue(
                    "model_id",
                    safeCode(providerReceipt.model())
            ).addKeyValue(
                    "provider_latency_ms",
                    providerReceipt.latencyMs()
            );
            addTokenUsage(event, providerReceipt.tokenUsage());
        }
        event.log("Incident Lab plan decision recorded");
    }

    public void runStarted(
            String correlationId,
            String planRef,
            GeneratedCaseReceipt generation,
            int backendLogCount
    ) {
        baseInfo(EVENT_RUN_STARTED, correlationId)
                .addKeyValue("operation", "run")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("scenario_id", safeCode(generation.scenarioId()))
                .addKeyValue(
                        "incident_family",
                        generation.incidentFamily().wireValue()
                )
                .addKeyValue(
                        "evidence_mode",
                        generation.evidenceMode().wireValue()
                )
                .addKeyValue(
                        "noise_level",
                        generation.noiseLevel().wireValue()
                )
                .addKeyValue("backend_log_count", backendLogCount)
                .addKeyValue("synthetic_only", true)
                .addKeyValue("write_tools_available", false)
                .log("Incident Lab synthetic run started");
    }

    public void alarmEvaluated(
            String correlationId,
            String planRef,
            String scenarioId,
            SignalAlarmReceipt alarm
    ) {
        var event = baseInfo(EVENT_ALARM_EVALUATED, correlationId)
                .addKeyValue("operation", "alarm")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("scenario_id", safeCode(scenarioId))
                .addKeyValue("alarm_fired", alarm != null);
        if (alarm != null) {
            event.addKeyValue("alarm_id", safeCode(alarm.alarmId()))
                    .addKeyValue("rule_id", safeCode(alarm.ruleId()))
                    .addKeyValue("service_code", safeCode(alarm.service()))
                    .addKeyValue(
                            "signal_name",
                            safeCode(alarm.signal().name())
                    )
                    .addKeyValue(
                            "signal_comparison",
                            alarm.signal().comparison().wireValue()
                    )
                    .addKeyValue(
                            "signal_threshold",
                            alarm.signal().thresholdValue()
                    )
                    .addKeyValue(
                            "signal_observed",
                            alarm.signal().observedValue()
                    )
                    .addKeyValue(
                            "signal_unit",
                            safeCode(alarm.signal().unit())
                    )
                    .addKeyValue(
                            "alarm_evidence_count",
                            alarm.evidenceIds().size()
                    );
            if (alarm.signal().lookbackSeconds() != null) {
                event.addKeyValue(
                        "signal_lookback_seconds",
                        alarm.signal().lookbackSeconds()
                );
            }
        }
        event.log("Incident Lab deterministic alarm evaluated");
    }

    public void adkStarted(
            String correlationId,
            String planRef,
            SignalAlarmReceipt alarm
    ) {
        baseInfo(EVENT_ADK_STARTED, correlationId)
                .addKeyValue("operation", "adk")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("scenario_id", safeCode(alarm.scenarioId()))
                .addKeyValue("alarm_id", safeCode(alarm.alarmId()))
                .addKeyValue("rule_id", safeCode(alarm.ruleId()))
                .addKeyValue("framework", "google_adk")
                .addKeyValue("workflow_type", "sequential")
                .addKeyValue("write_tools_available", false)
                .log("Incident Lab ADK investigation started");
    }

    public void adkCompleted(
            String correlationId,
            String planRef,
            String alarmId,
            AdkAgentTurnResponse turn
    ) {
        AdkAgentTurnResponse.ControlReceipt receipt = turn.receipt();
        var event = baseInfo(EVENT_ADK_COMPLETED, correlationId)
                .addKeyValue("operation", "adk")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("alarm_id", safeCode(alarmId))
                .addKeyValue("adk_run_id", safeCode(turn.runId()))
                .addKeyValue("adk_turn_id", safeCode(turn.turnId()))
                .addKeyValue("adk_outcome", safeCode(turn.outcome()))
                .addKeyValue("model_call_count", receipt.modelCalls())
                .addKeyValue("tool_call_count", receipt.adkToolCalls())
                .addKeyValue("read_operation_count", receipt.readOperations())
                .addKeyValue("embedding_call_count", receipt.embeddingCalls())
                .addKeyValue(
                        "write_tools_available",
                        receipt.writeToolsAvailable()
                )
                .addKeyValue("action_executed", receipt.actionExecuted())
                .addKeyValue(
                        "human_approval_required",
                        receipt.humanApprovalRequired()
                )
                .addKeyValue("total_latency_ms", receipt.totalLatencyMs());
        addTokenUsage(event, receipt.tokenUsage());
        addCost(event, receipt.estimatedCostUsd());
        if (turn.workflow() != null) {
            event.addKeyValue(
                    "workflow_completed_in_order",
                    turn.workflow().completedInOrder()
            ).addKeyValue(
                    "observed_agent_count",
                    turn.workflow().observedAgentOrder().size()
            );
        }
        event.log("Incident Lab ADK investigation completed");
    }

    public void verificationCompleted(
            String correlationId,
            String planRef,
            String alarmId,
            AdkAgentTurnResponse turn
    ) {
        AdkAgentTurnResponse.VerificationEvent verification =
                turn.verificationEvent();
        var event = baseInfo(EVENT_VERIFICATION_COMPLETED, correlationId)
                .addKeyValue("operation", "verification")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("alarm_id", safeCode(alarmId))
                .addKeyValue("adk_run_id", safeCode(turn.runId()))
                .addKeyValue("adk_outcome", safeCode(turn.outcome()))
                .addKeyValue("verification_available", verification != null);
        if (verification != null) {
            event.addKeyValue("schema_valid", verification.schemaValid())
                    .addKeyValue(
                            "citations_valid",
                            verification.citationsValid()
                    )
                    .addKeyValue(
                            "direct_evidence_support_valid",
                            verification.directEvidenceSupportValid()
                    )
                    .addKeyValue(
                            "agent_sequence_valid",
                            verification.agentSequenceValid()
                    )
                    .addKeyValue(
                            "tool_boundary_valid",
                            verification.toolBoundaryValid()
                    )
                    .addKeyValue(
                            "answer_released",
                            verification.answerReleased()
                    );
        }
        event.log("Incident Lab Java verification completed");
    }

    public void runCompleted(
            String correlationId,
            String planRef,
            String outcome,
            IncidentLabRunResponse.AnswerState answerState,
            boolean alarmFired,
            boolean adkInvoked
    ) {
        baseInfo(EVENT_RUN_COMPLETED, correlationId)
                .addKeyValue("operation", "run")
                .addKeyValue("plan_ref", safeCode(planRef))
                .addKeyValue("outcome", safeCode(outcome))
                .addKeyValue(
                        "answer_state",
                        answerState.wireValue()
                )
                .addKeyValue("alarm_fired", alarmFired)
                .addKeyValue("adk_invoked", adkInvoked)
                .addKeyValue("synthetic_only", true)
                .addKeyValue("action_executed", false)
                .log("Incident Lab run completed");
    }

    public void operationFailed(
            String correlationId,
            String operation,
            String planRef,
            RuntimeException failure
    ) {
        var event = LOG.atError()
                .addKeyValue("event_name", EVENT_OPERATION_FAILED)
                .addKeyValue("correlation_id", safeCode(correlationId))
                .addKeyValue("operation", safeCode(operation))
                .addKeyValue(
                        "error_type",
                        safeCode(failure.getClass().getSimpleName())
                );
        if (planRef != null) {
            event.addKeyValue("plan_ref", safeCode(planRef));
        }
        event.log("Incident Lab operation failed");
    }

    private org.slf4j.spi.LoggingEventBuilder baseInfo(
            String eventName,
            String correlationId
    ) {
        return LOG.atInfo()
                .addKeyValue("event_name", eventName)
                .addKeyValue("correlation_id", safeCode(correlationId));
    }

    private void addTokenUsage(
            org.slf4j.spi.LoggingEventBuilder event,
            ModelTokenUsage usage
    ) {
        if (usage == null) {
            return;
        }
        addNumber(event, "input_tokens", usage.inputTokens());
        addNumber(event, "cached_input_tokens", usage.cachedInputTokens());
        addNumber(event, "output_tokens", usage.outputTokens());
        addNumber(event, "total_tokens", usage.totalTokens());
    }

    private void addCost(
            org.slf4j.spi.LoggingEventBuilder event,
            BigDecimal estimatedCostUsd
    ) {
        if (estimatedCostUsd != null) {
            event.addKeyValue("estimated_cost_usd", estimatedCostUsd);
        }
    }

    private void addNumber(
            org.slf4j.spi.LoggingEventBuilder event,
            String key,
            Integer value
    ) {
        if (value != null) {
            event.addKeyValue(key, value);
        }
    }

    private String safeCode(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        String stripped = value.strip();
        if (stripped.length() > 120
                || !stripped.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            return "invalid";
        }
        return stripped;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
