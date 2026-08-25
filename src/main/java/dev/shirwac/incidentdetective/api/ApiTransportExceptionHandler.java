package dev.shirwac.incidentdetective.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiTransportExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ApiProblemFactory.create(
                HttpStatus.BAD_REQUEST,
                "Invalid request body",
                "Send a valid JSON body that matches the endpoint contract.",
                "INVALID_REQUEST_BODY"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type",
                "Send the request body as application/json.",
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                "Use the HTTP method documented for this endpoint.",
                "METHOD_NOT_ALLOWED"
        );
    }
}
