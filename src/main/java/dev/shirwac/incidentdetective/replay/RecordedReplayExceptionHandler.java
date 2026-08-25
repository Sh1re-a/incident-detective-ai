package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class RecordedReplayExceptionHandler {

    @ExceptionHandler(ScenarioNotFoundException.class)
    ProblemDetail handleScenarioNotFound(ScenarioNotFoundException exception) {
        return ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "Recorded scenario not found",
                exception.getMessage(),
                "SCENARIO_NOT_FOUND"
        );
    }
}
