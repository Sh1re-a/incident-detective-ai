package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.CollectionToolBudget;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.InvestigationToolExecutor;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public final class LiveInvestigationService {

    public static final String TRUTH_LABEL =
            "Simulated incident — real AI investigation.";
    public static final String GENERATED_TRUTH_LABEL =
            "Generated synthetic incident — real AI investigation.";
    static final int MAX_COLLECTION_ROUNDS = 2;
    static final int MAX_TOOL_CALLS_TOTAL = 8;
    static final int MAX_TOOL_CALLS_PER_TYPE = 2;
    static final int MAX_METRIC_CALLS = 1;
    static final int MAX_RUNBOOK_CALLS = 1;
    static final int MAX_TOOL_CALLS_PER_ROUND = 3;
    static final Duration HARD_DEADLINE = Duration.ofSeconds(45);
    static final Duration PROVIDER_CALL_CAP = Duration.ofSeconds(28);
    static final Duration SECOND_COLLECTION_CALL_CAP = Duration.ofSeconds(8);
    static final Duration SYNTHESIS_RESERVE = Duration.ofSeconds(15);
    static final Duration DEADLINE_SAFETY_MARGIN = Duration.ofSeconds(1);
    static final Duration MIN_SECOND_COLLECTION_TIMEOUT = Duration.ofSeconds(8);
    static final int DAILY_LIVE_RUN_LIMIT = 20;

    private final GeminiAiProperties properties;
    private final InvestigationScenarioCatalog scenarios;
    private final InvestigationToolExecutor tools;
    private final InvestigationModelGateway model;
    private final CompletedInvestigationVerifier verifier;
    private final GroundTruthInvestigationVerifier groundTruthVerifier;
    private final GeminiCostEstimator costEstimator;
    private final LiveInvestigationAdmissionGuard admissionGuard;
    private final LiveInvestigationMetrics metrics;
    private final GlobalDailyLiveQuota dailyQuota;
    private final Clock clock;

    public LiveInvestigationService(
            GeminiAiProperties properties,
            InvestigationScenarioCatalog scenarios,
            InvestigationToolExecutor tools,
            InvestigationModelGateway model,
            CompletedInvestigationVerifier verifier,
            GroundTruthInvestigationVerifier groundTruthVerifier,
            GeminiCostEstimator costEstimator,
            LiveInvestigationAdmissionGuard admissionGuard,
            LiveInvestigationMetrics metrics,
            GlobalDailyLiveQuota dailyQuota,
            Clock clock
    ) {
        this.properties = properties;
        this.scenarios = scenarios;
        this.tools = tools;
        this.model = model;
        this.verifier = verifier;
        this.groundTruthVerifier = groundTruthVerifier;
        this.costEstimator = costEstimator;
        this.admissionGuard = admissionGuard;
        this.metrics = metrics;
        this.dailyQuota = dailyQuota;
        this.clock = clock;
    }

    public LiveInvestigationResult investigate(
            String scenarioId,
            LiveInvestigationRequest request
    ) {
        long startedAtNanos = System.nanoTime();
        try {
            requireLiveAccess(request);
            Scenario scenario = scenarios.findById(scenarioId)
                    .orElseThrow(() -> new InvestigationScenarioNotFoundException(
                            scenarioId
                    ));
            java.util.function.Supplier<LiveInvestigationResult> action = () -> {
                consumeDailyQuota();
                return investigateAdmitted(catalogContext(scenarioId, scenario));
            };
            LiveInvestigationResult result = admissionGuard.admit(action);
            metrics.recordResult(
                    result.status(),
                    elapsedMillis(startedAtNanos),
                    result.toolCallCount(),
                    result.modelCallCount()
            );
            return result;
        } catch (RuntimeException exception) {
            metrics.recordFailure(elapsedMillis(startedAtNanos));
            throw exception;
        }
    }

    public LiveInvestigationResult investigateGenerated(
            InvestigationData data,
            GroundTruth groundTruth,
            LiveInvestigationRequest request
    ) {
        long startedAtNanos = System.nanoTime();
        try {
            requireLiveAccess(request);
            requireMatchingGeneratedCase(data, groundTruth);
            java.util.function.Supplier<LiveInvestigationResult> action = () -> {
                consumeDailyQuota();
                return investigateAdmitted(generatedContext(data, groundTruth));
            };
            LiveInvestigationResult result = admissionGuard.admit(action);
            metrics.recordResult(
                    result.status(),
                    elapsedMillis(startedAtNanos),
                    result.toolCallCount(),
                    result.modelCallCount()
            );
            return result;
        } catch (RuntimeException exception) {
            metrics.recordFailure(elapsedMillis(startedAtNanos));
            throw exception;
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
    }

    private LiveInvestigationResult investigateAdmitted(
            InvestigationContext context
    ) {
        String scenarioId = context.scenarioId();
        Scenario scenario = context.scenario();
        Instant startedAt = clock.instant();
        List<String> availableMetricNames = context.availableMetricNames();
        Map<String, Evidence> evidenceById = new LinkedHashMap<>();
        List<LiveToolEvent> toolEvents = new ArrayList<>();
        List<ModelCallMetadata> modelCalls = new ArrayList<>();
        Map<ToolName, Integer> callsByType = new EnumMap<>(ToolName.class);
        Set<String> callIds = new HashSet<>();

        for (int round = 1; round <= MAX_COLLECTION_ROUNDS; round++) {
            requireWithinDeadline(startedAt);
            Set<String> traceIdsAvailableToModel = discoveredTraceIds(
                    evidenceById.values()
            );
            CollectionToolBudget toolBudget = collectionToolBudget(
                    toolEvents.size(),
                    callsByType,
                    traceIdsAvailableToModel
            );
            if (toolBudget.allowedTools().isEmpty()) {
                break;
            }
            Optional<Duration> timeout = collectionTimeoutFor(
                    elapsedSince(startedAt),
                    round
            );
            if (timeout.isEmpty()) {
                if (round == 1) {
                    throw deadlineExceeded();
                }
                break;
            }
            CollectionModelResult collection = model.collect(
                    scenario,
                    availableMetricNames,
                    List.copyOf(evidenceById.values()),
                    toolBudget,
                    round,
                    timeout.orElseThrow()
            );
            requireWithinDeadline(startedAt);
            modelCalls.add(collection.metadata());
            if (collection.toolCalls().isEmpty()) {
                break;
            }
            preflightToolCalls(
                    collection.toolCalls(),
                    toolBudget,
                    callsByType,
                    callIds
            );
            for (CollectionToolCall call : collection.toolCalls()) {
                ToolExecution execution = context.executeTool().apply(call);
                ensureScenarioIsolation(scenarioId, execution.evidence());
                execution.evidence().forEach(evidence ->
                        evidenceById.putIfAbsent(evidence.evidenceId(), evidence)
                );
                toolEvents.add(new LiveToolEvent(
                        execution.callId(),
                        round,
                        execution.toolName(),
                        execution.arguments(),
                        execution.safeSummary(),
                        execution.evidence(),
                        execution.runbookRetrieval()
                ));
            }
        }

        requireWithinDeadline(startedAt);
        Duration synthesisTimeout = synthesisTimeoutFor(
                elapsedSince(startedAt)
        ).orElseThrow(this::deadlineExceeded);
        SynthesisModelResult synthesis = model.synthesize(
                scenario,
                List.copyOf(evidenceById.values()),
                synthesisTimeout
        );
        requireWithinDeadline(startedAt);
        modelCalls.add(synthesis.metadata());

        CompletedInvestigationVerification verification = context.verify().apply(
                synthesis.diagnosis(),
                Set.copyOf(evidenceById.keySet())
        );
        if (!verification.report().diagnosisSchemaPass()) {
            throw malformed("Model diagnosis failed the validated contract");
        }
        LiveRunStatus status = verification.report().hardErrors().isEmpty()
                ? LiveRunStatus.COMPLETED
                : LiveRunStatus.VERIFICATION_FAILED;
        ModelTokenUsage usage = aggregateUsage(modelCalls);
        ModelCostEstimate cost = costEstimator.estimate(
                properties.modelId(),
                usage
        );
        Instant completedAt = clock.instant();

        return new LiveInvestigationResult(
                UUID.randomUUID().toString(),
                scenarioId,
                RunMode.LIVE_AI,
                context.truthLabel(),
                status,
                startedAt,
                completedAt,
                Math.max(0, Duration.between(startedAt, completedAt).toMillis()),
                scenario,
                toolEvents,
                synthesis.diagnosis(),
                verification.report(),
                verification.comparison(),
                properties.modelId(),
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                modelCalls,
                usage,
                aggregatePromptCache(modelCalls),
                cost.estimatedUsd(),
                cost.breakdown(),
                cost.basis(),
                toolEvents.size(),
                modelCalls.size(),
                context.limitations().get()
        );
    }

    private List<String> limitations() {
        return List.of(
                "All incident data is synthetic.",
                tools.runbookRetrievalLimitation(),
                "The system recommends next steps but never executes remediation."
        );
    }

    private List<String> generatedLimitations() {
        return List.of(
                "The incident and its signals were generated from a versioned synthetic template.",
                "User-supplied text, files, and real company data are not accepted.",
                tools.runbookRetrievalLimitation(),
                "Correctness is checked only against this case's generator GroundTruth; it is not a model-accuracy claim.",
                "The generated case exists only for this request and is not persisted.",
                "The system recommends next steps but never executes remediation."
        );
    }

    private InvestigationContext catalogContext(
            String scenarioId,
            Scenario scenario
    ) {
        return new InvestigationContext(
                scenarioId,
                scenario,
                tools.availableMetricNames(scenarioId),
                call -> tools.execute(scenarioId, call),
                (diagnosis, seenEvidenceIds) -> verifier.verify(
                        scenarioId,
                        diagnosis,
                        seenEvidenceIds
                ),
                TRUTH_LABEL,
                this::limitations
        );
    }

    private InvestigationContext generatedContext(
            InvestigationData data,
            GroundTruth groundTruth
    ) {
        String scenarioId = data.scenario().scenarioId();
        return new InvestigationContext(
                scenarioId,
                data.scenario(),
                tools.availableMetricNames(data),
                call -> tools.execute(data, call),
                (diagnosis, seenEvidenceIds) -> groundTruthVerifier.verify(
                        groundTruth,
                        diagnosis,
                        seenEvidenceIds
                ),
                GENERATED_TRUTH_LABEL,
                this::generatedLimitations
        );
    }

    private void requireMatchingGeneratedCase(
            InvestigationData data,
            GroundTruth groundTruth
    ) {
        if (data == null || groundTruth == null) {
            throw new IllegalArgumentException(
                    "Generated investigation data and GroundTruth are required"
            );
        }
        String scenarioId = data.scenario().scenarioId();
        if (!scenarioId.equals(groundTruth.scenarioId())) {
            throw new IllegalArgumentException(
                    "Generated investigation data and GroundTruth must share scenario_id"
            );
        }
    }

    private void consumeDailyQuota() {
        GlobalDailyLiveQuota.Decision decision = dailyQuota.tryConsume(
                DAILY_LIVE_RUN_LIMIT
        );
        if (!decision.allowed()) {
            throw new LiveDailyQuotaExceededException(decision.resetsAt());
        }
    }

    private void requireLiveAccess(LiveInvestigationRequest request) {
        if (request == null || !request.confirmLiveAi()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.CONFIRMATION_REQUIRED,
                    "Live AI request was not explicitly confirmed"
            );
        }
        if (!properties.liveEnabled()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.LIVE_AI_DISABLED,
                    "Live AI is disabled by server configuration"
            );
        }
        if (!properties.hasApiKey()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.API_KEY_MISSING,
                    "Gemini API key is missing"
            );
        }
    }

    private void preflightToolCalls(
            List<CollectionToolCall> calls,
            CollectionToolBudget budget,
            Map<ToolName, Integer> callsByType,
            Set<String> callIds
    ) {
        if (calls.size() > budget.maxCallsThisRound()) {
            throw malformed("Model exceeded the remaining round tool-call budget");
        }
        Set<ToolName> allowedTools = Set.copyOf(budget.allowedTools());
        EnumMap<ToolName, Integer> nextCounts = new EnumMap<>(callsByType);
        Set<String> nextCallIds = new HashSet<>(callIds);

        for (CollectionToolCall call : calls) {
            if (!allowedTools.contains(call.toolName())) {
                throw malformed("Model requested a tool outside the remaining budget");
            }
            if (!nextCallIds.add(call.callId())) {
                throw malformed("Model repeated a function-call ID");
            }
            int nextCount = nextCounts.getOrDefault(call.toolName(), 0) + 1;
            int limit = perInvestigationLimit(call.toolName());
            if (nextCount > limit) {
                throw malformed("Model exceeded the per-tool call budget");
            }
            enforceTraceDiscovery(call, budget.discoveredTraceIds());
            nextCounts.put(call.toolName(), nextCount);
        }

        if (nextCallIds.size() > MAX_TOOL_CALLS_TOTAL) {
            throw malformed("Model exceeded the total tool-call budget");
        }
        callsByType.clear();
        callsByType.putAll(nextCounts);
        callIds.clear();
        callIds.addAll(nextCallIds);
    }

    private void enforceTraceDiscovery(
            CollectionToolCall call,
            Set<String> traceIdsAvailableToModel
    ) {
        if (call.toolName() != ToolName.GET_TRACE) {
            return;
        }

        Object traceId = call.arguments().get("trace_id");
        if (!(traceId instanceof String value)
                || !traceIdsAvailableToModel.contains(value)) {
            throw malformed(
                    "Model requested a trace ID that was not present in previously collected evidence"
            );
        }
    }

    private Set<String> discoveredTraceIds(Iterable<Evidence> evidence) {
        Set<String> traceIds = new HashSet<>();
        for (Evidence item : evidence) {
            if (item instanceof LogEvidence log) {
                String traceId = log.content().attributes().get("trace_id");
                if (traceId != null && !traceId.isBlank()) {
                    traceIds.add(traceId);
                }
            } else if (item instanceof TraceEvidence trace) {
                traceIds.add(trace.content().traceId());
            }
        }
        return Set.copyOf(traceIds);
    }

    private CollectionToolBudget collectionToolBudget(
            int completedCalls,
            Map<ToolName, Integer> callsByType,
            Set<String> discoveredTraceIds
    ) {
        int remainingTotal = Math.max(0, MAX_TOOL_CALLS_TOTAL - completedCalls);
        EnumMap<ToolName, Integer> remainingByTool = new EnumMap<>(ToolName.class);
        for (ToolName toolName : ToolName.values()) {
            int remaining = perInvestigationLimit(toolName)
                    - callsByType.getOrDefault(toolName, 0);
            remainingByTool.put(toolName, Math.max(0, remaining));
        }
        return new CollectionToolBudget(
                remainingTotal,
                Math.min(MAX_TOOL_CALLS_PER_ROUND, remainingTotal),
                remainingByTool,
                discoveredTraceIds
        );
    }

    private int perInvestigationLimit(ToolName toolName) {
        return switch (toolName) {
            case GET_METRICS -> MAX_METRIC_CALLS;
            case RETRIEVE_RUNBOOKS -> MAX_RUNBOOK_CALLS;
            default -> MAX_TOOL_CALLS_PER_TYPE;
        };
    }

    private void ensureScenarioIsolation(
            String scenarioId,
            List<Evidence> evidence
    ) {
        boolean foreignEvidence = evidence.stream()
                .anyMatch(item -> !scenarioId.equals(item.scenarioId()));
        if (foreignEvidence) {
            throw new IllegalStateException(
                    "Read-only tool returned evidence from another scenario"
            );
        }
    }

    private void requireWithinDeadline(Instant startedAt) {
        if (elapsedSince(startedAt).compareTo(HARD_DEADLINE) >= 0) {
            throw deadlineExceeded();
        }
    }

    static Optional<Duration> collectionTimeoutFor(
            Duration elapsed,
            int round
    ) {
        if (round < 1 || round > MAX_COLLECTION_ROUNDS) {
            throw new IllegalArgumentException("Collection round is outside the budget");
        }
        Duration available = remainingFor(elapsed)
                .minus(SYNTHESIS_RESERVE)
                .minus(DEADLINE_SAFETY_MARGIN);
        if (!available.isPositive()) {
            return Optional.empty();
        }
        Duration callCap = round == 1
                ? PROVIDER_CALL_CAP
                : SECOND_COLLECTION_CALL_CAP;
        Duration timeout = min(callCap, available);
        if (round > 1
                && timeout.compareTo(MIN_SECOND_COLLECTION_TIMEOUT) < 0) {
            return Optional.empty();
        }
        return Optional.of(timeout);
    }

    static Optional<Duration> synthesisTimeoutFor(Duration elapsed) {
        Duration available = remainingFor(elapsed)
                .minus(DEADLINE_SAFETY_MARGIN);
        if (!available.isPositive()) {
            return Optional.empty();
        }
        return Optional.of(min(PROVIDER_CALL_CAP, available));
    }

    private static Duration remainingFor(Duration elapsed) {
        Duration normalized = elapsed.isNegative() ? Duration.ZERO : elapsed;
        Duration remaining = HARD_DEADLINE.minus(normalized);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private Duration elapsedSince(Instant startedAt) {
        Duration elapsed = Duration.between(startedAt, clock.instant());
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    private LiveInvestigationException deadlineExceeded() {
        return new LiveInvestigationException(
                LiveInvestigationFailure.DEADLINE_EXCEEDED,
                "Live investigation exceeded its hard deadline"
        );
    }

    private ModelTokenUsage aggregateUsage(List<ModelCallMetadata> calls) {
        List<ModelTokenUsage> usages = calls.stream()
                .map(ModelCallMetadata::tokenUsage)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (usages.isEmpty()) {
            return null;
        }
        return new ModelTokenUsage(
                sumWhenComplete(calls, usages, ModelTokenUsage::inputTokens),
                sumWhenReported(usages, ModelTokenUsage::cachedInputTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::uncachedInputTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::candidateOutputTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::thinkingOutputTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::outputTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::toolUsePromptTokens),
                sumWhenComplete(calls, usages, ModelTokenUsage::totalTokens)
        );
    }

    private PromptCacheTelemetry aggregatePromptCache(
            List<ModelCallMetadata> calls
    ) {
        List<Integer> reported = calls.stream()
                .map(ModelCallMetadata::tokenUsage)
                .filter(java.util.Objects::nonNull)
                .map(ModelTokenUsage::cachedInputTokens)
                .filter(java.util.Objects::nonNull)
                .toList();
        Integer cachedInputTokens = reported.isEmpty()
                ? null
                : reported.stream().mapToInt(Integer::intValue).sum();
        return new PromptCacheTelemetry(
                PromptCacheStrategy.PROVIDER_IMPLICIT,
                reported.size(),
                calls.size(),
                cachedInputTokens,
                reported.stream().anyMatch(tokens -> tokens > 0)
        );
    }

    private Integer sumWhenComplete(
            List<ModelCallMetadata> calls,
            List<ModelTokenUsage> usages,
            java.util.function.Function<ModelTokenUsage, Integer> value
    ) {
        if (usages.size() != calls.size()
                || usages.stream().map(value).anyMatch(java.util.Objects::isNull)) {
            return null;
        }
        return usages.stream().map(value).mapToInt(Integer::intValue).sum();
    }

    private Integer sumWhenReported(
            List<ModelTokenUsage> usages,
            java.util.function.Function<ModelTokenUsage, Integer> value
    ) {
        List<Integer> reported = usages.stream()
                .map(value)
                .filter(java.util.Objects::nonNull)
                .toList();
        return reported.isEmpty()
                ? null
                : reported.stream().mapToInt(Integer::intValue).sum();
    }

    private ModelProviderException malformed(String message) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                message
        );
    }

    private record InvestigationContext(
            String scenarioId,
            Scenario scenario,
            List<String> availableMetricNames,
            Function<CollectionToolCall, ToolExecution> executeTool,
            BiFunction<Diagnosis, Set<String>, CompletedInvestigationVerification> verify,
            String truthLabel,
            Supplier<List<String>> limitations
    ) {
        private InvestigationContext {
            availableMetricNames = List.copyOf(availableMetricNames);
        }
    }
}
