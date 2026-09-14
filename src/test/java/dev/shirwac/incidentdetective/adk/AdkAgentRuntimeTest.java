package dev.shirwac.incidentdetective.adk;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionCallingConfigMode;
import com.google.genai.types.Part;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeId;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeOutcome;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeReceipt;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeRequest;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeService;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.tools.InvestigationToolExecutor;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdkAgentRuntimeTest {

    private static final String DIAGNOSIS_JSON = """
            {"status":"insufficient_evidence","root_cause_code":null,"affected_service":null,"business_summary":"Not enough evidence.","technical_summary":"The bounded evidence did not prove a root cause.","claims":[],"safe_next_step":{"summary":"Ask a human to review the evidence.","requires_human_approval":true}}
            """.strip();

    @Test
    void runsTwoChildrenInOrderWithOneRawToolHandoffAndTwoModelCalls()
            throws Exception {
        GeneratedCase generated = generatedCase();
        InvestigationToolExecutor tools = boundedTools(generated);
        AdkAgentRuntime runtime = runtime(tools);
        SequencedFakeLlm model = new SequencedFakeLlm();

        AdkAgentRuntime.RunResult result = runtime.run(
                generated,
                "Undersök larmet med endast read-only verktyg.",
                model
        );

        assertEquals(2, result.modelCallCount());
        assertEquals(1, result.toolInvocationCount());
        assertEquals(
                DiagnosticProbeId.SERVICE_HEALTH,
                result.diagnosticProbeReceipt().probeId()
        );
        assertEquals(2, model.requests().size());
        assertEquals(
                List.of(
                        AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                        AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                        AdkAgentRuntime.DIAGNOSIS_AGENT_NAME
                ),
                result.events().stream().map(Event::author).toList()
        );
        assertEquals(DIAGNOSIS_JSON, runtime.finalText(result.events()));

        AdkAgentRuntime.TrajectoryValidation trajectory =
                runtime.validateTrajectory(result.events());
        assertTrue(trajectory.valid());
        assertTrue(trajectory.completedInOrder());
        assertEquals("sequential_agent", trajectory.workflowType());
        assertEquals(
                List.of(
                        AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                        AdkAgentRuntime.DIAGNOSIS_AGENT_NAME
                ),
                trajectory.observedAgentOrder()
        );
        assertEquals("adk_function_response", trajectory.evidenceHandoff());
        assertEquals(
                AdkAgentRuntime.DIAGNOSIS_AGENT_NAME,
                trajectory.finalResponseAuthor()
        );
        assertTrue(trajectory.violations().isEmpty());

        LlmRequest evidenceRequest = model.requests().getFirst();
        assertTrue(evidenceRequest.config()
                .flatMap(config -> config.responseMimeType())
                .isEmpty());
        assertTrue(evidenceRequest.config()
                .flatMap(config -> config.responseJsonSchema())
                .isEmpty());
        assertEquals(
                List.of(AdkAgentRuntime.TOOL_NAME),
                evidenceRequest.tools().keySet().stream().sorted().toList()
        );
        assertEquals(
                FunctionCallingConfigMode.Known.ANY,
                evidenceRequest.config()
                        .flatMap(config -> config.toolConfig())
                        .flatMap(config -> config.functionCallingConfig())
                        .flatMap(config -> config.mode())
                        .orElseThrow()
                        .knownEnum()
        );
        assertEquals(
                List.of(AdkAgentRuntime.TOOL_NAME),
                evidenceRequest.config()
                        .flatMap(config -> config.toolConfig())
                        .flatMap(config -> config.functionCallingConfig())
                        .flatMap(config -> config.allowedFunctionNames())
                        .orElseThrow()
        );

        LlmRequest diagnosisRequest = model.requests().getLast();
        assertTrue(diagnosisRequest.tools().isEmpty());
        assertEquals(
                "application/json",
                diagnosisRequest.config()
                        .flatMap(config -> config.responseMimeType())
                        .orElseThrow()
        );
        Map<String, Object> expectedSchema = JsonMapper.builder()
                .build()
                .readValue(
                        new ClassPathResource(
                                GeminiPromptContracts.DIAGNOSIS_SCHEMA_RESOURCE
                        )
                                .getContentAsString(StandardCharsets.UTF_8),
                        new TypeReference<LinkedHashMap<String, Object>>() {
                        }
                );
        assertEquals(
                expectedSchema,
                diagnosisRequest.config()
                        .flatMap(config -> config.responseJsonSchema())
                        .orElseThrow()
        );
        String returnedEvidenceId = result.toolExecutions().stream()
                .flatMap(execution -> execution.evidence().stream())
                .map(Evidence::evidenceId)
                .findFirst()
                .orElseThrow();
        assertTrue(requestText(diagnosisRequest).contains(returnedEvidenceId));
        String diagnosisInstructions = String.join(
                "\n",
                diagnosisRequest.getSystemInstructions()
        );
        assertTrue(diagnosisInstructions
                .contains("Decide the status before creating claims"));
        assertFalse(diagnosisInstructions.contains("ground_truth"));
    }

    @Test
    void validationRejectsReorderedEventsAndAgentTransfer() {
        GeneratedCase generated = generatedCase();
        AdkAgentRuntime runtime = runtime(boundedTools(generated));
        AdkAgentRuntime.RunResult result = runtime.run(
                generated,
                "Inspect the alert using read-only evidence.",
                new SequencedFakeLlm()
        );
        List<Event> events = result.events();
        Event transferredCall = events.getFirst().toBuilder()
                .actions(events.getFirst().actions().toBuilder()
                        .transferToAgent(AdkAgentRuntime.DIAGNOSIS_AGENT_NAME)
                        .build())
                .build();
        List<Event> invalid = List.of(
                events.getLast(),
                transferredCall,
                events.get(1)
        );

        AdkAgentRuntime.TrajectoryValidation trajectory =
                runtime.validateTrajectory(invalid);

        assertFalse(trajectory.valid());
        assertFalse(trajectory.completedInOrder());
        assertFalse(trajectory.agentSequenceValid());
        assertFalse(trajectory.finalAuthorValid());
        assertFalse(trajectory.transferBoundaryValid());
        assertTrue(trajectory.violations().contains("unexpected_agent_sequence"));
        assertTrue(trajectory.violations().contains("agent_transfer_observed"));
    }

    @Test
    void finalTextNeverUsesEvidenceAgentNarrative() {
        AdkAgentRuntime runtime = runtime(mock(InvestigationToolExecutor.class));
        Event evidenceNarrative = textEvent(
                AdkAgentRuntime.EVIDENCE_AGENT_NAME,
                "This text must never become the diagnosis."
        );
        Event diagnosis = textEvent(
                AdkAgentRuntime.DIAGNOSIS_AGENT_NAME,
                DIAGNOSIS_JSON
        );

        assertEquals(
                DIAGNOSIS_JSON,
                runtime.finalText(List.of(diagnosis, evidenceNarrative))
        );
        assertEquals("", runtime.finalText(List.of(evidenceNarrative)));
    }

    @Test
    void projectedFinalResponseWithholdsModelTextUnlessReleased() {
        AdkAgentRuntime runtime = runtime(mock(InvestigationToolExecutor.class));
        String raw = "DO-NOT-RELEASE-model-output";
        Event diagnosis = textEvent(AdkAgentRuntime.DIAGNOSIS_AGENT_NAME, raw);

        AdkAgentTurnResponse.RuntimeEvent withheld =
                runtime.projectEvents(List.of(diagnosis), false).getFirst();
        assertTrue(withheld.finalResponse());
        assertTrue(withheld.contentWithheld());
        assertNull(withheld.text());

        AdkAgentTurnResponse.RuntimeEvent released =
                runtime.projectEvents(List.of(diagnosis), true).getFirst();
        assertFalse(released.contentWithheld());
        assertEquals(raw, released.text());
    }

    @Test
    void rejectsFreeFormTerminalLikeProbeBeforeAnyDiagnosticRuns() {
        GeneratedCase generated = generatedCase();
        DiagnosticProbeService probes = mock(DiagnosticProbeService.class);
        AdkAgentRuntime.ReadOnlyIncidentTool tool =
                new AdkAgentRuntime.ReadOnlyIncidentTool(
                        generated.investigationData(),
                        boundedTools(generated),
                        probes
                );

        Map<String, Object> response = tool.inspectIncidentEvidence(
                "PAYMENT_ADAPTER",
                "payment timeout",
                "open_terminal"
        );

        assertEquals("invalid_arguments", response.get("status"));
        assertNull(tool.diagnosticProbeReceipt());
        verifyNoInteractions(probes);
    }

    private AdkAgentRuntime runtime(InvestigationToolExecutor tools) {
        return new AdkAgentRuntime(
                tools,
                diagnosticProbes(),
                JsonMapper.builder().build()
        );
    }

    private DiagnosticProbeService diagnosticProbes() {
        DiagnosticProbeService probes = mock(DiagnosticProbeService.class);
        when(probes.execute(
                any(InvestigationData.class),
                any(DiagnosticProbeRequest.class)
        )).thenAnswer(invocation -> {
            InvestigationData data = invocation.getArgument(0);
            DiagnosticProbeRequest request = invocation.getArgument(1);
            return new DiagnosticProbeReceipt(
                    data.scenario().scenarioId(),
                    request.probeId(),
                    DiagnosticProbeOutcome.NOT_AVAILABLE,
                    "No additional bounded findings were available.",
                    List.of(),
                    false
            );
        });
        return probes;
    }

    private GeneratedCase generatedCase() {
        return new PaymentTimeoutGeneratedCaseGenerator().generate(
                new GeneratedCaseRequest(
                        42,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        GeneratedNoiseLevel.LOW
                )
        );
    }

    private InvestigationToolExecutor boundedTools(GeneratedCase generated) {
        InvestigationToolExecutor tools = mock(InvestigationToolExecutor.class);
        when(tools.availableMetricNames(any(InvestigationData.class)))
                .thenReturn(generated.investigationData().evidenceInventory().stream()
                        .filter(MetricEvidence.class::isInstance)
                        .map(MetricEvidence.class::cast)
                        .map(metric -> metric.content().metricName())
                        .distinct()
                        .toList());
        when(tools.execute(
                any(InvestigationData.class),
                any(CollectionToolCall.class)
        )).thenAnswer(invocation -> {
            InvestigationData data = invocation.getArgument(0);
            CollectionToolCall call = invocation.getArgument(1);
            List<Evidence> evidence = data.evidenceInventory().stream()
                    .filter(item -> switch (call.toolName()) {
                        case GET_METRICS -> item instanceof MetricEvidence;
                        case SEARCH_LOGS -> item instanceof LogEvidence;
                        case GET_TRACE -> item instanceof TraceEvidence;
                        case RETRIEVE_RUNBOOKS -> item instanceof RunbookEvidence;
                    })
                    .toList();
            return new ToolExecution(
                    call.callId(),
                    call.toolName(),
                    call.arguments(),
                    "Returned bounded test evidence.",
                    evidence,
                    null
            );
        });
        return tools;
    }

    private String requestText(LlmRequest request) {
        return request.contents().stream()
                .flatMap(content -> content.parts().orElse(List.of()).stream())
                .flatMap(part -> part.text().stream())
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private Event textEvent(String author, String text) {
        return Event.builder()
                .id("event-" + author)
                .invocationId("invocation-1")
                .author(author)
                .content(Content.fromParts(Part.fromText(text)))
                .build();
    }

    private static final class SequencedFakeLlm extends BaseLlm {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<LlmRequest> requests = new ArrayList<>();

        private SequencedFakeLlm() {
            super("gemini-test");
        }

        @Override
        public Flowable<LlmResponse> generateContent(
                LlmRequest request,
                boolean stream
        ) {
            requests.add(request);
            int call = calls.incrementAndGet();
            if (call == 1) {
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder()
                                        .functionCall(FunctionCall.builder()
                                                .id("inspect-call-1")
                                                .name(AdkAgentRuntime.TOOL_NAME)
                                                .args(Map.of(
                                                        "log_query", "PAYMENT_ADAPTER",
                                                        "runbook_query", "payment timeout",
                                                        "diagnostic_probe", "service_health"
                                                ))
                                                .build())
                                        .build())
                                .build())
                        .modelVersion("gemini-test-version")
                        .build());
            }
            if (call == 2) {
                return Flowable.just(LlmResponse.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.fromText(DIAGNOSIS_JSON))
                                .build())
                        .modelVersion("gemini-test-version")
                        .build());
            }
            return Flowable.error(new AssertionError(
                    "Sequential runtime exceeded its two-call provider budget"
            ));
        }

        @Override
        public BaseLlmConnection connect(LlmRequest request) {
            throw new UnsupportedOperationException("Live mode is not used in this test");
        }

        private List<LlmRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
