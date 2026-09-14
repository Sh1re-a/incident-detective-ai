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
            Scenario scenario = invocation.getArgument(0);
            List<Evidence> evidence = invocation.getArgument(1);
            return new SynthesisModelResult(
                    diagnosticDiagnosis(scenario, evidence),
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
                                  "incident_family": "warehouse_robots",
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
                .andExpect(jsonPath("$.generation.incident_family")
                        .value("payment_timeout"))
                .andExpect(jsonPath("$.investigation.truth_label").value(
                        LiveInvestigationService.GENERATED_TRUTH_LABEL
                ))
                .andExpect(jsonPath("$.investigation.status").value("completed"))
                .andExpect(jsonPath("$.investigation.tool_call_count").value(5))
                .andExpect(jsonPath("$.investigation.model_call_count").value(3))
                .andExpect(jsonPath(
                        "$.investigation.token_usage.input_tokens"
                ).value(360))
                .andExpect(jsonPath(
                        "$.investigation.token_usage.cached_input_tokens"
                ).value(60))
                .andExpect(jsonPath(
                        "$.investigation.token_usage.output_tokens"
                ).value(90))
                .andExpect(jsonPath(
                        "$.investigation.estimated_cost_usd"
                ).value(0.0002115))
                .andExpect(jsonPath(
                        "$.investigation.model_cost_breakdown.uncached_input_usd"
                ).value(0.000075))
                .andExpect(jsonPath(
                        "$.investigation.model_cost_breakdown.cached_input_usd"
                ).value(0.0000015))
                .andExpect(jsonPath(
                        "$.investigation.model_cost_breakdown.output_usd"
                ).value(0.000135))
                .andExpect(jsonPath(
                        "$.investigation.model_cost_breakdown"
                                + ".observed_cache_savings_usd"
                ).value(0.0000135))
                .andExpect(jsonPath(
                        "$.investigation.verification.ground_truth_schema_pass"
                ).value(true))
                .andExpect(jsonPath(
                        "$.investigation.verification.claim_coverage.score"
                ).value(1.0))
                .andExpect(jsonPath("$.investigation.comparison")
                        .value((Object) null))
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
        assertTrue(response.get("investigation").get("comparison").isNull());
    }

    @Test
    void selectedNordlyFamilyIsEchoedAndChangesTheGeneratedScenario()
            throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(
                                42,
                                "catalog_cache_invalidation",
                                true
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generation.incident_family")
                        .value("catalog_cache_invalidation"))
                .andExpect(jsonPath("$.investigation.scenario_id")
                        .value(org.hamcrest.Matchers.startsWith(
                                "generated-catalog-cache-invalidation-"
                        )))
                .andExpect(jsonPath("$.investigation.scenario.affected_services[*]")
                        .value(org.hamcrest.Matchers.hasItem("CATALOG_SERVICE")))
                .andReturn();

        JsonNode response = jsonMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertEquals(
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                response.at("/investigation/diagnosis/root_cause_code").asText()
        );
        assertTrue(response.get("investigation").get("comparison").isNull());
    }

    @Test
    void openApiDocumentsTheGeneratedLiveEndpoint() throws Exception {
        String post = "$.paths['" + PATH + "'].post";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(post + ".summary").value(
                        "Generate and investigate a synthetic Nordly incident"
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
                        "$.components.schemas.GeneratedCaseLiveRequest"
                                + ".properties.incident_family.enum"
                ).value(containsInAnyOrder(
                        "payment_timeout",
                        "catalog_cache_invalidation",
                        "order_event_backlog",
                        "order_idempotency_failure"
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
                                    "services", List.of(),
                                    "levels", List.of(),
                                    "query", failureQuery(scenario),
                                    "start", start,
                                    "end", end
                            )),
                            call(
                                    "generated-runbook",
                                    ToolName.RETRIEVE_RUNBOOKS,
                                    Map.of(
                                            "query", runbookQuery(scenario),
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
                                    "services", List.of(),
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

    private Diagnosis diagnosticDiagnosis(
            Scenario scenario,
            List<Evidence> evidence
    ) {
        if (!scenario.scenarioId().startsWith("generated-payment-timeout-")) {
            return nordlyDiagnosticDiagnosis(scenario, evidence);
        }
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

    private Diagnosis nordlyDiagnosticDiagnosis(
            Scenario scenario,
            List<Evidence> evidence
    ) {
        FamilyDiagnosis expected = familyDiagnosis(scenario.scenarioId());
        return new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                expected.rootCause(),
                expected.affectedService(),
                "Generated Nordly operations are affected in the selected family.",
                "The generated evidence shows the selected bounded failure mechanism.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                expected.rootCause(),
                                evidenceId(evidence, "-log-causal-config"),
                                evidenceId(evidence, "-trace-failure")
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                expected.affectedService(),
                                evidenceId(evidence, "-log-failure"),
                                evidenceId(evidence, "-trace-failure")
                        ),
                        claim(
                                ClaimCode.TRIGGER,
                                expected.trigger(),
                                evidenceId(evidence, "-log-change-event"),
                                evidenceId(evidence, "-log-causal-config")
                        ),
                        claim(
                                ClaimCode.CUSTOMER_IMPACT,
                                expected.impact(),
                                evidenceId(evidence, "-metric-impact-ratio"),
                                evidenceId(evidence, "-metric-impact-count")
                        ),
                        claim(
                                ClaimCode.OBSERVED_SYMPTOM,
                                expected.symptom(),
                                evidenceId(evidence, "-metric-symptom"),
                                evidenceId(evidence, "-log-failure")
                        )
                ),
                new SafeNextStep(
                        "Review the generated evidence with a human.",
                        true
                )
        );
    }

    private FamilyDiagnosis familyDiagnosis(String scenarioId) {
        if (scenarioId.startsWith("generated-catalog-cache-invalidation-")) {
            return new FamilyDiagnosis(
                    "CATALOG_CACHE_INVALIDATION_FAILURE",
                    "CATALOG_SERVICE",
                    "CATALOG_INVALIDATION_CONFIG_CHANGE",
                    "STALE_CATALOG_RESULTS",
                    "CATALOG_VERSION_DIVERGENCE"
            );
        }
        if (scenarioId.startsWith("generated-order-event-backlog-")) {
            return new FamilyDiagnosis(
                    "ORDER_EVENT_CONSUMER_BACKLOG",
                    "ORDER_EVENT_CONSUMER",
                    "ORDER_CONSUMER_CONFIG_CHANGE",
                    "ORDER_PROCESSING_DELAYS",
                    "ORDER_CONSUMER_LAG"
            );
        }
        if (scenarioId.startsWith("generated-order-idempotency-failure-")) {
            return new FamilyDiagnosis(
                    "ORDER_IDEMPOTENCY_FAILURE",
                    "ORDER_SERVICE",
                    "ORDER_IDEMPOTENCY_STORAGE_CHANGE",
                    "DUPLICATE_ORDERS",
                    "DUPLICATE_ORDER_CREATION"
            );
        }
        throw new AssertionError("Unexpected generated family " + scenarioId);
    }

    private String failureQuery(Scenario scenario) {
        String scenarioId = scenario.scenarioId();
        if (scenarioId.contains("catalog-cache")) {
            return "stale";
        }
        if (scenarioId.contains("order-event-backlog")) {
            return "backlog";
        }
        if (scenarioId.contains("order-idempotency")) {
            return "duplicate";
        }
        return "timeout";
    }

    private String runbookQuery(Scenario scenario) {
        String scenarioId = scenario.scenarioId();
        if (scenarioId.contains("catalog-cache")) {
            return "catalog cache invalidation";
        }
        if (scenarioId.contains("order-event-backlog")) {
            return "order event backlog";
        }
        if (scenarioId.contains("order-idempotency")) {
            return "duplicate order idempotency";
        }
        return "payment timeout";
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

    private String request(
            long seed,
            String incidentFamily,
            boolean confirmLiveAi
    ) {
        return """
                {
                  "seed": %d,
                  "incident_family": "%s",
                  "evidence_mode": "diagnostic",
                  "noise_level": "none",
                  "confirm_live_ai": %s
                }
                """.formatted(seed, incidentFamily, confirmLiveAi);
    }

    private record FamilyDiagnosis(
            String rootCause,
            String affectedService,
            String trigger,
            String impact,
            String symptom
    ) {
    }
}
