package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingException;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingFailure;
import dev.shirwac.incidentdetective.rag.RunbookIndexNotReadyException;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiveInvestigationExceptionHandlerTest {

    @Test
    void returnsSanitizedProviderRateLimitWithoutInventingRetryAfter() {
        LiveInvestigationExceptionHandler handler =
                new LiveInvestigationExceptionHandler();

        ResponseEntity<ProblemDetail> response = handler.handleProviderFailure(
                new ModelProviderException(
                        ModelProviderFailure.RATE_LIMITED,
                        "private provider response with request-id request-123"
                )
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNull(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("Model provider rate limited", response.getBody().getTitle());
        assertEquals(
                "MODEL_PROVIDER_RATE_LIMITED",
                response.getBody().getProperties().get("code")
        );
        assertEquals(
                "Gemini temporarily rejected the bounded model request.",
                response.getBody().getDetail()
        );
    }

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

    @Test
    void returnsSanitizedUnavailableProblemForAnUnreadyRunbookIndex() {
        LiveInvestigationExceptionHandler handler =
                new LiveInvestigationExceptionHandler();

        ProblemDetail problem = handler.handleRunbookIndexNotReady(
                new RunbookIndexNotReadyException(
                        new RunbookIndexStatus(11, 10, 12)
                )
        );

        assertProblem(
                problem,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Runbook retrieval unavailable",
                "The pgvector runbook index is not ready for live retrieval.",
                "RAG_INDEX_NOT_READY"
        );
    }

    @Test
    void mapsEmbeddingFailuresWithoutReturningProviderDetails() {
        LiveInvestigationExceptionHandler handler =
                new LiveInvestigationExceptionHandler();

        assertProblem(
                handler.handleRunbookEmbeddingFailure(
                        embeddingFailure(RunbookEmbeddingFailure.CONFIGURATION)
                ),
                HttpStatus.SERVICE_UNAVAILABLE,
                "Runbook retrieval unavailable",
                "The embedding provider is not configured for live retrieval.",
                "RAG_EMBEDDING_NOT_CONFIGURED"
        );
        assertProblem(
                handler.handleRunbookEmbeddingFailure(
                        embeddingFailure(RunbookEmbeddingFailure.UPSTREAM)
                ),
                HttpStatus.BAD_GATEWAY,
                "Runbook embedding provider failed",
                "The embedding provider could not complete the bounded retrieval request.",
                "RAG_EMBEDDING_PROVIDER_ERROR"
        );
        assertProblem(
                handler.handleRunbookEmbeddingFailure(
                        embeddingFailure(RunbookEmbeddingFailure.MALFORMED_RESPONSE)
                ),
                HttpStatus.BAD_GATEWAY,
                "Runbook embedding response rejected",
                "The embedding provider returned output outside the allowed contract.",
                "RAG_EMBEDDING_RESPONSE_INVALID"
        );
    }

    @Test
    void returnsSanitizedUnavailableProblemForRunbookDatabaseFailure() {
        LiveInvestigationExceptionHandler handler =
                new LiveInvestigationExceptionHandler();

        ProblemDetail problem = handler.handleRunbookDatabaseFailure(
                new DataAccessResourceFailureException(
                        "private jdbc url and database details"
                )
        );

        assertProblem(
                problem,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Runbook retrieval unavailable",
                "The pgvector runbook store is temporarily unavailable.",
                "RAG_DATABASE_UNAVAILABLE"
        );
    }

    private RunbookEmbeddingException embeddingFailure(
            RunbookEmbeddingFailure failure
    ) {
        return new RunbookEmbeddingException(
                failure,
                "private provider response with request-id request-123"
        );
    }

    private void assertProblem(
            ProblemDetail problem,
            HttpStatus status,
            String title,
            String detail,
            String code
    ) {
        assertEquals(status.value(), problem.getStatus());
        assertEquals(title, problem.getTitle());
        assertEquals(detail, problem.getDetail());
        assertEquals(code, problem.getProperties().get("code"));
    }
}
