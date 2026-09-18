package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static dev.shirwac.incidentdetective.api.ApiProblemResponse.Code.INCIDENT_LAB_REPLAY_NOT_AVAILABLE;

@RestControllerAdvice(assignableTypes = IncidentLabReplayController.class)
public final class IncidentLabReplayExceptionHandler {

    @ExceptionHandler(IncidentLabReplayUnavailableException.class)
    ProblemDetail handleUnavailable(
            IncidentLabReplayUnavailableException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Incident Lab replay unavailable",
                exception.getMessage(),
                INCIDENT_LAB_REPLAY_NOT_AVAILABLE
        );
    }
}
