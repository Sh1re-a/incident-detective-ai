package dev.shirwac.incidentdetective.ai;

import com.google.genai.types.Candidate;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentResponse;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiInvestigationModelGatewayTest {

    @Test
    void recognizesTheSdkInterruptedIoTimeout() {
        assertTrue(GeminiInvestigationModelGateway.isTimeout(
                new InterruptedIOException("timeout")
        ));
    }

    @Test
    void recognizesATimeoutNestedByTheSdk() {
        IOException wrapper = new IOException(
                "request failed",
                new InterruptedIOException("timeout")
        );

        assertTrue(GeminiInvestigationModelGateway.isTimeout(wrapper));
    }

    @Test
    void doesNotTreatEveryIoFailureAsATimeout() {
        assertFalse(GeminiInvestigationModelGateway.isTimeout(
                new IOException("connection refused")
        ));
    }

    @Test
    void translatesARejectedCollectionResponseToTheProviderContract() {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway().decodeToolCalls(maxTokensResponse())
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, exception.failure());
    }

    @Test
    void translatesARejectedSynthesisResponseToTheProviderContract() {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway().decodeDiagnosis(maxTokensResponse())
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, exception.failure());
    }

    @Test
    void advertisesOnlyToolsThatRemainSafeForTheCurrentRound() {
        CollectionToolBudget budget = new CollectionToolBudget(
                4,
                3,
                Map.of(
                        ToolName.GET_METRICS, 1,
                        ToolName.SEARCH_LOGS, 1,
                        ToolName.GET_TRACE, 2,
                        ToolName.RETRIEVE_RUNBOOKS, 0
                ),
                Set.of("trace-4821")
        );

        assertEquals(
                java.util.List.of(
                        "get_metrics",
                        "search_logs",
                        "get_trace"
                ),
                gateway().allowedFunctionNames(budget)
        );
    }

    @Test
    void hidesTraceUntilAReadOnlyToolHasDiscoveredItsId() {
        CollectionToolBudget budget = new CollectionToolBudget(
                8,
                3,
                Map.of(
                        ToolName.GET_METRICS, 2,
                        ToolName.SEARCH_LOGS, 2,
                        ToolName.GET_TRACE, 2,
                        ToolName.RETRIEVE_RUNBOOKS, 1
                ),
                Set.of()
        );

        assertFalse(gateway().allowedFunctionNames(budget).contains("get_trace"));
    }

    private GeminiInvestigationModelGateway gateway() {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        return new GeminiInvestigationModelGateway(
                new GeminiAiProperties(
                        "test-key",
                        true,
                        "gemini-test",
                        GeminiThinkingLevel.LOW,
                        "test-prompt"
                ),
                new GeminiDiagnosisDecoder(
                        mapper,
                        Validation.buildDefaultValidatorFactory().getValidator()
                ),
                mapper
        );
    }

    private GenerateContentResponse maxTokensResponse() {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .finishReason(FinishReason.Known.MAX_TOKENS)
                        .build())
                .build();
    }
}
