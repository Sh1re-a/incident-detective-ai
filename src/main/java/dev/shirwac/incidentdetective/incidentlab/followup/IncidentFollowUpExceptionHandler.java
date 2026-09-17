package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INCIDENT_FOLLOW_UP_ALREADY_ATTEMPTED;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INCIDENT_FOLLOW_UP_IDEMPOTENCY_CONFLICT;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INCIDENT_FOLLOW_UP_NOT_VERIFIABLE;
import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INCIDENT_FOLLOW_UP_RUN_EXPIRED;

@RestControllerAdvice(assignableTypes = IncidentFollowUpController.class)
@Profile("rag")
public final class IncidentFollowUpExceptionHandler {

    @ExceptionHandler(IncidentFollowUpException.class)
    ProblemDetail handle(IncidentFollowUpException exception) {
        return switch (exception.code()) {
            case RUN_REFERENCE_EXPIRED -> ApiProblemFactory.create(
                    HttpStatus.NOT_FOUND,
                    "Incident run expired",
                    "This synthetic incident is no longer available for follow-up questions.",
                    INCIDENT_FOLLOW_UP_RUN_EXPIRED
            );
            case IDEMPOTENCY_CONFLICT -> ApiProblemFactory.create(
                    HttpStatus.CONFLICT,
                    "Follow-up turn conflict",
                    "The client turn ID was already used for another question.",
                    INCIDENT_FOLLOW_UP_IDEMPOTENCY_CONFLICT
            );
            case TURN_ALREADY_ATTEMPTED -> ApiProblemFactory.create(
                    HttpStatus.CONFLICT,
                    "Follow-up already attempted",
                    "The provider outcome is uncertain or failed. Automatic retry is disabled; submit a new client turn ID.",
                    INCIDENT_FOLLOW_UP_ALREADY_ATTEMPTED
            );
            case REPLAY_QUESTION_NOT_SUPPORTED, RESPONSE_NOT_VERIFIABLE ->
                    ApiProblemFactory.create(
                            HttpStatus.BAD_GATEWAY,
                            "Incident follow-up withheld",
                            "The response could not be bound to the frozen incident receipt.",
                            INCIDENT_FOLLOW_UP_NOT_VERIFIABLE
                    );
        };
    }
}
