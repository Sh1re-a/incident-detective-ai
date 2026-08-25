package dev.shirwac.incidentdetective.replay;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class RecordedReplayExceptionHandler {

    @ExceptionHandler(ScenarioNotFoundException.class)
    ProblemDetail handleScenarioNotFound(ScenarioNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage()
        );
        problem.setTitle("Recorded scenario not found");
        return problem;
    }
}
