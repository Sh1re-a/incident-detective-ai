package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.InvalidToolArgumentsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class LiveInvestigationExceptionHandler {

    @ExceptionHandler(LiveAdmissionRejectedException.class)
    ResponseEntity<ProblemDetail> handleAdmissionRejection(
            LiveAdmissionRejectedException exception
    ) {
        String detail = exception.rejection() == LiveAdmissionRejection.CONCURRENT_RUN
                ? "Another live investigation is already running."
                : "The public live investigation limit has been reached.";
        ProblemDetail problem = problem(
                HttpStatus.TOO_MANY_REQUESTS,
                "Live AI is busy",
                detail,
                "LIVE_AI_RATE_LIMITED"
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
            case CONFIRMATION_REQUIRED -> problem(
                    HttpStatus.BAD_REQUEST,
                    "Live AI confirmation required",
                    "Set confirm_live_ai to true to allow this model call.",
                    "LIVE_AI_CONFIRMATION_REQUIRED"
            );
            case LIVE_AI_DISABLED -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Live AI unavailable",
                    "Live AI is disabled by server configuration.",
                    "LIVE_AI_DISABLED"
            );
            case API_KEY_MISSING -> problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Live AI unavailable",
                    "The model provider is not configured on the server.",
                    "LIVE_AI_NOT_CONFIGURED"
            );
            case DEADLINE_EXCEEDED -> problem(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Live investigation timed out",
                    "The live investigation exceeded its 45 second deadline.",
                    "LIVE_INVESTIGATION_TIMEOUT"
            );
        };
    }

    @ExceptionHandler(ModelProviderException.class)
    ProblemDetail handleProviderFailure(ModelProviderException exception) {
        return switch (exception.failure()) {
            case TIMEOUT -> problem(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Model provider timed out",
                    "Gemini did not respond within the bounded timeout.",
                    "MODEL_PROVIDER_TIMEOUT"
            );
            case UPSTREAM -> problem(
                    HttpStatus.BAD_GATEWAY,
                    "Model provider failed",
                    "Gemini could not complete the bounded model request.",
                    "MODEL_PROVIDER_ERROR"
            );
            case MALFORMED_RESPONSE -> problem(
                    HttpStatus.BAD_GATEWAY,
                    "Model response rejected",
                    "Gemini returned output outside the allowed contract.",
                    "MALFORMED_MODEL_RESPONSE"
            );
        };
    }

    @ExceptionHandler(InvalidToolArgumentsException.class)
    ProblemDetail handleInvalidModelToolArguments(
            InvalidToolArgumentsException exception
    ) {
        return problem(
                HttpStatus.BAD_GATEWAY,
                "Model tool arguments rejected",
                "Gemini returned arguments outside the strict read-only tool contract.",
                "INVALID_MODEL_TOOL_ARGUMENTS"
        );
    }

    @ExceptionHandler(InvestigationScenarioNotFoundException.class)
    ProblemDetail handleScenarioNotFound(
            InvestigationScenarioNotFoundException exception
    ) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Investigation scenario not found",
                exception.getMessage(),
                "SCENARIO_NOT_FOUND"
        );
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String code
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
