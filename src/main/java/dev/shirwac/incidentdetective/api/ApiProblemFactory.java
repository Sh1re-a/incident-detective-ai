package dev.shirwac.incidentdetective.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblemFactory {

    private ApiProblemFactory() {
    }

    public static ProblemDetail create(
            HttpStatus status,
            String title,
            String detail,
            ApiProblemResponse.Code code
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code.name());
        return problem;
    }
}
