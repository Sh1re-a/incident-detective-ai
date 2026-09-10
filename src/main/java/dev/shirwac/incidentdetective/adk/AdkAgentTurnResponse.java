package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Post-run receipt for one bounded ADK turn.
 *
 * <p>The ordered runtime events are projections of events emitted by ADK. The
 * separate verification event is produced by deterministic Java code after
 * ADK has finished.</p>
 */
public record AdkAgentTurnResponse(
        String contractVersion,
        String runId,
        @Schema(nullable = true)
        String sessionId,
        String turnId,
        String mode,
        String truthLabel,
        String outcome,
        @Schema(nullable = true)
        Scenario scenario,
        SafetyDecision safety,
        RuntimeProvenance runtime,
        List<RuntimeEvent> events,
        List<LiveToolEvent> toolEvents,
        @Schema(nullable = true)
        Diagnosis diagnosis,
        @Schema(nullable = true)
        VerificationReport verification,
        @Schema(nullable = true)
        ReplayComparison comparison,
        @Schema(nullable = true)
        VerificationEvent verificationEvent,
        ControlReceipt receipt,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "nordly-adk-turn-v1";
    public static final String MODE = "adk_live_ai";
    public static final String TRUTH_LABEL =
            "Generated synthetic incident — real Google ADK investigation.";

    public AdkAgentTurnResponse {
        events = events == null ? List.of() : List.copyOf(events);
        toolEvents = toolEvents == null ? List.of() : List.copyOf(toolEvents);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public record SafetyDecision(
            String decision,
            String reasonCode,
            String summarySv,
            String summaryEn
    ) {
    }

    public record RuntimeProvenance(
            String framework,
            String frameworkVersion,
            String agentName,
            String modelId,
            String promptVersion,
            String sessionService,
            String delivery,
            String eventSource,
            boolean runnerInvoked,
            boolean streamed
    ) {
    }

    public record RuntimeEvent(
            int sequence,
            String eventId,
            String invocationId,
            String author,
            String type,
            Instant observedAt,
            boolean finalResponse,
            boolean contentWithheld,
            @Schema(nullable = true)
            String text,
            List<FunctionCallEvent> functionCalls,
            List<FunctionResponseEvent> functionResponses,
            @Schema(nullable = true)
            ModelTokenUsage tokenUsage,
            @Schema(nullable = true)
            String providerModelVersion
    ) {
        public RuntimeEvent {
            functionCalls = functionCalls == null
                    ? List.of()
                    : List.copyOf(functionCalls);
            functionResponses = functionResponses == null
                    ? List.of()
                    : List.copyOf(functionResponses);
        }
    }

    public record FunctionCallEvent(
            @Schema(nullable = true)
            String id,
            String name,
            Map<String, Object> arguments
    ) {
        public FunctionCallEvent {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }

    public record FunctionResponseEvent(
            @Schema(nullable = true)
            String id,
            String name,
            Map<String, Object> response
    ) {
        public FunctionResponseEvent {
            response = response == null ? Map.of() : Map.copyOf(response);
        }
    }

    public record VerificationEvent(
            String source,
            Instant executedAt,
            boolean schemaValid,
            boolean citationsValid,
            boolean factualResultMatchesGroundTruth,
            boolean answerReleased,
            String summary
    ) {
    }

    public record ControlReceipt(
            int modelCalls,
            int adkToolCalls,
            int readOperations,
            int embeddingCalls,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            boolean humanApprovalRequired,
            List<String> registeredTools,
            @Schema(nullable = true)
            ModelTokenUsage tokenUsage,
            @Schema(nullable = true)
            BigDecimal estimatedCostUsd,
            String estimatedCostBasis,
            long totalLatencyMs
    ) {
        public ControlReceipt {
            registeredTools = registeredTools == null
                    ? List.of()
                    : List.copyOf(registeredTools);
        }
    }
}
