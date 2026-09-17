package dev.shirwac.incidentdetective.incidentlab.followup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Structured Cloud Logging lifecycle without prompts, questions, or answers. */
@Component
public final class IncidentFollowUpEventLogger {

    static final String EVENT_STARTED = "incident_lab.follow_up.started";
    static final String EVENT_COMPLETED = "incident_lab.follow_up.completed";
    static final String EVENT_FAILED = "incident_lab.follow_up.failed";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            IncidentFollowUpEventLogger.class
    );

    public void started(
            String runReference,
            String clientTurnId,
            IncidentFollowUpResponse.Mode mode
    ) {
        LOGGER.atInfo()
                .addKeyValue("event_name", EVENT_STARTED)
                .addKeyValue("operation", "incident_follow_up")
                .addKeyValue("run_ref_hash", hash(runReference))
                .addKeyValue("turn_ref_hash", hash(clientTurnId))
                .addKeyValue("mode", mode.wireValue())
                .addKeyValue("status", "started")
                .addKeyValue("write_tools_available", false)
                .addKeyValue("action_executed", false)
                .log("Incident Lab follow-up started");
    }

    public void completed(IncidentFollowUpResponse response) {
        long latencyMs = response.provider() == null
                ? 0
                : response.provider().latencyMs();
        LOGGER.atInfo()
                .addKeyValue("event_name", EVENT_COMPLETED)
                .addKeyValue("operation", "incident_follow_up")
                .addKeyValue("run_ref_hash", hash(response.runReference()))
                .addKeyValue("turn_ref_hash", hash(response.clientTurnId()))
                .addKeyValue("mode", response.mode().wireValue())
                .addKeyValue("status", "completed")
                .addKeyValue("answer_state", response.answerState().wireValue())
                .addKeyValue("citation_count", response.citations().size())
                .addKeyValue(
                        "provider_call_count",
                        response.receipt().providerCalls()
                )
                .addKeyValue(
                        "model_call_count",
                        response.receipt().modelCalls()
                )
                .addKeyValue(
                        "read_operation_count",
                        response.receipt().readOperations()
                )
                .addKeyValue("provider_latency_ms", latencyMs)
                .addKeyValue("write_tools_available", false)
                .addKeyValue("action_executed", false)
                .log("Incident Lab follow-up completed");
    }

    public void failed(
            String runReference,
            String clientTurnId,
            RuntimeException failure
    ) {
        LOGGER.atWarn()
                .addKeyValue("event_name", EVENT_FAILED)
                .addKeyValue("operation", "incident_follow_up")
                .addKeyValue("run_ref_hash", hash(runReference))
                .addKeyValue("turn_ref_hash", hash(clientTurnId))
                .addKeyValue("status", "failed")
                .addKeyValue(
                        "error_type",
                        failure.getClass().getSimpleName()
                )
                .addKeyValue("write_tools_available", false)
                .addKeyValue("action_executed", false)
                .log("Incident Lab follow-up failed");
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (Exception exception) {
            return "hash_unavailable";
        }
    }
}
