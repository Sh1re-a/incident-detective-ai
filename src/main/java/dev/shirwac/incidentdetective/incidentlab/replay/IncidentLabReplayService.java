package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabPlanResponse;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public final class IncidentLabReplayService {

    static final String NOT_CONFIGURED = "golden_recording_not_captured";
    private static final Set<String> RECORDING_SOURCES = Set.of(
            IncidentLabReplayFixture.CAPTURED_PUBLIC_API,
            IncidentLabReplayFixture.TEST_CONTRACT_FIXTURE
    );
    private static final String PUBLIC_FUNCTION_NAME =
            "inspect_incident_evidence";
    private static final Set<String> PUBLIC_FUNCTION_ARGUMENTS = Set.of(
            "log_query",
            "runbook_query",
            "diagnostic_probe"
    );
    private static final Set<String> PUBLIC_FUNCTION_RESPONSE_FIELDS = Set.of(
            "status",
            "safe_summary",
            "scenario_id",
            "evidence_ids",
            "source_refs",
            "write_capability",
            "action_executed"
    );
    private static final Set<String> PUBLIC_RESPONSE_STATUSES = Set.of(
            "found",
            "not_found",
            "invalid_arguments",
            "budget_exhausted"
    );
    private static final Set<String> PUBLIC_DIAGNOSTIC_PROBES = Set.of(
            "service_health",
            "dependency_status",
            "release_metadata",
            "config_fingerprint_diff"
    );
    private static final Pattern SENSITIVE_FIELD_NAME = Pattern.compile(
            "(?i).*(?:secret|token|password|key|prompt|credential|"
                    + "authorization|cookie).*"
    );
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(?:.*\\b(?:secret|token|password|api[-_ ]?key|prompt|"
                    + "credential|authorization)\\s*[:=].*|"
                    + ".*\\bbearer\\s+[a-z0-9._~+/=-]{8,}.*|"
                    + ".*\\bAIza[0-9a-z_-]{20,}\\b.*|"
                    + ".*\\bsk-[0-9a-z_-]{16,}\\b.*)"
    );
    private static final int MAX_PUBLIC_VALUE_LENGTH = 512;
    private static final List<String> LIMITATIONS = List.of(
            "The plan and investigation are a historical, versioned capture; they did not execute in this playback request.",
            "Historical provider, token, latency, retrieval, and cost fields describe the recording only.",
            "No provider, ADK runner, database, embedding, vector search, live quota, or remediation action is invoked by playback."
    );

    private final Clock clock;
    private final LoadedFixture loaded;

    public IncidentLabReplayService(
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${incident-detective.incident-lab-replay.resource:}")
            String configuredResource
    ) {
        this.clock = clock;
        String resource = configuredResource == null
                ? ""
                : configuredResource.strip();
        loaded = resource.isEmpty() ? null : load(jsonMapper, resource);
    }

    public IncidentLabReplayAvailabilityResponse availability() {
        return loaded == null
                ? IncidentLabReplayAvailabilityResponse.unavailable()
                : IncidentLabReplayAvailabilityResponse.ready();
    }

    public IncidentLabReplayResponse play() {
        if (loaded == null) {
            throw new IncidentLabReplayUnavailableException();
        }
        IncidentLabReplayFixture fixture = loaded.fixture();
        return new IncidentLabReplayResponse(
                IncidentLabReplayResponse.CONTRACT_VERSION,
                fixture.replayId(),
                UUID.randomUUID().toString(),
                RunMode.RECORDED_REPLAY,
                IncidentLabReplayResponse.DELIVERY,
                IncidentLabReplayResponse.TRUTH_LABEL,
                IncidentLabReplayResponse.TRUTH_LABEL_EN,
                clock.instant(),
                fixture.recordedInstruction(),
                fixture.recordedInstructionLocale(),
                fixture.recordedPlan(),
                fixture.recordedRun(),
                new IncidentLabReplayResponse.Provenance(
                        fixture.fixtureVersion(),
                        fixture.recordingSource(),
                        fixture.recordedAt(),
                        fixture.sourceBuildGitSha(),
                        loaded.resourceSha256(),
                        true,
                        fixture.recordedPlan().contractVersion(),
                        fixture.recordedRun().contractVersion()
                ),
                IncidentLabReplayResponse.PlaybackReceipt.noCurrentExecution(),
                LIMITATIONS
        );
    }

    private LoadedFixture load(JsonMapper jsonMapper, String resourcePath) {
        String normalized = resourcePath.startsWith("/")
                ? resourcePath.substring(1)
                : resourcePath;
        ClassPathResource fixtureResource = new ClassPathResource(normalized);
        ClassPathResource checksumResource = new ClassPathResource(
                normalized + ".sha256"
        );
        if (!fixtureResource.isReadable() || !checksumResource.isReadable()) {
            throw new IllegalStateException(
                    "Configured Incident Lab replay resource or checksum is not readable"
            );
        }

        try {
            byte[] bytes = fixtureResource.getContentAsByteArray();
            String expected = new String(
                    checksumResource.getContentAsByteArray(),
                    StandardCharsets.UTF_8
            ).strip();
            if (!expected.matches("[0-9a-f]{64}")) {
                throw new IllegalStateException(
                        "Incident Lab replay checksum must be lowercase SHA-256"
                );
            }
            String actual = sha256(bytes);
            if (!actual.equals(expected)) {
                throw new IllegalStateException(
                        "Incident Lab replay checksum does not match the configured fixture"
                );
            }
            IncidentLabReplayFixture fixture = jsonMapper.readValue(
                    bytes,
                    IncidentLabReplayFixture.class
            );
            validateFixture(
                    fixture,
                    isActualTestClasspathResource(fixtureResource)
            );
            return new LoadedFixture(fixture, actual);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read the configured Incident Lab replay fixture",
                    exception
            );
        }
    }

    private static boolean isActualTestClasspathResource(
            ClassPathResource resource
    ) throws IOException {
        String protocol = resource.getURL().getProtocol();
        if (!"file".equalsIgnoreCase(protocol)) {
            return false;
        }
        String location = resource.getURL().toExternalForm()
                .replace('\\', '/');
        return location.contains("/target/test-classes/")
                || location.contains("/build/resources/test/");
    }

    static void validateFixture(IncidentLabReplayFixture fixture) {
        validateFixture(fixture, false);
    }

    private static void validateFixture(
            IncidentLabReplayFixture fixture,
            boolean actualTestClasspathResource
    ) {
        Objects.requireNonNull(fixture, "fixture must not be null");
        requireEqual(
                IncidentLabReplayFixture.FIXTURE_VERSION,
                fixture.fixtureVersion(),
                "fixture version"
        );
        requireText(fixture.replayId(), "replay ID");
        requireText(fixture.recordingSource(), "recording source");
        if (!RECORDING_SOURCES.contains(fixture.recordingSource())) {
            throw invalid("unknown recording source");
        }
        if (IncidentLabReplayFixture.TEST_CONTRACT_FIXTURE.equals(
                fixture.recordingSource()
        ) && !actualTestClasspathResource) {
            throw invalid(
                    "test contract fixture is allowed only from the actual test classpath"
            );
        }
        Objects.requireNonNull(fixture.recordedAt(), "recordedAt must not be null");
        requireText(fixture.sourceBuildGitSha(), "source build git SHA");
        requireText(fixture.recordedInstruction(), "recorded instruction");
        if (!Set.of("sv", "en").contains(fixture.recordedInstructionLocale())) {
            throw invalid("recorded instruction locale must be sv or en");
        }

        IncidentLabPlanResponse plan = Objects.requireNonNull(
                fixture.recordedPlan(),
                "recordedPlan must not be null"
        );
        IncidentLabRunResponse run = Objects.requireNonNull(
                fixture.recordedRun(),
                "recordedRun must not be null"
        );
        requireEqual(
                IncidentLabPlanResponse.CONTRACT_VERSION,
                plan.contractVersion(),
                "recorded plan contract"
        );
        requireEqual(
                IncidentLabRunResponse.CONTRACT_VERSION,
                run.contractVersion(),
                "recorded run contract"
        );
        if (!"plan_ready".equals(plan.outcome())
                || plan.javaValidation() == null
                || !plan.javaValidation().accepted()
                || plan.javaValidation().plan() == null) {
            throw invalid("recorded plan must contain a Java-approved plan");
        }
        if (!plan.javaValidation().plan().equals(run.plan())) {
            throw invalid("recorded plan and run plan must match exactly");
        }
        validateRun(run);

        if (IncidentLabReplayFixture.CAPTURED_PUBLIC_API.equals(
                fixture.recordingSource()
        )) {
            validateCapturedPublicApi(fixture, plan, run);
        }
    }

    private static void validateCapturedPublicApi(
            IncidentLabReplayFixture fixture,
            IncidentLabPlanResponse plan,
            IncidentLabRunResponse run
    ) {
        if (!fixture.sourceBuildGitSha().matches("[0-9a-f]{40}")) {
            throw invalid("captured replay requires a full lowercase build SHA");
        }
        requireEqual(
                IncidentLabPlanResponse.DELIVERY,
                plan.delivery(),
                "captured plan delivery"
        );
        requireEqual(
                IncidentLabPlanResponse.TRUTH_LABEL,
                plan.truthLabel(),
                "captured plan truth label"
        );
        if (plan.providerReceipt() == null) {
            throw invalid("captured replay requires the original planner receipt");
        }
        requireReportedUsage(
                plan.providerReceipt().tokenUsage(),
                "captured planner token usage"
        );
        requireEqual(
                IncidentLabRunResponse.DELIVERY,
                run.delivery(),
                "captured run delivery"
        );
        requireEqual(
                IncidentLabRunResponse.TRUTH_LABEL,
                run.truthLabel(),
                "captured run truth label"
        );

        AdkAgentTurnResponse agent = Objects.requireNonNull(
                run.agentTurn(),
                "captured replay agent must not be null"
        );
        requireEqual(
                AdkAgentTurnResponse.CONTRACT_VERSION,
                agent.contractVersion(),
                "captured agent contract"
        );
        requireEqual(
                AdkAgentTurnResponse.MODE,
                agent.mode(),
                "captured agent mode"
        );
        requireEqual(
                AdkAgentTurnResponse.TRUTH_LABEL,
                agent.truthLabel(),
                "captured agent truth label"
        );
        if (agent.diagnosis() != null || agent.comparison() != null) {
            throw invalid(
                    "captured public agent must not contain diagnosis or ground-truth comparison"
            );
        }
        if (agent.events().stream()
                .filter(AdkAgentTurnResponse.RuntimeEvent::finalResponse)
                .noneMatch(AdkAgentTurnResponse.RuntimeEvent::contentWithheld)) {
            throw invalid("captured final model content must be withheld");
        }

        AdkAgentTurnResponse.RuntimeProvenance runtime =
                Objects.requireNonNull(
                        agent.runtime(),
                        "captured agent runtime must not be null"
                );
        requireEqual("google_adk", runtime.framework(),
                "captured agent framework");
        requireEqual("google_adk_runner", runtime.eventSource(),
                "captured agent event source");
        requireEqual(IncidentLabRunResponse.DELIVERY, runtime.delivery(),
                "captured agent delivery");
        requireText(runtime.frameworkVersion(),
                "captured agent framework version");
        requireText(runtime.modelId(), "captured agent model ID");
        requireText(runtime.promptVersion(), "captured agent prompt version");

        requireRecordedLiveProof(run);
        requireRecordedProviderRoute(plan, agent);
        requireReportedUsage(
                agent.receipt().tokenUsage(),
                "captured agent token usage"
        );
        if (!List.of("inspect_incident_evidence")
                .equals(agent.receipt().registeredTools())) {
            throw invalid(
                    "captured agent must expose only the bounded read-only tool"
            );
        }
        validateCapturedPublicToolPayloads(agent);
    }

    private static void requireRecordedProviderRoute(
            IncidentLabPlanResponse plan,
            AdkAgentTurnResponse agent
    ) {
        requireText(agent.providerRoute().transport(),
                "captured provider transport");
        requireText(agent.providerRoute().authenticationMode(),
                "captured provider authentication mode");
        requireEqual(
                plan.providerReceipt().transport(),
                agent.providerRoute().transport(),
                "captured planner and agent provider transport"
        );
        String expectedAuthentication = switch (
                agent.providerRoute().transport()
        ) {
            case "developer_api" -> "api_key";
            case "vertex_ai" -> "adc";
            default -> throw invalid("unknown captured provider transport");
        };
        requireEqual(
                expectedAuthentication,
                agent.providerRoute().authenticationMode(),
                "captured provider authentication mode"
        );
    }

    private static void requireReportedUsage(
            ModelTokenUsage usage,
            String field
    ) {
        if (usage == null
                || usage.totalTokens() == null
                || usage.totalTokens() <= 0) {
            throw invalid(field + " must report a positive total");
        }
    }

    private static void validateCapturedPublicToolPayloads(
            AdkAgentTurnResponse agent
    ) {
        List<CapturedFunctionCall> calls = new java.util.ArrayList<>();
        List<CapturedFunctionResponse> responses = new java.util.ArrayList<>();
        for (AdkAgentTurnResponse.RuntimeEvent event : agent.events()) {
            event.functionCalls().forEach(call -> calls.add(
                    new CapturedFunctionCall(event.sequence(), call)
            ));
            event.functionResponses().forEach(response -> responses.add(
                    new CapturedFunctionResponse(event.sequence(), response)
            ));
        }
        if (calls.size() != 1 || responses.size() != 1) {
            throw invalid(
                    "captured public agent requires exactly one bounded function call and response"
            );
        }

        CapturedFunctionCall capturedCall = calls.getFirst();
        CapturedFunctionResponse capturedResponse = responses.getFirst();
        AdkAgentTurnResponse.FunctionCallEvent call = capturedCall.call();
        AdkAgentTurnResponse.FunctionResponseEvent response =
                capturedResponse.response();
        requireEqual(PUBLIC_FUNCTION_NAME, call.name(),
                "captured function-call name");
        requireEqual(PUBLIC_FUNCTION_NAME, response.name(),
                "captured function-response name");
        requireText(call.id(), "captured function-call ID");
        requireEqual(call.id(), response.id(),
                "captured function call/response ID");
        if (capturedResponse.sequence() <= capturedCall.sequence()) {
            throw invalid(
                    "captured function response must follow its function call"
            );
        }
        validatePublicFunctionArguments(call.arguments());
        validatePublicFunctionResponse(response.response());
        agent.toolEvents().forEach(
                IncidentLabReplayService::validatePublicToolArguments
        );
    }

    private static void validatePublicFunctionArguments(
            Map<String, Object> arguments
    ) {
        requireExactKeys(
                arguments,
                PUBLIC_FUNCTION_ARGUMENTS,
                "captured function-call arguments"
        );
        String logQuery = requirePublicString(
                arguments.get("log_query"),
                "captured log query"
        );
        String runbookQuery = requirePublicString(
                arguments.get("runbook_query"),
                "captured runbook query"
        );
        if (logQuery.length() > 160 || runbookQuery.length() > 160) {
            throw invalid("captured public query exceeds the bounded length");
        }
        String diagnosticProbe = requirePublicString(
                arguments.get("diagnostic_probe"),
                "captured diagnostic probe"
        );
        if (!PUBLIC_DIAGNOSTIC_PROBES.contains(diagnosticProbe)) {
            throw invalid("captured diagnostic probe is not allowlisted");
        }
    }

    private static void validatePublicFunctionResponse(
            Map<String, Object> response
    ) {
        requireExactKeys(
                response,
                PUBLIC_FUNCTION_RESPONSE_FIELDS,
                "captured function-response fields"
        );
        String status = requirePublicString(
                response.get("status"),
                "captured function-response status"
        );
        if (!PUBLIC_RESPONSE_STATUSES.contains(status)) {
            throw invalid("captured function-response status is not allowlisted");
        }
        requirePublicString(
                response.get("safe_summary"),
                "captured function-response safe summary"
        );
        requirePublicString(
                response.get("scenario_id"),
                "captured function-response scenario ID"
        );
        requirePublicStringList(
                response.get("evidence_ids"),
                "captured function-response evidence IDs"
        );
        requirePublicStringList(
                response.get("source_refs"),
                "captured function-response source refs"
        );
        requireFalseWhenPresent(
                response,
                "write_capability",
                "captured function-response write capability"
        );
        requireFalseWhenPresent(
                response,
                "action_executed",
                "captured function-response action execution"
        );
    }

    private static void validatePublicToolArguments(LiveToolEvent event) {
        Map<String, Object> arguments = event.arguments();
        Set<String> allowed = switch (event.toolName()) {
            case GET_METRICS -> Set.of("metric_names", "start", "end");
            case SEARCH_LOGS -> Set.of(
                    "services",
                    "levels",
                    "query",
                    "start",
                    "end"
            );
            case GET_TRACE -> Set.of("trace_id");
            case RETRIEVE_RUNBOOKS -> Set.of("query", "max_results");
        };
        requireExactKeys(
                arguments,
                allowed,
                "captured " + event.toolName().wireValue() + " arguments"
        );
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            switch (entry.getKey()) {
                case "metric_names", "services", "levels" ->
                        requirePublicStringList(
                                entry.getValue(),
                                "captured " + entry.getKey()
                        );
                case "max_results" -> requireBoundedInteger(
                        entry.getValue(),
                        "captured max_results"
                );
                default -> requirePublicString(
                        entry.getValue(),
                        "captured " + entry.getKey()
                );
            }
        }
    }

    private static void requireExactKeys(
            Map<String, Object> values,
            Set<String> expected,
            String field
    ) {
        if (values == null) {
            throw invalid(field + " must use the exact public allowlist");
        }
        rejectSensitiveFieldNames(values.keySet(), field);
        if (!values.keySet().equals(expected)) {
            throw invalid(field + " must use the exact public allowlist");
        }
    }

    private static void rejectSensitiveFieldNames(
            Set<String> fieldNames,
            String field
    ) {
        if (fieldNames.stream().anyMatch(name -> name == null
                || SENSITIVE_FIELD_NAME.matcher(name).matches())) {
            throw invalid(field + " contains a sensitive field name");
        }
    }

    private static String requirePublicString(Object value, String field) {
        if (!(value instanceof String text)
                || text.isBlank()
                || text.length() > MAX_PUBLIC_VALUE_LENGTH
                || SENSITIVE_VALUE.matcher(text).matches()) {
            throw invalid(
                    field + " must be bounded, non-sensitive plain text"
            );
        }
        return text;
    }

    private static void requirePublicStringList(Object value, String field) {
        if (!(value instanceof List<?> values)
                || values.size() > 100
                || values.stream().anyMatch(item -> !(item instanceof String))) {
            throw invalid(field + " must be a flat list of public strings");
        }
        values.forEach(item -> requirePublicString(item, field));
    }

    private static void requireBoundedInteger(Object value, String field) {
        if (!(value instanceof Number number)
                || number.doubleValue() != number.intValue()
                || number.intValue() < 1
                || number.intValue() > 10) {
            throw invalid(field + " must be a bounded integer");
        }
    }

    private static void requireFalseWhenPresent(
            Map<String, Object> values,
            String key,
            String field
    ) {
        if (values.containsKey(key) && !Boolean.FALSE.equals(values.get(key))) {
            throw invalid(field + " must be false");
        }
    }

    private static void validateRun(IncidentLabRunResponse run) {
        Objects.requireNonNull(run.plan(), "run plan must not be null");
        Objects.requireNonNull(run.scenario(), "run scenario must not be null");
        Objects.requireNonNull(
                run.generationReceipt(),
                "generation receipt must not be null"
        );
        requireEqual(
                run.scenario().scenarioId(),
                run.generationReceipt().scenarioId(),
                "generated scenario ID"
        );
        validateAction(run.actionReceipt(), "top-level action receipt");
        validateAction(
                run.localizedPresentations().sv().actionReceipt(),
                "Swedish action receipt"
        );
        validateAction(
                run.localizedPresentations().en().actionReceipt(),
                "English action receipt"
        );

        for (LogEvidence log : run.backendLogs()) {
            requireEqual(
                    run.scenario().scenarioId(),
                    log.scenarioId(),
                    "backend log scenario ID"
            );
        }
        Set<String> backendLogIds = run.backendLogs().stream()
                .map(LogEvidence::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!backendLogIds.containsAll(
                run.developerResponse().highlightedLogEvidenceIds()
        )) {
            throw invalid("highlighted log IDs must exist in backend logs");
        }

        if (run.alarmReceipt() != null) {
            requireEqual(
                    run.scenario().scenarioId(),
                    run.alarmReceipt().scenarioId(),
                    "alarm scenario ID"
            );
        }
        AdkAgentTurnResponse agent = run.agentTurn();
        if (agent == null) {
            validateNotStarted(run);
            return;
        }
        if (run.alarmReceipt() == null) {
            throw invalid("a run with an agent requires an alarm receipt");
        }

        requireEqual(
                run.scenario().scenarioId(),
                agent.scenario().scenarioId(),
                "agent scenario ID"
        );
        if (!agent.runtime().runnerInvoked() || agent.runtime().streamed()) {
            throw invalid("recorded agent must be an invoked post-run workflow");
        }
        if (agent.workflow() == null || !agent.workflow().completedInOrder()) {
            throw invalid("recorded agent workflow must have completed in order");
        }
        if (agent.events().stream().anyMatch(event -> event.text() != null)) {
            throw invalid("recorded public events must not contain model text");
        }
        validateActionReceiptCounts(agent);
        validateEvidenceScenarioIds(run, agent.toolEvents());
        if (run.actionReceipt().readOperations()
                != agent.receipt().readOperations()) {
            throw invalid("top-level and agent read counts must match");
        }

        IncidentLabRunResponse.AnswerState answerState = run.answerState();
        if (answerState == null) {
            throw invalid("answer state must not be null");
        }
        switch (answerState) {
            case DIAGNOSED -> validateReleasedAnswer(run, agent, true);
            case INSUFFICIENT_EVIDENCE ->
                    validateReleasedAnswer(run, agent, false);
            case WITHHELD -> validateWithheld(run, agent);
            case NOT_STARTED -> throw invalid(
                    "not_started replay must not contain an agent"
            );
        }
    }

    private static void validateNotStarted(IncidentLabRunResponse run) {
        if (run.answerState() != IncidentLabRunResponse.AnswerState.NOT_STARTED
                || !"no_alarm".equals(run.outcome())
                || run.alarmReceipt() != null) {
            throw invalid(
                    "a run without an agent must be a no_alarm not_started run"
            );
        }
        requireDiagnosisFields(run, false);
    }

    private static void validateReleasedAnswer(
            IncidentLabRunResponse run,
            AdkAgentTurnResponse agent,
            boolean diagnosed
    ) {
        if (!"alarm_investigated".equals(run.outcome())
                || !"completed".equals(agent.outcome())) {
            throw invalid(
                    "released replay requires a completed investigation"
            );
        }
        AdkAgentTurnResponse.VerificationEvent event =
                agent.verificationEvent();
        if (event == null
                || !event.schemaValid()
                || !event.citationsValid()
                || !event.directEvidenceSupportValid()
                || !event.factualResultMatchesGroundTruth()
                || !event.agentSequenceValid()
                || !event.evidenceHandoffValid()
                || !event.toolBoundaryValid()
                || !event.finalAuthorValid()
                || !event.answerReleased()) {
            throw invalid(
                    "released replay requires every verification flag to pass"
            );
        }

        VerificationReport report = agent.verification();
        if (report == null
                || !report.hardErrors().isEmpty()
                || !report.diagnosisSchemaPass()
                || !report.groundTruthSchemaPass()
                || !report.citationValidity().valid()) {
            throw invalid(
                    "released replay requires an error-free verification report"
            );
        }
        var correctness = report.diagnosisCorrectness();
        boolean coherentCorrectness = diagnosed
                ? correctness.evaluated()
                        && correctness.diagnosisApplicable()
                        && correctness.rootCauseCorrect()
                        && correctness.affectedServiceCorrect()
                        && !correctness.abstentionCorrect()
                : correctness.evaluated()
                        && !correctness.diagnosisApplicable()
                        && !correctness.rootCauseCorrect()
                        && !correctness.affectedServiceCorrect()
                        && correctness.abstentionCorrect();
        if (!coherentCorrectness) {
            throw invalid("answer state conflicts with diagnosis correctness");
        }
        requireDiagnosisFields(run, diagnosed);
    }

    private static void validateWithheld(
            IncidentLabRunResponse run,
            AdkAgentTurnResponse agent
    ) {
        if (!"alarm_detected_investigation_withheld".equals(run.outcome())
                || !"verification_failed".equals(agent.outcome())
                || agent.verificationEvent() == null
                || agent.verificationEvent().answerReleased()) {
            throw invalid(
                    "withheld replay requires a failed, unreleased verification"
            );
        }
        requireDiagnosisFields(run, false);
    }

    private static void requireDiagnosisFields(
            IncidentLabRunResponse run,
            boolean required
    ) {
        List<IncidentLabRunResponse.DeveloperResponse> responses = List.of(
                run.developerResponse(),
                run.localizedPresentations().sv().developerResponse(),
                run.localizedPresentations().en().developerResponse()
        );
        if (!required) {
            if (responses.stream().anyMatch(response ->
                    response.rootCauseCode() != null
                            || response.affectedService() != null)) {
                throw invalid(
                        "answer state must not publish diagnosis fields"
                );
            }
            return;
        }

        String rootCause = responses.getFirst().rootCauseCode();
        String service = responses.getFirst().affectedService();
        requireText(rootCause, "diagnosed root cause");
        requireText(service, "diagnosed affected service");
        if (responses.stream().anyMatch(response ->
                !rootCause.equals(response.rootCauseCode())
                        || !service.equals(response.affectedService()))) {
            throw invalid(
                    "diagnosed fields must match across localized presentations"
            );
        }
    }

    private static void validateAction(
            IncidentLabRunResponse.ActionReceipt receipt,
            String name
    ) {
        if (receipt == null
                || receipt.writeToolsAvailable()
                || receipt.actionExecuted()
                || !receipt.humanApprovalRequired()) {
            throw invalid(name + " must remain read-only and human-approved");
        }
    }

    private static void validateActionReceiptCounts(AdkAgentTurnResponse agent) {
        AdkAgentTurnResponse.ControlReceipt receipt = Objects.requireNonNull(
                agent.receipt(),
                "agent receipt must not be null"
        );
        if (receipt.writeToolsAvailable()
                || receipt.actionExecuted()
                || !receipt.humanApprovalRequired()) {
            throw invalid("agent receipt must remain read-only and human-approved");
        }
        long modelCalls = agent.events().stream()
                .filter(event -> !event.functionCalls().isEmpty()
                        || event.finalResponse())
                .count();
        long adkCalls = agent.events().stream()
                .mapToLong(event -> event.functionResponses().size())
                .sum();
        long embeddings = agent.toolEvents().stream()
                .map(LiveToolEvent::runbookRetrieval)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.queryEmbedding())
                .filter(Objects::nonNull)
                .count();
        int reads = agent.toolEvents().size()
                + (agent.diagnosticProbe() == null ? 0 : 1);
        if (receipt.modelCalls() != modelCalls
                || receipt.adkToolCalls() != adkCalls
                || receipt.embeddingCalls() != embeddings
                || receipt.readOperations() != reads) {
            throw invalid("agent receipt counts must match recorded events");
        }
    }

    private static void validateEvidenceScenarioIds(
            IncidentLabRunResponse run,
            List<LiveToolEvent> events
    ) {
        for (LiveToolEvent event : events) {
            for (Evidence evidence : event.evidence()) {
                requireEqual(
                        run.scenario().scenarioId(),
                        evidence.scenarioId(),
                        "tool evidence scenario ID"
                );
            }
        }
    }

    private static void requireRecordedLiveProof(IncidentLabRunResponse run) {
        AdkAgentTurnResponse agent = run.agentTurn();
        if (run.alarmReceipt() == null
                || agent == null
                || agent.providerRoute() == null
                || agent.receipt().modelCalls() < 1
                || agent.receipt().embeddingCalls() < 1) {
            throw invalid("captured replay requires alarm, provider, ADK, and embedding receipts");
        }
        boolean pgvector = agent.toolEvents().stream()
                .map(LiveToolEvent::runbookRetrieval)
                .filter(Objects::nonNull)
                .anyMatch(metadata ->
                        RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE
                                .wireValue()
                                .equals(metadata.backend())
                                && metadata.queryEmbedding() != null
                                && metadata.matches() != null
                                && !metadata.matches().isEmpty()
                );
        if (!pgvector) {
            throw invalid("captured replay requires recorded pgvector retrieval");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireEqual(
            Object expected,
            Object actual,
            String field
    ) {
        if (!Objects.equals(expected, actual)) {
            throw invalid(field + " does not match");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " must not be blank");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "Invalid Incident Lab replay fixture: " + message
        );
    }

    private record CapturedFunctionCall(
            int sequence,
            AdkAgentTurnResponse.FunctionCallEvent call
    ) {
    }

    private record CapturedFunctionResponse(
            int sequence,
            AdkAgentTurnResponse.FunctionResponseEvent response
    ) {
    }

    private record LoadedFixture(
            IncidentLabReplayFixture fixture,
            String resourceSha256
    ) {
    }
}
