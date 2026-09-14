package dev.shirwac.incidentdetective.incidentlab;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.generated.GeneratedCaseReceipt;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Objects;

public record IncidentLabRunResponse(
        String contractVersion,
        String outcome,
        String delivery,
        String truthLabel,
        AnswerState answerState,
        BusinessResponse businessResponse,
        DeveloperResponse developerResponse,
        ActionReceipt actionReceipt,
        @Schema(description = "Backend-owned Swedish and English projections of the same Java-verified facts; never model translations.")
        LocalizedPresentations localizedPresentations,
        IncidentPlan plan,
        GeneratedCaseReceipt generationReceipt,
        Scenario scenario,
        List<LogEvidence> backendLogs,
        @Schema(nullable = true)
        SignalAlarmReceipt alarmReceipt,
        @Schema(nullable = true)
        AdkAgentTurnResponse agentTurn,
        List<String> limitations
) {

    public static final String CONTRACT_VERSION = "incident-lab-run-v3";
    public static final String DELIVERY = "synchronous_post_run";
    public static final String TRUTH_LABEL =
            "Backend-generated synthetic telemetry — deterministic Java alarm; Google ADK runs only when triggered.";

    public IncidentLabRunResponse {
        Objects.requireNonNull(
                localizedPresentations,
                "localizedPresentations must not be null"
        );
        backendLogs = backendLogs == null ? List.of() : List.copyOf(backendLogs);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public enum AnswerState {
        DIAGNOSED("diagnosed"),
        INSUFFICIENT_EVIDENCE("insufficient_evidence"),
        WITHHELD("withheld"),
        NOT_STARTED("not_started");

        private final String wireValue;

        AnswerState(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public record BusinessResponse(
            String headline,
            String whatHappened,
            String impact,
            List<String> whatIsKnown,
            List<String> whatRemainsUnknown,
            String safeNextStep,
            String certainty,
            boolean humanApprovalRequired
    ) {
        public BusinessResponse {
            whatIsKnown = whatIsKnown == null
                    ? List.of()
                    : List.copyOf(whatIsKnown);
            whatRemainsUnknown = whatRemainsUnknown == null
                    ? List.of()
                    : List.copyOf(whatRemainsUnknown);
        }
    }

    public record DeveloperResponse(
            String summary,
            @Schema(nullable = true)
            String rootCauseCode,
            @Schema(nullable = true)
            String affectedService,
            List<VerifiedClaim> verifiedClaims,
            List<String> highlightedLogEvidenceIds,
            List<String> missingEvidenceCodes,
            List<String> failedVerificationChecks,
            String nextRead
    ) {
        public DeveloperResponse {
            verifiedClaims = verifiedClaims == null
                    ? List.of()
                    : List.copyOf(verifiedClaims);
            highlightedLogEvidenceIds = highlightedLogEvidenceIds == null
                    ? List.of()
                    : List.copyOf(highlightedLogEvidenceIds);
            missingEvidenceCodes = missingEvidenceCodes == null
                    ? List.of()
                    : List.copyOf(missingEvidenceCodes);
            failedVerificationChecks = failedVerificationChecks == null
                    ? List.of()
                    : List.copyOf(failedVerificationChecks);
        }
    }

    public record VerifiedClaim(
            String claimCode,
            String claimValueCode,
            List<String> evidenceIds
    ) {
        public VerifiedClaim {
            evidenceIds = evidenceIds == null
                    ? List.of()
                    : List.copyOf(evidenceIds);
        }
    }

    /**
     * Backend-owned copy for every supported presentation language.
     *
     * <p>Both variants are projected from the same Java-verified facts and
     * receipts. They are not model translations.</p>
     */
    public record LocalizedPresentations(
            LocalizedPresentation sv,
            LocalizedPresentation en
    ) {
        public LocalizedPresentations {
            if (sv == null || en == null) {
                throw new IllegalArgumentException(
                        "Both sv and en presentations are required"
                );
            }
        }
    }

    public record LocalizedPresentation(
            BusinessResponse businessResponse,
            DeveloperResponse developerResponse,
            ActionReceipt actionReceipt
    ) {
        public LocalizedPresentation {
            if (businessResponse == null
                    || developerResponse == null
                    || actionReceipt == null) {
                throw new IllegalArgumentException(
                        "A localized presentation must be complete"
                );
            }
        }
    }

    public record ActionReceipt(
            String status,
            int readOperations,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            boolean humanApprovalRequired,
            @Schema(nullable = true)
            String proposedNextStep,
            String summary
    ) {
        public ActionReceipt {
            if (readOperations < 0) {
                throw new IllegalArgumentException(
                        "readOperations must not be negative"
                );
            }
            if (writeToolsAvailable
                    || actionExecuted
                    || !humanApprovalRequired) {
                throw new IllegalArgumentException(
                        "Incident Lab actions must remain proposed-only"
                );
            }
        }
    }
}
