package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.alarm.GeneratedIncidentAlarmEvaluator;
import dev.shirwac.incidentdetective.alarm.SignalAlarmReceipt;
import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseReceipt;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedCaseSeed;
import dev.shirwac.incidentdetective.generated.GeneratedCaseVariant;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.NordlyIncidentGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveInvestigationException;
import dev.shirwac.incidentdetective.live.LiveInvestigationFailure;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.planning.IncidentBlastRadius;
import dev.shirwac.incidentdetective.planning.IncidentPlan;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposal;
import dev.shirwac.incidentdetective.planning.IncidentPlanProposalStatus;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidationResult;
import dev.shirwac.incidentdetective.planning.IncidentPlanValidator;
import dev.shirwac.incidentdetective.planning.IncidentPlannerException;
import dev.shirwac.incidentdetective.planning.IncidentPlannerFailure;
import dev.shirwac.incidentdetective.planning.IncidentPlannerReceipt;
import dev.shirwac.incidentdetective.planning.IncidentService;
import dev.shirwac.incidentdetective.planning.IncidentSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentLabController.class)
@ActiveProfiles("rag")
@EnableConfigurationProperties(ApiCorsProperties.class)
class IncidentLabApiTest {

    private static final String PLANS = "/api/v1/incident-lab/plans";
    private static final String RUNS = "/api/v1/incident-lab/runs";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentLabService service;

    @BeforeEach
    void resetService() {
        reset(service);
    }

