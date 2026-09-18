package dev.shirwac.incidentdetective.incidentlab.followup;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record IncidentFollowUpResponse(
        String contractVersion,
        String runReference,
        String clientTurnId,
        Mode mode,
        String delivery,
        AnswerState answerState,
        Answer answer,
        List<Claim> claims,
        List<Citation> citations,
        List<Step> steps,
        Verification verification,
        Receipt receipt,
        @Schema(nullable = true)
        ProviderReceipt provider,
        List<SuggestedQuestion> suggestedQuestions,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "incident-lab-follow-up-v1";

    public IncidentFollowUpResponse {
        claims = claims == null ? List.of() : List.copyOf(claims);
        citations = citations == null ? List.of() : List.copyOf(citations);
        steps = steps == null ? List.of() : List.copyOf(steps);
        suggestedQuestions = suggestedQuestions == null
                ? List.of()
                : List.copyOf(suggestedQuestions);
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public enum Mode {
        LIVE_AI("live_ai"),
        RECORDED_REPLAY("recorded_replay");

        private final String wireValue;

        Mode(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public enum AnswerState {
        ANSWERED("answered"),
        OUTSIDE_SCOPE("outside_scope"),
        REPLAY_QUESTION_NOT_SUPPORTED("replay_question_not_supported");

        private final String wireValue;

        AnswerState(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public record Answer(
            String text,
            ProblemLocation problemLocation,
            Finding cause,
            String customerImpact,
            List<String> known,
            List<String> unknown,
            String boundary
    ) {
        public Answer {
            known = known == null ? List.of() : List.copyOf(known);
            unknown = unknown == null ? List.of() : List.copyOf(unknown);
        }
    }

    @Schema(name = "IncidentFollowUpProblemLocation")
    public record ProblemLocation(
            @Schema(nullable = true)
            String service,
            String summary,
            String certainty
    ) {
    }

    public record Finding(String summary, String certainty) {
    }

    public record Claim(
            ClaimSection section,
            String text,
            List<String> citationIds
    ) {
        public Claim {
            citationIds = citationIds == null
                    ? List.of()
                    : List.copyOf(citationIds);
        }
    }

    public enum ClaimSection {
        PROBLEM_LOCATION("problem_location"),
        CAUSE("cause"),
        CUSTOMER_IMPACT("customer_impact"),
        KNOWN("known"),
        UNKNOWN("unknown"),
        BOUNDARY("boundary");

        private final String wireValue;

        ClaimSection(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public record Citation(
            String evidenceId,
            String sourceRef,
            String sourceType,
            String label,
            TargetScene targetScene,
            String targetId
    ) {
    }

    public enum TargetScene {
        LOGS("logs"),
        AGENT_RAG("agent_rag"),
        JAVA("java");

        private final String wireValue;

        TargetScene(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    public record Step(
            int sequence,
            String code,
            String status,
            String summary,
            List<String> evidenceIds
    ) {
        public Step {
            evidenceIds = evidenceIds == null
                    ? List.of()
                    : List.copyOf(evidenceIds);
        }
    }

    public record Verification(
            String status,
            String snapshotSha256,
            boolean citationsValid,
            boolean sourceScopeValid,
            boolean answerStatePreserved,
            boolean writeToolsAvailable,
            boolean actionExecuted
    ) {
        public Verification {
            if (writeToolsAvailable || actionExecuted) {
                throw new IllegalArgumentException(
                        "Incident follow-ups are read-only"
                );
            }
        }
    }

    public record ProviderReceipt(
            GoogleGenAiProviderRoute route,
            String modelVersion,
            @Schema(nullable = true)
            String responseId,
            @Schema(nullable = true)
            ModelTokenUsage tokenUsage,
            long latencyMs,
            String purpose
    ) {
    }

    public record Receipt(
            int providerCalls,
            int modelCalls,
            int toolCalls,
            int readOperations,
            boolean liveQuotaConsumed,
            boolean writeToolsAvailable,
            boolean actionExecuted
    ) {
        public Receipt {
            if (providerCalls < 0 || modelCalls < 0 || toolCalls < 0
                    || readOperations < 0 || writeToolsAvailable
                    || actionExecuted) {
                throw new IllegalArgumentException(
                        "Incident follow-up receipt is invalid"
                );
            }
        }
    }

    public record SuggestedQuestion(String id, String label) {
    }
}
