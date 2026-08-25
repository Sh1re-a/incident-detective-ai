package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "run.gemini.smoke", matches = "true")
class GeminiLiveSmokeIT {

    @Autowired
    private LiveInvestigationService service;

    @Autowired
    private GeminiAiProperties properties;

    @Value("${gemini.smoke.scenario:checkout-orders-at-risk-v1}")
    private String scenarioId;

    @Test
    void runsOneExplicitlyEnabledRealGeminiInvestigation() {
        assertTrue(properties.liveEnabled(),
                "Set INCIDENT_DETECTIVE_LIVE_AI_ENABLED=true first");
        assertTrue(properties.hasApiKey(),
                "Set GEMINI_API_KEY in the ignored local secrets file first");

        LiveInvestigationResult result = service.investigate(
                scenarioId,
                new LiveInvestigationRequest(true)
        );

        assertEquals(RunMode.LIVE_AI, result.mode());
        assertEquals(LiveInvestigationService.TRUTH_LABEL, result.truthLabel());
        assertNotNull(result.diagnosis());
        assertTrue(result.verification().diagnosisSchemaPass());
        assertTrue(result.verification().citationValidity().valid());
        assertTrue(result.diagnosis().safeNextStep().requiresHumanApproval());
        assertTrue(result.modelCallCount() >= 2 && result.modelCallCount() <= 3);
        assertTrue(result.toolCallCount() >= 1 && result.toolCallCount() <= 8);
        assertTrue(result.tokenUsage().totalTokens() > 0);
        assertTrue(result.latencyMs() < 45_000);

        System.out.printf(
                Locale.ROOT,
                "LIVE_SMOKE_OK scenario=%s model=%s thinking=%s "
                        + "run_status=%s diagnosis=%s root_cause=%s "
                        + "affected_service=%s tools=%s "
                        + "model_calls=%d tool_calls=%d input_tokens=%d "
                        + "output_tokens=%d latency_ms=%d estimated_cost_usd=%s "
                        + "root_cause_correct=%s affected_service_correct=%s "
                        + "citations_valid=%s%n",
                result.scenarioId(),
                result.modelId(),
                properties.thinkingLevel(),
                result.status(),
                result.diagnosis().status(),
                result.diagnosis().rootCauseCode(),
                result.diagnosis().affectedService(),
                result.toolEvents().stream()
                        .map(event -> event.toolName().wireValue())
                        .toList(),
                result.modelCallCount(),
                result.toolCallCount(),
                result.tokenUsage().inputTokens(),
                result.tokenUsage().outputTokens(),
                result.latencyMs(),
                result.estimatedCostUsd(),
                result.verification().diagnosisCorrectness().rootCauseCorrect(),
                result.verification().diagnosisCorrectness()
                        .affectedServiceCorrect(),
                result.verification().citationValidity().valid()
        );
    }
}
