package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DemoOrderController.class)
public final class DemoOrderExceptionHandler {

    @ExceptionHandler(DemoOrderNotFoundException.class)
    ProblemDetail handleNotFound(DemoOrderNotFoundException exception) {
        return ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "Nordly demo order not found",
                exception.getMessage(),
                ApiProblemResponse.Code.DEMO_ORDER_NOT_FOUND
        );
    }
}
