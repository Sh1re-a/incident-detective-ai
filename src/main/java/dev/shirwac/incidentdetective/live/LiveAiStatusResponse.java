package dev.shirwac.incidentdetective.live;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record LiveAiStatusResponse(
        String contractVersion,
        LiveState liveState,
        String reasonCode,
        @Schema(nullable = true)
        Instant resetsAt,
        @Schema(nullable = true)
        Long retryAfterSeconds,
        boolean replayAvailable,
        boolean dailyCostGuardActive,
        GlobalDailyLiveQuota.Scope quotaScope
) {
    public static final String CONTRACT_VERSION = "live-ai-status-v1";

    public enum LiveState {
        AVAILABLE("available"),
        DAILY_BUDGET_EXHAUSTED("daily_budget_exhausted"),
        DISABLED("disabled"),
        NOT_CONFIGURED("not_configured");

        private final String wireValue;

        LiveState(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }
}
