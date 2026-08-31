package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.LiveInvestigationService;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key",
        "incident-detective.ai.model-id=gemini-3.1-flash-lite"
})
@AutoConfigureMockMvc
class GeneratedCaseApiTest {

    private static final String PATH =
            "/api/v1/generated-cases/runs/live-ai";
    private static final Instant QUOTA_RESET =
            Instant.parse("2026-09-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private InvestigationModelGateway model;

    @MockitoBean
    private GlobalDailyLiveQuota dailyQuota;

    @BeforeEach
    void configureRequestLocalInvestigation() {
        reset(model, dailyQuota);
        when(dailyQuota.tryConsume(anyInt())).thenReturn(
                new GlobalDailyLiveQuota.Decision(true, 1, 20, QUOTA_RESET)
        );
        stubCollectionRounds();
        when(model.synthesize(any(), anyList(), any())).thenAnswer(invocation -> {
            List<Evidence> evidence = invocation.getArgument(1);
            return new SynthesisModelResult(
                    diagnosticDiagnosis(evidence),
                    metadata(ModelPhase.SYNTHESIZE, 1)
            );
        });
    }

    @Test
    void confirmationFalseReturnsBadRequestWithoutCallingTheModel()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(42, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));

        verifyNoInteractions(model);
        verify(dailyQuota, never()).tryConsume(anyInt());
    }

    @Test
    void rejectsMissingOrUnknownGeneratorControlsBeforeCallingTheModel()
            throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidence_mode": "diagnostic",
                                  "noise_level": "none",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed": 42,
                                  "evidence_mode": "diagnostic",
                                  "noise_level": "none",
                                  "confirm_live_ai": true,
                                  "uploaded_logs": ["not allowed"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));

        verifyNoInteractions(model);
        verify(dailyQuota, never()).tryConsume(anyInt());
    }

    @Test
    void sameSeedReproducesGenerationMetadataAndScenario() throws Exception {
        JsonNode first = performDiagnosticRun(42);
        JsonNode second = performDiagnosticRun(42);

        assertEquals(first.get("generation"), second.get("generation"));
        assertEquals(
                first.at("/investigation/scenario"),
                second.at("/investigation/scenario")
        );
        assertEquals(
                first.at("/investigation/scenario_id").asText(),
                second.at("/investigation/scenario_id").asText()
        );
        assertNotEquals(
                first.at("/investigation/run_id").asText(),
                second.at("/investigation/run_id").asText()
        );
    }

    @Test
    void diagnosticRunUsesRequestLocalToolsAndKeepsGroundTruthHidden()
            throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(99, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value(GeneratedCaseRunResult.CONTRACT_VERSION))
                .andExpect(jsonPath("$.generation.generator_version")
                        .value(GeneratedCaseFactory.GENERATOR_VERSION))
                .andExpect(jsonPath("$.generation.seed").value(99))
                .andExpect(jsonPath("$.investigation.truth_label").value(
                        LiveInvestigationService.GENERATED_TRUTH_LABEL
                ))
                .andExpect(jsonPath("$.investigation.status").value("completed"))
                .andExpect(jsonPath("$.investigation.tool_call_count").value(5))
                .andExpect(jsonPath("$.investigation.model_call_count").value(3))
                .andExpect(jsonPath(
                        "$.investigation.verification.ground_truth_schema_pass"
                ).value(true))
                .andExpect(jsonPath(
                        "$.investigation.verification.claim_coverage.score"
                ).value(1.0))
                .andExpect(jsonPath(
                        "$.investigation.comparison.root_cause_correct"
                ).value(true))
                .andExpect(jsonPath(
                        "$.investigation.tool_events[*].tool_name"
                ).value(containsInAnyOrder(
                        "get_metrics",
                        "search_logs",
                        "retrieve_runbooks",
                        "get_trace",
                        "search_logs"
                )))
                .andExpect(jsonPath("$.hidden_ground_truth").doesNotExist())
                .andExpect(jsonPath(
                        "$.investigation.hidden_ground_truth"
                ).doesNotExist())
                .andReturn();

        JsonNode response = jsonMapper.readTree(
                result.getResponse().getContentAsString()
        );
        String scenarioId = response.at("/investigation/scenario_id").asText();
        assertTrue(scenarioId.startsWith("generated-payment-timeout-"));
        assertAllToolEvidenceBelongsToScenario(response, scenarioId);
        assertFactoryRunbookWasExposedThroughARequestLocalTool(response);
        assertNull(response.get("hidden_ground_truth"));
        assertNull(response.get("investigation").get("hidden_ground_truth"));
        assertNull(response.get("investigation").get("ground_truth"));
    }

    @Test
    void openApiDocumentsTheGeneratedLiveEndpoint() throws Exception {
        String post = "$.paths['" + PATH + "'].post";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(post + ".summary").value(
                        "Generate and investigate a synthetic payment-timeout case"
                ))
                .andExpect(jsonPath(
                        post
                                + ".requestBody.content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/GeneratedCaseLiveRequest"))
                .andExpect(jsonPath(
                        post
                                + ".responses['200'].content['application/json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/GeneratedCaseRunResult"))
                .andExpect(jsonPath(
                        post
                                + ".responses['400']"
                                + ".content['application/problem+json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ApiProblemResponse"))
                .andExpect(jsonPath(
                        post
                                + ".responses['415']"
                                + ".content['application/problem+json']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ApiProblemResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratedCaseLiveRequest.required"
                ).value(containsInAnyOrder(
                        "seed",
                        "evidence_mode",
                        "noise_level",
                        "confirm_live_ai"
                )))
                .andExpect(jsonPath(
                        "$.components.schemas.GeneratedCaseRunResult"
                                + ".properties.hidden_ground_truth"
                ).doesNotExist());
    }

    private JsonNode performDiagnosticRun(long seed) throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(seed, true)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }

    private void stubCollectionRounds() {
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        )).thenAnswer(invocation -> {
            Scenario scenario = invocation.getArgument(0);
            List<String> metricNames = invocation.getArgument(1);
            String start = scenario.timeWindow().start().toString();
            String end = scenario.timeWindow().end().toString();
            return new CollectionModelResult(
                    List.of(
                            call("generated-metrics", ToolName.GET_METRICS, Map.of(
                                    "metric_names", metricNames,
                                    "start", start,
                                    "end", end
                            )),
                            call("generated-timeout-logs", ToolName.SEARCH_LOGS, Map.of(
                                    "services", List.of("PAYMENT_ADAPTER"),
                                    "levels", List.of(),
                                    "query", "timeout",
                                    "start", start,
                                    "end", end
                            )),
                            call(
                                    "generated-runbook",
                                    ToolName.RETRIEVE_RUNBOOKS,
                                    Map.of(
                                            "query", "payment timeout",
                                            "max_results", 2
                                    )
                            )
                    ),
                    metadata(ModelPhase.COLLECT, 1)
            );
        });

        when(model.collect(
                any(), anyList(), anyList(), any(), eq(2), any()
        )).thenAnswer(invocation -> {
            Scenario scenario = invocation.getArgument(0);
            List<Evidence> collected = invocation.getArgument(2);
            String traceId = collected.stream()
                    .filter(LogEvidence.class::isInstance)
                    .map(LogEvidence.class::cast)
                    .map(log -> log.content().attributes().get("trace_id"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseThrow();
            return new CollectionModelResult(
                    List.of(
                            call("generated-trace", ToolName.GET_TRACE, Map.of(
                                    "trace_id", traceId
                            )),
                            call("generated-release-log", ToolName.SEARCH_LOGS, Map.of(
                                    "services", List.of("PAYMENT_ADAPTER"),
                                    "levels", List.of(),
                                    "query", "release",
                                    "start", scenario.timeWindow().start().toString(),
                                    "end", scenario.timeWindow().end().toString()
                            ))
                    ),
                    metadata(ModelPhase.COLLECT, 2)
            );
        });
    }

    private Diagnosis diagnosticDiagnosis(List<Evidence> evidence) {
        String timeoutConfig = evidenceId(evidence, "-log-timeout-config");
        String timeoutError = evidenceId(evidence, "-log-timeout-error");
        String failedTrace = evidenceId(evidence, "-trace-failed-checkout");
        String release = evidenceId(evidence, "-log-release");
        String failureRatio = evidenceId(
                evidence,
                "-metric-checkout-failure-ratio"
        );
        String failedAttempts = evidenceId(
                evidence,
                "-metric-failed-checkouts"
        );
        String paymentP95 = evidenceId(evidence, "-metric-payment-p95");

        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "PAYMENT_TIMEOUT_CONFIG",
                "PAYMENT_ADAPTER",
                "Generated checkout attempts fail during payment authorization.",
                "The generated timeout is below the observed authorization duration.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                "PAYMENT_TIMEOUT_CONFIG",
                                timeoutConfig,
                                failedTrace
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                "PAYMENT_ADAPTER",
                                timeoutError,
                                failedTrace
                        ),
                        claim(
                                ClaimCode.TRIGGER,
                                "PAYMENT_ADAPTER_RELEASE",
                                release,
                                timeoutConfig
                        ),
                        claim(
                                ClaimCode.CUSTOMER_IMPACT,
                                "CHECKOUT_PAYMENT_FAILURES",
                                failureRatio,
                                failedAttempts
                        ),
                        claim(
                                ClaimCode.OBSERVED_SYMPTOM,
                                "PAYMENT_LATENCY_SPIKE",
                                paymentP95,
                                failedTrace
                        )
                ),
                new SafeNextStep(
                        "Review the generated timeout evidence with a human.",
                        true
                )
        );
    }

    private Claim claim(
            ClaimCode code,
            String value,
            String firstEvidence,
            String secondEvidence
    ) {
        return new Claim(
                code,
                value,
                "Generated evidence supports " + value + ".",
                List.of(firstEvidence, secondEvidence)
        );
    }

    private String evidenceId(List<Evidence> evidence, String suffix) {
        return evidence.stream()
                .map(Evidence::evidenceId)
                .filter(id -> id.endsWith(suffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected generated evidence ending with " + suffix
                ));
    }

    private void assertAllToolEvidenceBelongsToScenario(
            JsonNode response,
            String scenarioId
    ) {
        int evidenceCount = 0;
        for (JsonNode event : response.at("/investigation/tool_events")) {
            for (JsonNode evidence : event.get("evidence")) {
                assertEquals(scenarioId, evidence.get("scenario_id").asText());
                evidenceCount++;
            }
        }
        assertTrue(evidenceCount > 0);
    }

    private void assertFactoryRunbookWasExposedThroughARequestLocalTool(
            JsonNode response
    ) {
        boolean found = response.at("/investigation/tool_events")
                .values()
                .stream()
                .filter(event -> "retrieve_runbooks".equals(
                        event.get("tool_name").asText()
                ))
                .anyMatch(event -> !event.get("evidence").isEmpty());
        assertTrue(found);
    }

    private CollectionToolCall call(
            String callId,
            ToolName toolName,
            Map<String, Object> arguments
    ) {
        return new CollectionToolCall(callId, toolName, arguments);
    }

    private ModelCallMetadata metadata(ModelPhase phase, int round) {
        return new ModelCallMetadata(
                phase,
                round,
                "generated-test-" + phase.wireValue() + "-" + round,
                "gemini-test-version",
                new ModelTokenUsage(
                        120,
                        20,
                        100,
                        30,
                        0,
                        30,
                        0,
                        150
                ),
                5
        );
    }

    private String request(long seed, boolean confirmLiveAi) {
        return """
                {
                  "seed": %d,
                  "evidence_mode": "diagnostic",
                  "noise_level": "none",
                  "confirm_live_ai": %s
                }
                """.formatted(seed, confirmLiveAi);
    }
}
