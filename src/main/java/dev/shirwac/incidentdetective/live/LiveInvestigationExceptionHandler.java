package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.InvalidToolArgumentsException;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingException;
import dev.shirwac.incidentdetective.rag.RunbookIndexNotReadyException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Duration;
import java.time.Instant;

import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.*;

@RestControllerAdvice
public final class LiveInvestigationExceptionHandler {

    @ExceptionHandler(LiveDailyQuotaExceededException.class)
    ResponseEntity<ProblemDetail> handleDailyQuota(
            LiveDailyQuotaExceededException exception
    ) {
        ProblemDetail problem = ApiProblemFactory.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Daily live AI limit reached",
                "The public demo has used its live AI allowance for today.",
                LIVE_AI_DAILY_LIMIT_REACHED
        );
        long retryAfterSeconds = Math.max(
                1,
                Duration.between(Instant.now(), exception.resetsAt()).toSeconds()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(LiveAdmissionRejectedException.class)
    ResponseEntity<ProblemDetail> handleAdmissionRejection(
            LiveAdmissionRejectedException exception
    ) {
        String detail = exception.rejection() == LiveAdmissionRejection.CONCURRENT_RUN
                ? "Another live investigation is already running."
                : "The public live investigation limit has been reached.";
        ProblemDetail problem = ApiProblemFactory.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Live AI is busy",
                detail,
                LIVE_AI_RATE_LIMITED
        );
        long retryAfterSeconds = Math.max(
                1,
                (exception.retryAfter().toMillis() + 999) / 1_000
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds))
                .body(problem);
    }

    @ExceptionHandler(LiveInvestigationException.class)
    ProblemDetail handleLiveFailure(LiveInvestigationException exception) {
        return switch (exception.failure()) {
            case CONFIRMATION_REQUIRED -> ApiProblemFactory.create(
                    HttpStatus.BAD_REQUEST,
                    "Live AI confirmation required",
                    "Set confirm_live_ai to true to allow this model call.",
                    LIVE_AI_CONFIRMATION_REQUIRED
            );
            case LIVE_AI_DISABLED -> ApiProblemFactory.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Live AI unavailable",
                    "Live AI is disabled by server configuration.",
                    LIVE_AI_DISABLED
            );
            case API_KEY_MISSING -> ApiProblemFactory.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Live AI unavailable",
                    "The model provider is not configured on the server.",
                    LIVE_AI_NOT_CONFIGURED
            );
            case DEADLINE_EXCEEDED -> ApiProblemFactory.create(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Live investigation timed out",
                    "The live investigation exceeded its 45 second deadline.",
                    LIVE_INVESTIGATION_TIMEOUT
            );
        };
    }

    @ExceptionHandler(ModelProviderException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure(
            ModelProviderException exception
    ) {
        ProblemDetail problem = switch (exception.failure()) {
            case TIMEOUT -> ApiProblemFactory.create(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Model provider timed out",
                    "Gemini did not respond within the bounded timeout.",
                    MODEL_PROVIDER_TIMEOUT
            );
            case RATE_LIMITED -> ApiProblemFactory.create(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Model provider rate limited",
                    "Gemini temporarily rejected the bounded model request.",
                    MODEL_PROVIDER_RATE_LIMITED
            );
            case UPSTREAM -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Model provider failed",
                    "Gemini could not complete the bounded model request.",
                    MODEL_PROVIDER_ERROR
            );
            case MALFORMED_RESPONSE -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Model response rejected",
                    "Gemini returned output outside the allowed contract.",
                    MALFORMED_MODEL_RESPONSE
            );
        };
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(InvalidToolArgumentsException.class)
    ProblemDetail handleInvalidModelToolArguments(
            InvalidToolArgumentsException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.BAD_GATEWAY,
                "Model tool arguments rejected",
                "Gemini returned arguments outside the strict read-only tool contract.",
                INVALID_MODEL_TOOL_ARGUMENTS
        );
    }

    @ExceptionHandler(RunbookIndexNotReadyException.class)
    ProblemDetail handleRunbookIndexNotReady(
            RunbookIndexNotReadyException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Runbook retrieval unavailable",
                "The pgvector runbook index is not ready for live retrieval.",
                RAG_INDEX_NOT_READY
        );
    }

    @ExceptionHandler(RunbookEmbeddingException.class)
    ProblemDetail handleRunbookEmbeddingFailure(
            RunbookEmbeddingException exception
    ) {
        return switch (exception.failure()) {
            case CONFIGURATION -> ApiProblemFactory.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Runbook retrieval unavailable",
                    "The embedding provider is not configured for live retrieval.",
                    RAG_EMBEDDING_NOT_CONFIGURED
            );
            case UPSTREAM -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Runbook embedding provider failed",
                    "The embedding provider could not complete the bounded retrieval request.",
                    RAG_EMBEDDING_PROVIDER_ERROR
            );
            case MALFORMED_RESPONSE -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Runbook embedding response rejected",
                    "The embedding provider returned output outside the allowed contract.",
                    RAG_EMBEDDING_RESPONSE_INVALID
            );
        };
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleRunbookDatabaseFailure(DataAccessException exception) {
        return ApiProblemFactory.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Runbook retrieval unavailable",
                "The pgvector runbook store is temporarily unavailable.",
                RAG_DATABASE_UNAVAILABLE
        );
    }

    @ExceptionHandler(InvestigationScenarioNotFoundException.class)
    ProblemDetail handleScenarioNotFound(
            InvestigationScenarioNotFoundException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "Investigation scenario not found",
                exception.getMessage(),
                SCENARIO_NOT_FOUND
        );
    }

}
