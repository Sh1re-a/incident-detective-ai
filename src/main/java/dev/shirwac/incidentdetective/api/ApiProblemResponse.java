package dev.shirwac.incidentdetective.api;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiProblemResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Request path that produced the problem response."
        )
        String instance,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Stable machine-readable error code."
        )
        Code code
) {
    @Schema(
            name = "ApiErrorCode",
            description = "Stable machine-readable code for documented API failures."
    )
    public enum Code {
        INVALID_REQUEST_BODY,
        UNSUPPORTED_MEDIA_TYPE,
        METHOD_NOT_ALLOWED,
        ROUTE_NOT_FOUND,
        LIVE_AI_RATE_LIMITED,
        LIVE_AI_DAILY_LIMIT_REACHED,
        LIVE_AI_CONFIRMATION_REQUIRED,
        LIVE_AI_DISABLED,
        LIVE_AI_NOT_CONFIGURED,
        LIVE_AI_COST_PROFILE_UNAVAILABLE,
        LIVE_AI_BUDGET_UNAVAILABLE,
        LIVE_INVESTIGATION_TIMEOUT,
        MODEL_PROVIDER_TIMEOUT,
        MODEL_PROVIDER_RATE_LIMITED,
        MODEL_PROVIDER_ERROR,
        MALFORMED_MODEL_RESPONSE,
        ADK_CONTROL_RECEIPT_INVALID,
        INVALID_MODEL_TOOL_ARGUMENTS,
        RAG_INDEX_NOT_READY,
        RAG_EMBEDDING_NOT_CONFIGURED,
        RAG_EMBEDDING_PROVIDER_ERROR,
        RAG_EMBEDDING_RESPONSE_INVALID,
        RAG_DATABASE_UNAVAILABLE,
        INCIDENT_LAB_REPLAY_NOT_AVAILABLE,
        INCIDENT_FOLLOW_UP_RUN_EXPIRED,
        INCIDENT_FOLLOW_UP_IDEMPOTENCY_CONFLICT,
        INCIDENT_FOLLOW_UP_ALREADY_ATTEMPTED,
        INCIDENT_FOLLOW_UP_NOT_VERIFIABLE,
        SCENARIO_NOT_FOUND,
        KNOWLEDGE_QUESTION_NOT_FOUND,
        DEMO_ORDER_NOT_FOUND
    }
}