    @Test
    void planEndpointReturnsProposalJavaValidationAndProviderReceipt()
            throws Exception {
        IncidentPlanProposal proposal = proposal();
        IncidentPlanValidationResult validation = new IncidentPlanValidator()
                .validate(proposal);
        when(service.createPlan(any())).thenReturn(new IncidentLabPlanResponse(
                IncidentLabPlanResponse.CONTRACT_VERSION,
                "plan_ready",
                IncidentLabPlanResponse.DELIVERY,
                IncidentLabPlanResponse.TRUTH_LABEL,
                new IncidentLabPlanResponse.SafetyReceipt(
                        "allowed",
                        "none",
                        "Java granskar förslaget.",
                        "Java validates the proposal."
                ),
                proposal,
                validation,
                new IncidentPlannerReceipt(
                        "developer_api",
                        "gemini-3.1-flash-lite",
                        "provider-1",
                        null,
                        20
                ),
                List.of("No fallback plan was created.")
        ));

        mockMvc.perform(post(PLANS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instruction": "Simulera betalningstimeout",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("incident-lab-plan-v1"))
                .andExpect(jsonPath("$.outcome").value("plan_ready"))
                .andExpect(jsonPath("$.delivery")
                        .value("synchronous_post_run"))
                .andExpect(jsonPath("$.proposal.incident_family")
                        .value("payment_timeout"))
                .andExpect(jsonPath("$.java_validation.decision")
                        .value("APPROVED"))
                .andExpect(jsonPath("$.provider_receipt.transport")
                        .value("developer_api"));
    }

    @Test
    void planEndpointReturnsStableConfirmationProblem() throws Exception {
        when(service.createPlan(any())).thenThrow(
                new LiveInvestigationException(
                        LiveInvestigationFailure.CONFIRMATION_REQUIRED,
                        "Live AI request was not explicitly confirmed"
                )
        );

        mockMvc.perform(post(PLANS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instruction": "Simulera betalningstimeout i den syntetiska Nordly-miljön.",
                                  "confirm_live_ai": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("LIVE_AI_CONFIRMATION_REQUIRED"));
    }

    @Test
    void runEndpointRejectsMissingPlanBeforeCallingService() throws Exception {
        mockMvc.perform(post(RUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "seed": 42,
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void changedRunPlanReturnsAStableBadRequest() throws Exception {
        when(service.run(any())).thenThrow(
                new InvalidIncidentLabPlanException()
        );

        mockMvc.perform(post(RUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Incident plan rejected"))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST_BODY"));
    }

    @Test
    void invalidAdkControlReceiptReturnsStableWithheldProblem() throws Exception {
        when(service.run(any())).thenThrow(
                new InvalidAdkControlReceiptException()
        );

        mockMvc.perform(post(RUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runRequestJson()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("ADK result withheld"))
                .andExpect(jsonPath("$.detail").value(
                        "The investigation did not provide a valid read-only control receipt. No diagnosis was returned."
                ))
                .andExpect(jsonPath("$.code")
                        .value("ADK_CONTROL_RECEIPT_INVALID"));
    }

    @Test
    void runEndpointSerializesTheDualResponseAndGenericReceipts()
            throws Exception {
        IncidentLabRunResponse response = diagnosedRunResponse();
        when(service.run(any())).thenReturn(response);

        mockMvc.perform(post(RUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogRunRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version")
                        .value("incident-lab-run-v3"))
                .andExpect(jsonPath("$.answer_state").value("diagnosed"))
                .andExpect(jsonPath("$.business_response.headline")
                        .value("Starkaste förklaringen i det syntetiska fallet"))
                .andExpect(jsonPath("$.developer_response.root_cause_code")
                        .value("CATALOG_CACHE_INVALIDATION_FAILURE"))
                .andExpect(jsonPath("$.action_receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.action_receipt.action_executed")
                        .value(false))
                .andExpect(jsonPath("$.action_receipt.human_approval_required")
                        .value(true))
                .andExpect(jsonPath(
                        "$.localized_presentations.sv.business_response.headline"
                ).value("Starkaste förklaringen i det syntetiska fallet"))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.business_response.headline"
                ).value("Strongest explanation in the synthetic case"))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.developer_response.root_cause_code"
                ).value("CATALOG_CACHE_INVALIDATION_FAILURE"))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.action_receipt.read_operations"
                ).value(1))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.action_receipt.action_executed"
                ).value(false))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.action_receipt.human_approval_required"
                ).value(true))
                .andExpect(jsonPath("$.generation_receipt.generator_version")
                        .value(GeneratedCaseFactory.GENERATOR_VERSION))
                .andExpect(jsonPath("$.generation_receipt.variant.variant_id")
                        .isNotEmpty())
                .andExpect(jsonPath("$.alarm_receipt.incident_family")
                        .value("catalog_cache_invalidation"))
                .andExpect(jsonPath("$.alarm_receipt.signal.name")
                        .value("catalog_version_divergence_count"))
                .andExpect(jsonPath("$.alarm_receipt.signal.threshold_value")
                        .value(1.0))
                .andExpect(jsonPath("$.agent_turn.diagnosis")
                        .value(nullValue()))
                .andExpect(jsonPath("$.agent_turn.comparison")
                        .value(nullValue()));
    }

    @Test
    void noAlarmRunSerializesNullAlarmAndAgentReceipts() throws Exception {
        IncidentLabRunResponse response = noAlarmRunResponse();
        when(service.run(any())).thenReturn(response);

        mockMvc.perform(post(RUNS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(catalogRunRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer_state").value("not_started"))
                .andExpect(jsonPath("$.alarm_receipt").value(nullValue()))
                .andExpect(jsonPath("$.agent_turn").value(nullValue()))
                .andExpect(jsonPath("$.action_receipt.action_executed")
                        .value(false))
                .andExpect(jsonPath("$.action_receipt.human_approval_required")
                        .value(true))
                .andExpect(jsonPath(
                        "$.localized_presentations.sv.business_response.headline"
                ).value("Ingen utredning startades"))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.business_response.headline"
                ).value("No investigation was started"))
                .andExpect(jsonPath(
                        "$.localized_presentations.en.action_receipt.read_operations"
                ).value(0));
    }

    @ParameterizedTest
    @MethodSource("plannerFailures")
    void plannerFailuresBecomeDocumentedProblemDetails(
            IncidentPlannerFailure failure,
            int expectedStatus,
            ApiProblemResponse.Code expectedCode
    ) throws Exception {
        when(service.createPlan(any())).thenThrow(
                new IncidentPlannerException(failure, "internal provider detail")
        );

        mockMvc.perform(post(PLANS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "instruction": "Simulera betalningstimeout",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode.name()));
    }

    private static Stream<Arguments> plannerFailures() {
        return Stream.of(
                Arguments.of(
                        IncidentPlannerFailure.DISABLED,
                        503,
                        ApiProblemResponse.Code.LIVE_AI_DISABLED
                ),
                Arguments.of(
                        IncidentPlannerFailure.NOT_CONFIGURED,
                        503,
                        ApiProblemResponse.Code.LIVE_AI_NOT_CONFIGURED
                ),
                Arguments.of(
                        IncidentPlannerFailure.TIMEOUT,
                        504,
                        ApiProblemResponse.Code.MODEL_PROVIDER_TIMEOUT
                ),
                Arguments.of(
                        IncidentPlannerFailure.RATE_LIMITED,
                        429,
                        ApiProblemResponse.Code.MODEL_PROVIDER_RATE_LIMITED
                ),
                Arguments.of(
                        IncidentPlannerFailure.UPSTREAM,
                        502,
                        ApiProblemResponse.Code.MODEL_PROVIDER_ERROR
                ),
                Arguments.of(
                        IncidentPlannerFailure.MALFORMED_RESPONSE,
                        502,
                        ApiProblemResponse.Code.MALFORMED_MODEL_RESPONSE
                )
        );
    }

    private IncidentPlanProposal proposal() {
        return new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                "Synthetic payment timeout.",
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                List.of(IncidentService.PAYMENT_ADAPTER),
                IncidentBlastRadius.SINGLE_SERVICE
        );
    }

    private IncidentLabRunResponse diagnosedRunResponse() {
        GeneratedCaseRequest request = generatedRequest();
        GeneratedCase generated = generatedCase(request);
        SignalAlarmReceipt alarm = new GeneratedIncidentAlarmEvaluator()
                .evaluate(request.incidentFamily(), generated.investigationData())
                .orElseThrow();
        String evidenceId = alarm.evidenceIds().getFirst();
        Diagnosis diagnosis = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "CATALOG_SERVICE",
                "Verified generated diagnosis.",
                "Verified generated diagnosis.",
                List.of(new Claim(
                        ClaimCode.ROOT_CAUSE,
                        "CATALOG_CACHE_INVALIDATION_FAILURE",
                        "Verified root cause.",
                        List.of(evidenceId)
                )),
                new SafeNextStep("Review the cited configuration.", true)
        );
        AdkAgentTurnResponse rawAgentTurn = completedAgentTurn(
                generated.scenario(),
                diagnosis
        );
        IncidentLabResponsePresenter.Presentation presentation =
                new IncidentLabResponsePresenter().present(
                        generated.scenario(),
                        logs(generated),
                        alarm,
                        rawAgentTurn
                );
        return new IncidentLabRunResponse(
                IncidentLabRunResponse.CONTRACT_VERSION,
                "alarm_investigated",
                IncidentLabRunResponse.DELIVERY,
                IncidentLabRunResponse.TRUTH_LABEL,
                IncidentLabRunResponse.AnswerState.DIAGNOSED,
                presentation.businessResponse(),
                presentation.developerResponse(),
                presentation.actionReceipt(),
                presentation.localizedPresentations(),
                catalogPlan(),
                generationReceipt(generated, request),
                generated.scenario(),
                logs(generated),
                alarm,
                IncidentLabResponsePresenter.sanitizeAgentTurn(
                        rawAgentTurn,
                        IncidentLabRunResponse.AnswerState.DIAGNOSED
                ),
                List.of("Synthetic only.")
        );
    }

    private IncidentLabRunResponse noAlarmRunResponse() {
        GeneratedCaseRequest request = generatedRequest();
        GeneratedCase generated = generatedCase(request);
        IncidentLabResponsePresenter.Presentation presentation =
                new IncidentLabResponsePresenter().present(
                        generated.scenario(),
                        logs(generated),
                        null,
                        null
                );
        return new IncidentLabRunResponse(
                IncidentLabRunResponse.CONTRACT_VERSION,
                "no_alarm",
                IncidentLabRunResponse.DELIVERY,
                IncidentLabRunResponse.TRUTH_LABEL,
                IncidentLabRunResponse.AnswerState.NOT_STARTED,
                presentation.businessResponse(),
                presentation.developerResponse(),
                presentation.actionReceipt(),
                presentation.localizedPresentations(),
                catalogPlan(),
                generationReceipt(generated, request),
                generated.scenario(),
                logs(generated),
                null,
                null,
                List.of("Synthetic only.")
        );
    }

    private AdkAgentTurnResponse completedAgentTurn(
            Scenario scenario,
            Diagnosis diagnosis
    ) {
        return new AdkAgentTurnResponse(
                AdkAgentTurnResponse.CONTRACT_VERSION,
                "run-api-test",
                "session-api-test",
                "turn-api-test",
                AdkAgentTurnResponse.MODE,
                AdkAgentTurnResponse.TRUTH_LABEL,
                "completed",
                null,
                scenario,
                new AdkAgentTurnResponse.SafetyDecision(
                        "allowed",
                        "none",
                        "Tillåten.",
                        "Allowed."
                ),
                new AdkAgentTurnResponse.RuntimeProvenance(
                        "google-adk",
                        "test",
                        "nordly_investigation",
                        "gemini-test",
                        "test",
                        "in_memory",
                        "post_run",
                        "adk_events",
                        true,
                        false
                ),
                null,
                List.of(
                        new AdkAgentTurnResponse.RuntimeEvent(
                                1,
                                "event-call",
                                "invocation-api-test",
                                "nordly_evidence_agent",
                                "tool_call",
                                Instant.parse("2026-09-01T08:20:00Z"),
                                false,
                                false,
                                null,
                                List.of(new AdkAgentTurnResponse.FunctionCallEvent(
                                        "call-1",
                                        "inspect_incident_evidence",
                                        Map.of("log_query", "catalog")
                                )),
                                List.of(),
                                null,
                                "gemini-test"
                        ),
                        new AdkAgentTurnResponse.RuntimeEvent(
                                2,
                                "event-result",
                                "invocation-api-test",
                                "nordly_evidence_agent",
                                "tool_result",
                                Instant.parse("2026-09-01T08:20:01Z"),
                                false,
                                false,
                                null,
                                List.of(),
                                List.of(new AdkAgentTurnResponse.FunctionResponseEvent(
                                        "call-1",
                                        "inspect_incident_evidence",
                                        Map.of(
                                                "status", "found",
                                                "safe_summary",
                                                "Returned bounded synthetic evidence.",
                                                "scenario_id",
                                                scenario.scenarioId(),
                                                "evidence_ids", List.of(),
                                                "source_refs", List.of(),
                                                "write_capability", false,
                                                "action_executed", false
                                        )
                                )),
                                null,
                                null
                        ),
                        new AdkAgentTurnResponse.RuntimeEvent(
                                3,
                                "event-final",
                                "invocation-api-test",
                                "nordly_diagnosis_agent",
                                "final_response",
                                Instant.parse("2026-09-01T08:20:02Z"),
                                true,
                                true,
                                null,
                                List.of(),
                                List.of(),
                                null,
                                "gemini-test"
                        )
                ),
                List.of(new LiveToolEvent(
                        "read-log-1",
                        1,
                        ToolName.SEARCH_LOGS,
                        Map.of("query", "catalog"),
                        "Returned request-local synthetic logs.",
                        List.of(),
                        null
                )),
                null,
                diagnosis,
                null,
                null,
                new AdkAgentTurnResponse.VerificationEvent(
                        "deterministic_java_verifier",
                        Instant.parse("2026-09-01T08:20:00Z"),
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        "Released."
                ),
                new AdkAgentTurnResponse.ControlReceipt(
                        2,
                        1,
                        1,
                        0,
                        false,
                        false,
                        true,
                        List.of("inspect_incident_evidence"),
                        null,
                        null,
                        "test",
                        10
                ),
                List.of("Synthetic only.")
        );
    }

    private GeneratedCaseRequest generatedRequest() {
        return new GeneratedCaseRequest(
                71L,
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        );
    }

    private GeneratedCase generatedCase(GeneratedCaseRequest request) {
        return new NordlyIncidentGeneratedCaseGenerator().generate(request);
    }

    private GeneratedCaseReceipt generationReceipt(
            GeneratedCase generated,
            GeneratedCaseRequest request
    ) {
        String scenarioId = generated.scenario().scenarioId();
        return new GeneratedCaseReceipt(
                GeneratedCaseFactory.GENERATOR_VERSION,
                request.seed(),
                GeneratedCaseSeed.Origin.EXPLICIT,
                request.incidentFamily(),
                request.evidenceMode(),
                request.noiseLevel(),
                scenarioId,
                GeneratedCaseVariant.from(
                        GeneratedCaseFactory.GENERATOR_VERSION,
                        request,
                        scenarioId
                )
        );
    }

    private List<LogEvidence> logs(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(LogEvidence.class::isInstance)
                .map(LogEvidence.class::cast)
                .toList();
    }

    private IncidentPlan catalogPlan() {
        return new IncidentPlanValidator().validate(new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                "Synthetic catalog incident.",
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                IncidentSeverity.HIGH,
                List.of(IncidentService.CATALOG_SERVICE),
                IncidentBlastRadius.SINGLE_SERVICE
        )).plan();
    }

    private String catalogRunRequestJson() {
        IncidentPlan plan = catalogPlan();
        return """
                {
                  "plan": {
                    "contract_version": "%s",
                    "incident_family": "catalog_cache_invalidation",
                    "severity": "high",
                    "affected_services": ["catalog_service"],
                    "summary": "%s",
                    "synthetic_only": true,
                    "write_actions_allowed": false,
                    "human_approval_required": true
                  },
                  "seed": 71,
                  "evidence_mode": "diagnostic",
                  "confirm_live_ai": true
                }
                """.formatted(plan.contractVersion(), plan.summary());
    }

    private String runRequestJson() {
        IncidentPlan plan = new IncidentPlanValidator()
                .validate(proposal())
                .plan();
        return """
                {
                  "plan": {
                    "contract_version": "%s",
                    "incident_family": "payment_timeout",
                    "severity": "high",
                    "affected_services": ["payment_adapter"],
                    "summary": "%s",
                    "synthetic_only": true,
                    "write_actions_allowed": false,
                    "human_approval_required": true
                  },
                  "seed": 42,
                  "confirm_live_ai": true
                }
                """.formatted(plan.contractVersion(), plan.summary());
    }
}
