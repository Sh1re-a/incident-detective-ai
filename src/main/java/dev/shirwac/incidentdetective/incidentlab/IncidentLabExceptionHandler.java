package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import dev.shirwac.incidentdetective.planning.IncidentPlannerException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.ADK_CONTROL_RECEIPT_INVALID;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INVALID_REQUEST_BODY;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.LIVE_AI_DISABLED;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.LIVE_AI_NOT_CONFIGURED;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.MALFORMED_MODEL_RESPONSE;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.MODEL_PROVIDER_ERROR;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.MODEL_PROVIDER_RATE_LIMITED;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.MODEL_PROVIDER_TIMEOUT;

@RestControllerAdvice(basePackageClasses = IncidentLabController.class)
@Profile("rag")
public final class IncidentLabExceptionHandler {

    @ExceptionHandler(InvalidAdkControlReceiptException.class)
    ProblemDetail handleInvalidControlReceipt(
            InvalidAdkControlReceiptException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.BAD_GATEWAY,
                "ADK result withheld",
                "The investigation did not provide a valid read-only control receipt. No diagnosis was returned.",
                ADK_CONTROL_RECEIPT_INVALID
        );
    }

    @ExceptionHandler(InvalidIncidentLabPlanException.class)
    ProblemDetail handleInvalidPlan(InvalidIncidentLabPlanException exception) {
        return ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST,
                "Incident plan rejected",
                "The submitted fields must exactly match the canonical runnable v1 plan.",
                INVALID_REQUEST_BODY
        );
    }

    @ExceptionHandler(IncidentPlannerException.class)
    ProblemDetail handlePlannerFailure(IncidentPlannerException exception) {
        return switch (exception.failure()) {
            case DISABLED -> ApiProblemFactory.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Incident planner unavailable",
                    "Live AI is disabled by server configuration.",
                    LIVE_AI_DISABLED
            );
            case NOT_CONFIGURED -> ApiProblemFactory.create(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Incident planner unavailable",
                    "The model provider is not configured on the server.",
                    LIVE_AI_NOT_CONFIGURED
            );
            case TIMEOUT -> ApiProblemFactory.create(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Incident planner timed out",
                    "Gemini did not return an incident proposal within the bounded timeout.",
                    MODEL_PROVIDER_TIMEOUT
            );
            case RATE_LIMITED -> ApiProblemFactory.create(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Incident planner rate limited",
                    "Gemini temporarily rejected the bounded planning request.",
                    MODEL_PROVIDER_RATE_LIMITED
            );
            case UPSTREAM -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Incident planner failed",
                    "Gemini could not complete the bounded planning request.",
                    MODEL_PROVIDER_ERROR
            );
            case MALFORMED_RESPONSE -> ApiProblemFactory.create(
                    HttpStatus.BAD_GATEWAY,
                    "Incident plan response rejected",
                    "Gemini returned a proposal outside the allowed plan contract.",
                    MALFORMED_MODEL_RESPONSE
            );
        };
    }
}
