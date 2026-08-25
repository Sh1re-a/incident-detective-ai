package dev.shirwac.incidentdetective.live;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveInvestigationExceptionHandlerTest {

    @Test
    void returnsSanitizedRateLimitProblemWithRetryAfter() {
        LiveInvestigationExceptionHandler handler =
                new LiveInvestigationExceptionHandler();

        ResponseEntity<ProblemDetail> response = handler.handleAdmissionRejection(
                new LiveAdmissionRejectedException(
                        LiveAdmissionRejection.ROLLING_LIMIT,
                        Duration.ofMillis(2_001)
                )
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("3", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Live AI is busy", response.getBody().getTitle());
        assertEquals(
                "LIVE_AI_RATE_LIMITED",
                response.getBody().getProperties().get("code")
        );
        assertEquals(
                "The public live investigation limit has been reached.",
                response.getBody().getDetail()
        );
    }
}
