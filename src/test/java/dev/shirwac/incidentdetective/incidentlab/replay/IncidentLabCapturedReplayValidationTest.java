package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabPlanResponse;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class IncidentLabCapturedReplayValidationTest {

    private static final String RESOURCE =
            "incident-lab/replays/test-golden-v1.json";

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void acceptsASanitizedCapturedPublicApiFixture() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> {
        });

        assertDoesNotThrow(
                () -> IncidentLabReplayService.validateFixture(fixture)
        );
    }

    @Test
    void rejectsACapturedFixtureWithATestTruthLabel() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> plan(root)
                .put("truth_label", "Test-only fixture"));

        assertInvalid(fixture, "captured plan truth label");
    }

    @Test
    void rejectsACapturedFixtureThatDoesNotIdentifyALiveAdkTurn()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> agent(root)
                .put("mode", "recorded_replay"));

        assertInvalid(fixture, "captured agent mode");
    }

    @Test
    void rejectsMismatchedPlannerAndAgentProviderRoutes() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> {
            ObjectNode providerRoute = agent(root)
                    .get("provider_route")
                    .asObject();
            providerRoute.put("transport", "vertex_ai");
            providerRoute.put("authentication_mode", "adc");
        });

        assertInvalid(
                fixture,
                "captured planner and agent provider transport"
        );
    }

    @Test
    void rejectsACapturedFixtureWithoutReportedAgentTokens()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> agent(root)
                .get("receipt")
                .asObject()
                .putNull("token_usage"));

        assertInvalid(fixture, "captured agent token usage");
    }

    @Test
    void rejectsACapturedFixtureWithoutReportedPlannerTokens()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> plan(root)
                .get("provider_receipt")
                .asObject()
                .putNull("token_usage"));

        assertInvalid(fixture, "captured planner token usage");
    }

    @Test
    void rejectsACapturedFixtureContainingRawModelText() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> agent(root)
                .get("events")
                .get(2)
                .asObject()
                .put("text", "raw model response"));

        assertInvalid(fixture, "must not contain model text");
    }

    @Test
    void rejectsACapturedFixtureThatRegistersAnotherTool()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> agent(root)
                .get("receipt")
                .asObject()
                .withArrayProperty("registered_tools")
                .add("run_shell"));

        assertInvalid(fixture, "bounded read-only tool");
    }

    @Test
    void rejectsATestContractFixtureOutsideTheTestClasspathLoadPath()
            throws Exception {
        IncidentLabReplayFixture fixture = jsonMapper.readValue(
                new ClassPathResource(RESOURCE).getInputStream(),
                IncidentLabReplayFixture.class
        );

        assertInvalid(fixture, "actual test classpath");
    }

    @Test
    void rejectsAnUnexpectedFunctionName() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                functionCall(root).put("name", "run_shell")
        );

        assertInvalid(fixture, "captured function-call name");
    }

    @Test
    void rejectsAMismatchedFunctionResponseId() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                functionResponse(root).put("id", "another-call")
        );

        assertInvalid(fixture, "function call/response ID");
    }

    @Test
    void rejectsSensitiveFunctionArguments() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                functionArguments(root).put("prompt", "hidden prompt")
        );

        assertInvalid(fixture, "sensitive field name");
    }

    @Test
    void rejectsNestedFunctionArguments() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                functionArguments(root)
                        .putObject("log_query")
                        .put("raw", "catalog")
        );

        assertInvalid(fixture, "bounded, non-sensitive plain text");
    }

    @Test
    void rejectsRawFunctionResponseMaps() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                functionResponse(root)
                        .get("response")
                        .asObject()
                        .putObject("operations")
                        .put("command", "inspect")
        );

        assertInvalid(fixture, "exact public allowlist");
    }

    @Test
    void rejectsSensitiveLiveToolArguments() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                toolArguments(root, 1).put("api_key", "not-public")
        );

        assertInvalid(fixture, "sensitive field name");
    }

    @Test
    void rejectsNestedLiveToolArguments() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root ->
                toolArguments(root, 2)
                        .putObject("query")
                        .put("raw", "cache")
        );

        assertInvalid(fixture, "bounded, non-sensitive plain text");
    }

    @Test
    void rejectsADiagnosedAnswerWhenAnyVerificationFlagFails()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> agent(root)
                .get("verification_event")
                .asObject()
                .put("tool_boundary_valid", false));

        assertInvalid(fixture, "every verification flag");
    }

    @Test
    void rejectsADiagnosedAnswerWithHardVerificationErrors()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> {
            ObjectNode verification = agent(root)
                    .get("verification")
                    .asObject();
            verification.put("diagnosis_schema_pass", false);
            verification.withArrayProperty("hard_errors")
                    .add("diagnosis_schema_invalid");
        });

        assertInvalid(fixture, "error-free verification report");
    }

    @Test
    void rejectsInsufficientEvidenceThatPublishesADiagnosis()
            throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> run(root)
                .put("answer_state", "insufficient_evidence"));

        assertInvalid(fixture, "diagnosis correctness");
    }

    @Test
    void rejectsAWithheldAnswerThatWasReleased() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> run(root)
                .put("answer_state", "withheld"));

        assertInvalid(fixture, "failed, unreleased verification");
    }

    @Test
    void rejectsNotStartedWhenAnAgentIsPresent() throws Exception {
        IncidentLabReplayFixture fixture = capturedFixture(root -> run(root)
                .put("answer_state", "not_started"));

        assertInvalid(fixture, "must not contain an agent");
    }

    private IncidentLabReplayFixture capturedFixture(
            Consumer<ObjectNode> mutation
    ) throws IOException {
        ObjectNode root = jsonMapper.readTree(
                new ClassPathResource(RESOURCE).getInputStream()
        ).asObject();
        root.put(
                "recording_source",
                IncidentLabReplayFixture.CAPTURED_PUBLIC_API
        );
        root.put("source_build_git_sha", "a".repeat(40));

        ObjectNode plan = plan(root);
        plan.put("delivery", IncidentLabPlanResponse.DELIVERY);
        plan.put("truth_label", IncidentLabPlanResponse.TRUTH_LABEL);
        ObjectNode plannerReceipt = plan.putObject("provider_receipt");
        plannerReceipt.put("transport", "developer_api");
        plannerReceipt.put("model", "gemini-3.1-flash-lite");
        plannerReceipt.put("provider_response_id", "provider-plan-1");
        plannerReceipt.put("latency_ms", 20);
        reportedUsage(plannerReceipt.putObject("token_usage"), 12);

        ObjectNode run = run(root);
        run.put("delivery", IncidentLabRunResponse.DELIVERY);
        run.put("truth_label", IncidentLabRunResponse.TRUTH_LABEL);

        ObjectNode agent = agent(root);
        agent.put("contract_version", AdkAgentTurnResponse.CONTRACT_VERSION);
        agent.put("mode", AdkAgentTurnResponse.MODE);
        agent.put("truth_label", AdkAgentTurnResponse.TRUTH_LABEL);
        ObjectNode providerRoute = agent.putObject("provider_route");
        providerRoute.put("transport", "developer_api");
        providerRoute.put("authentication_mode", "api_key");
        providerRoute.putNull("location");

        ObjectNode runtime = agent.get("runtime").asObject();
        runtime.put("framework", "google_adk");
        runtime.put("framework_version", "1.7.0");
        runtime.put("model_id", "gemini-3.1-flash-lite");
        runtime.put("prompt_version", "nordly-adk-sequential-v3");
        runtime.put("delivery", IncidentLabRunResponse.DELIVERY);
        runtime.put("event_source", "google_adk_runner");

        ObjectNode agentReceipt = agent.get("receipt").asObject();
        reportedUsage(agentReceipt.putObject("token_usage"), 24);

        ObjectNode functionArguments = functionArguments(root);
        functionArguments.put("log_query", "catalog");
        functionArguments.put("runbook_query", "cache invalidation");
        functionArguments.put("diagnostic_probe", "service_health");

        ObjectNode functionResponse = functionResponse(root)
                .get("response")
                .asObject();
        functionResponse.put("safe_summary", "Bounded evidence found.");
        functionResponse.put(
                "scenario_id",
                run.get("scenario").get("scenario_id").asText()
        );
        functionResponse.withArrayProperty("evidence_ids")
                .add("test-log-catalog-cache");
        functionResponse.withArrayProperty("source_refs")
                .add("test/logs/catalog-cache");
        functionResponse.put("write_capability", false);
        functionResponse.put("action_executed", false);

        ObjectNode metricArguments = toolArguments(root, 0);
        metricArguments.withArrayProperty("metric_names")
                .add("catalog_version_divergence_count");
        metricArguments.put("start", "2026-09-12T03:10:00Z");
        metricArguments.put("end", "2026-09-12T03:12:00Z");

        ObjectNode logArguments = toolArguments(root, 1);
        logArguments.withArrayProperty("services").add("CATALOG_SERVICE");
        logArguments.withArrayProperty("levels").add("ERROR");
        logArguments.put("query", "catalog");
        logArguments.put("start", "2026-09-12T03:10:00Z");
        logArguments.put("end", "2026-09-12T03:12:00Z");

        ObjectNode runbookArguments = toolArguments(root, 2);
        runbookArguments.put("max_results", 2);

        mutation.accept(root);
        return jsonMapper.treeToValue(root, IncidentLabReplayFixture.class);
    }

    private void reportedUsage(ObjectNode usage, int total) {
        usage.put("input_tokens", total - 4);
        usage.put("candidate_output_tokens", 4);
        usage.put("output_tokens", 4);
        usage.put("total_tokens", total);
    }

    private ObjectNode plan(ObjectNode root) {
        return root.get("recorded_plan").asObject();
    }

    private ObjectNode run(ObjectNode root) {
        return root.get("recorded_run").asObject();
    }

    private ObjectNode agent(ObjectNode root) {
        return run(root).get("agent_turn").asObject();
    }

    private ObjectNode functionCall(ObjectNode root) {
        return agent(root)
                .get("events")
                .get(0)
                .get("function_calls")
                .get(0)
                .asObject();
    }

    private ObjectNode functionArguments(ObjectNode root) {
        return functionCall(root).get("arguments").asObject();
    }

    private ObjectNode functionResponse(ObjectNode root) {
        return agent(root)
                .get("events")
                .get(1)
                .get("function_responses")
                .get(0)
                .asObject();
    }

    private ObjectNode toolArguments(ObjectNode root, int index) {
        return agent(root)
                .get("tool_events")
                .get(index)
                .get("arguments")
                .asObject();
    }

    private void assertInvalid(
            IncidentLabReplayFixture fixture,
            String expectedMessage
    ) {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IncidentLabReplayService.validateFixture(fixture)
        );
        assertTrue(failure.getMessage().contains(expectedMessage));
    }
}
