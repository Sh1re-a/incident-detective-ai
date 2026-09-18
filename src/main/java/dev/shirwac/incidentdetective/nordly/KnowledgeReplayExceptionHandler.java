package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.api.ApiProblemFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = KnowledgeReplayController.class)
public final class KnowledgeReplayExceptionHandler {

    @ExceptionHandler(KnowledgeQuestionNotFoundException.class)
    ProblemDetail handleQuestionNotFound(
            KnowledgeQuestionNotFoundException exception
    ) {
        return ApiProblemFactory.create(
                HttpStatus.NOT_FOUND,
                "Nordly knowledge question not found",
                exception.getMessage(),
                dev.shirwac.incidentdetective.api.ApiProblemResponse.Code
                        .KNOWLEDGE_QUESTION_NOT_FOUND
        );
    }
}
