package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.rag.RagProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Shares the public live-AI admission and daily-cost boundary with new runtimes. */
@Component
public final class LiveAiRunGuard {

    private static final Set<String> PRICED_MODELS = Set.of(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3.7-flash"
    );
    private static final Set<String> PRICED_EMBEDDING_MODELS = Set.of(
            "gemini-embedding-2"
    );

    private final GeminiAiProperties properties;
    private final RagProperties ragProperties;
    private final LiveAiBudgetProperties budget;
    private final LiveInvestigationAdmissionGuard admission;
    private final GlobalDailyLiveQuota dailyQuota;

    LiveAiRunGuard(
            GeminiAiProperties properties,
            RagProperties ragProperties,
            LiveAiBudgetProperties budget,
            LiveInvestigationAdmissionGuard admission,
            GlobalDailyLiveQuota dailyQuota
    ) {
        this.properties = properties;
        this.ragProperties = ragProperties;
        this.budget = budget;
        this.admission = admission;
        this.dailyQuota = dailyQuota;
    }

    public <T> T runConfirmed(
            boolean confirmed,
            LiveAiOperation operation,
            Supplier<T> action
    ) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(action, "action must not be null");
        requireLiveAccess(confirmed, operation);
        return admission.admit(() -> {
            GlobalDailyLiveQuota.Decision decision;
            try {
                decision = dailyQuota.tryConsume(
                        LiveInvestigationService.DAILY_LIVE_RUN_LIMIT,
                        budget.dailyLimitMicroUsd(),
                        operation.allowanceMicroUsd()
                );
            } catch (DataAccessException exception) {
                throw new LiveAiBudgetStoreUnavailableException(exception);
            }
            if (!decision.allowed()) {
                throw new LiveDailyQuotaExceededException(decision.resetsAt());
            }
            return action.get();
        });
    }

    /**
     * Establishes confirmation precedence before a caller checks its own
     * feature availability. This does not admit a run or consume quota;
     * {@link #runConfirmed(boolean, LiveAiOperation, Supplier)} remains the
     * only live-execution boundary.
     */
    public void requireExplicitConfirmation(boolean confirmed) {
        if (!confirmed) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.CONFIRMATION_REQUIRED,
                    "Live AI request was not explicitly confirmed"
            );
        }
    }

    GlobalDailyLiveQuota.Snapshot budgetSnapshot() {
        try {
            return dailyQuota.snapshot(
                    LiveInvestigationService.DAILY_LIVE_RUN_LIMIT,
                    budget.dailyLimitMicroUsd()
            );
        } catch (DataAccessException exception) {
            throw new LiveAiBudgetStoreUnavailableException(exception);
        }
    }

    boolean hasApprovedCostProfile(LiveAiOperation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return PRICED_MODELS.contains(properties.modelId())
                && (!operation.embeddingPossible()
                || PRICED_EMBEDDING_MODELS.contains(
                        ragProperties.embeddingModel()
                ));
    }

    long completeIncidentLabAllowanceMicroUsd() {
        return LiveAiOperation.INCIDENT_PLAN.allowanceMicroUsd()
                + LiveAiOperation.ADK_TURN.allowanceMicroUsd();
    }

    GlobalDailyLiveQuota.Scope quotaScope() {
        return dailyQuota.scope();
    }

    private void requireLiveAccess(
            boolean confirmed,
            LiveAiOperation operation
    ) {
        requireExplicitConfirmation(confirmed);
        if (!properties.liveEnabled()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.LIVE_AI_DISABLED,
                    "Live AI is disabled by server configuration"
            );
        }
        if (!properties.hasProviderConfiguration()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.API_KEY_MISSING,
                    "Google Gen AI provider configuration is missing"
            );
        }
        if (!hasApprovedCostProfile(operation)) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.COST_PROFILE_MISSING,
                    "The configured model or embedding model has no approved live cost profile"
            );
        }
    }
}
