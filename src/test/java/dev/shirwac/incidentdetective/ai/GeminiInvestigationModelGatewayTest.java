package dev.shirwac.incidentdetective.ai;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void classifiesSdkHttp429WithoutCopyingProviderDetails() {
        ApiException sdkFailure = new ApiException(
                429,
                "RESOURCE_EXHAUSTED",
                "private provider response with request-id request-123"
        );

        ModelProviderException translated = GeminiInvestigationModelGateway
                .translateApiFailure(sdkFailure);

        assertEquals(ModelProviderFailure.RATE_LIMITED, translated.failure());
        assertEquals("Gemini provider rate limit reached", translated.getMessage());
        assertEquals(sdkFailure, translated.getCause());
        assertFalse(translated.getMessage().contains("request-123"));
        assertFalse(translated.getMessage().contains("RESOURCE_EXHAUSTED"));
    }

    @Test
    void keepsOtherSdkHttpFailuresInTheGenericUpstreamCategory() {
        ApiException sdkFailure = new ApiException(
                503,
                "UNAVAILABLE",
                "private provider response"
        );

        ModelProviderException translated = GeminiInvestigationModelGateway
                .translateApiFailure(sdkFailure);

        assertEquals(ModelProviderFailure.UPSTREAM, translated.failure());
        assertEquals("Gemini provider request failed", translated.getMessage());
    }

    @Test
    void translatesARejectedCollectionResponseToTheProviderContract() {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway().decodeToolCalls(maxTokensCollectionResponse())
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, exception.failure());
    }

    @Test
    void translatesARejectedSynthesisResponseToTheProviderContract() {
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> gateway().decodeDiagnosis(maxTokensSynthesisResponse())
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

    @Test
    void preservesTheProviderTokenAndCacheBreakdown() {
        GenerateContentResponseUsageMetadata usage =
                GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(1_000)
                        .cachedContentTokenCount(400)
                        .candidatesTokenCount(100)
                        .thoughtsTokenCount(50)
                        .toolUsePromptTokenCount(75)
                        .totalTokenCount(1_225)
                        .build();

        ModelTokenUsage decoded = gateway().decodeUsage(usage);

        assertEquals(1_000, decoded.inputTokens());
        assertEquals(400, decoded.cachedInputTokens());
        assertEquals(600, decoded.uncachedInputTokens());
        assertEquals(100, decoded.candidateOutputTokens());
        assertEquals(50, decoded.thinkingOutputTokens());
        assertEquals(150, decoded.outputTokens());
        assertEquals(75, decoded.toolUsePromptTokens());
        assertEquals(1_225, decoded.totalTokens());
    }

    @Test
    void keepsMissingProviderUsageUnavailable() {
        assertNull(gateway().decodeUsage(null));
    }

    @Test
    void keepsMissingProviderModelVersionUnavailable() {
        GeminiInvestigationModelGateway gateway = gateway();

        assertNull(gateway.providerReportedModelVersion(
                GenerateContentResponse.builder().build()
        ));
        assertEquals(
                "gemini-provider-version",
                gateway.providerReportedModelVersion(
                        GenerateContentResponse.builder()
                                .modelVersion("gemini-provider-version")
                                .build()
                )
        );
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
                        GeminiPromptContracts.LIVE_PROMPT_VERSION
                ),
                new DiagnosisContractProperties(
                        "ai/diagnosis-schema-v3.json"
                ),
                new GeminiDiagnosisDecoder(
                        mapper,
                        Validation.buildDefaultValidatorFactory().getValidator()
                ),
                mapper
        );
    }

    private GenerateContentResponse maxTokensCollectionResponse() {
        return maxTokensResponse(Part.builder()
                .functionCall(FunctionCall.builder()
                        .id("call-1")
                        .name("get_metrics")
                        .args(Map.of("metric_names", java.util.List.of("checkout_failure_ratio")))
                        .build())
                .build());
    }

    private GenerateContentResponse maxTokensSynthesisResponse() {
        return maxTokensResponse(Part.fromText("""
                {
                  "status": "insufficient_evidence",
                  "root_cause_code": null,
                  "affected_service": null,
                  "business_summary": "Evidence is insufficient.",
                  "technical_summary": "No root cause can be isolated.",
                  "claims": [],
                  "safe_next_step": {
                    "summary": "Collect more evidence.",
                    "requires_human_approval": true
                  }
                }
                """));
    }

    private GenerateContentResponse maxTokensResponse(Part part) {
        return GenerateContentResponse.builder()
                .candidates(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(part)
                                .build())
                        .finishReason(FinishReason.Known.MAX_TOKENS)
                        .build())
                .build();
    }
}
