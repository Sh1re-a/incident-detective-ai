package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Shares the public live-AI admission and daily-cost boundary with new runtimes. */
@Component
public final class LiveAiRunGuard {

    private final GeminiAiProperties properties;
    private final LiveInvestigationAdmissionGuard admission;
    private final GlobalDailyLiveQuota dailyQuota;

    LiveAiRunGuard(
            GeminiAiProperties properties,
            LiveInvestigationAdmissionGuard admission,
            GlobalDailyLiveQuota dailyQuota
    ) {
        this.properties = properties;
        this.admission = admission;
        this.dailyQuota = dailyQuota;
    }

    public <T> T runConfirmed(boolean confirmed, Supplier<T> action) {
        requireLiveAccess(confirmed);
        return admission.admit(() -> {
            GlobalDailyLiveQuota.Decision decision = dailyQuota.tryConsume(
                    LiveInvestigationService.DAILY_LIVE_RUN_LIMIT
            );
            if (!decision.allowed()) {
                throw new LiveDailyQuotaExceededException(decision.resetsAt());
            }
            return action.get();
        });
    }

    private void requireLiveAccess(boolean confirmed) {
        if (!confirmed) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.CONFIRMATION_REQUIRED,
                    "Live AI request was not explicitly confirmed"
            );
        }
        if (!properties.liveEnabled()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.LIVE_AI_DISABLED,
                    "Live AI is disabled by server configuration"
            );
        }
        if (!properties.hasApiKey()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.API_KEY_MISSING,
                    "Gemini API key is missing"
            );
        }
    }
}
