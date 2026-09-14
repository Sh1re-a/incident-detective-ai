package dev.shirwac.incidentdetective.incidentlab;

import dev.shirwac.incidentdetective.api.ApiProblemResponse;
import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
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

import java.util.List;
import java.util.stream.Stream;

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
