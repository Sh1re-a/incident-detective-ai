package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import dev.shirwac.incidentdetective.investigation.tools.InvestigationToolExecutor;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

@Service
public final class LiveInvestigationService {

    public static final String TRUTH_LABEL =
            "Simulated incident — real AI investigation.";
    static final int MAX_COLLECTION_ROUNDS = 2;
    static final int MAX_TOOL_CALLS_TOTAL = 8;
    static final int MAX_TOOL_CALLS_PER_TYPE = 2;
    static final int MAX_TOOL_CALLS_PER_ROUND = 3;
    static final Duration HARD_DEADLINE = Duration.ofSeconds(45);
    static final Duration PROVIDER_CALL_CAP = Duration.ofSeconds(22);
    static final Duration SYNTHESIS_RESERVE = Duration.ofSeconds(22);
    static final Duration DEADLINE_SAFETY_MARGIN = Duration.ofSeconds(1);
    static final Duration MIN_SECOND_COLLECTION_TIMEOUT = Duration.ofSeconds(8);

    private static final BigDecimal INPUT_USD_PER_MILLION =
            new BigDecimal("0.75");
    private static final BigDecimal OUTPUT_USD_PER_MILLION =
            new BigDecimal("3.75");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final List<String> LIMITATIONS = List.of(
            "All incident data is synthetic.",
            "Runbook retrieval currently uses deterministic local keyword matching, not pgvector.",
            "The system recommends next steps but never executes remediation."
    );

    private final GeminiAiProperties properties;
    private final InvestigationScenarioCatalog scenarios;
    private final InvestigationToolExecutor tools;
    private final InvestigationModelGateway model;
    private final CompletedInvestigationVerifier verifier;
    private final Clock clock;

    public LiveInvestigationService(
            GeminiAiProperties properties,
            InvestigationScenarioCatalog scenarios,
            InvestigationToolExecutor tools,
            InvestigationModelGateway model,
            CompletedInvestigationVerifier verifier,
            Clock clock
    ) {
        this.properties = properties;
        this.scenarios = scenarios;
        this.tools = tools;
        this.model = model;
        this.verifier = verifier;
        this.clock = clock;
    }

    public LiveInvestigationResult investigate(
            String scenarioId,
            LiveInvestigationRequest request
    ) {
        requireLiveAccess(request);
        Instant startedAt = clock.instant();
        Scenario scenario = scenarios.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        List<String> availableMetricNames = tools.availableMetricNames(scenarioId);
        Map<String, Evidence> evidenceById = new LinkedHashMap<>();
        List<LiveToolEvent> toolEvents = new ArrayList<>();
        List<ModelCallMetadata> modelCalls = new ArrayList<>();
        Map<ToolName, Integer> callsByType = new EnumMap<>(ToolName.class);
        Set<String> callIds = new HashSet<>();

        for (int round = 1; round <= MAX_COLLECTION_ROUNDS; round++) {
            requireWithinDeadline(startedAt);
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
                    round,
                    timeout.orElseThrow()
            );
            requireWithinDeadline(startedAt);
            modelCalls.add(collection.metadata());
            if (collection.toolCalls().isEmpty()) {
                break;
            }
            enforceRoundBudget(collection.toolCalls());
            for (CollectionToolCall call : collection.toolCalls()) {
                enforceToolBudget(call, toolEvents.size(), callsByType, callIds);
                ToolExecution execution = tools.execute(scenarioId, call);
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
                        execution.evidence()
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

        CompletedInvestigationVerification verification = verifier.verify(
                scenarioId,
                synthesis.diagnosis(),
                Set.copyOf(evidenceById.keySet())
        );
        LiveRunStatus status = verification.report().hardErrors().isEmpty()
                ? LiveRunStatus.COMPLETED
                : LiveRunStatus.VERIFICATION_FAILED;
        ModelTokenUsage usage = aggregateUsage(modelCalls);
        Instant completedAt = clock.instant();

        return new LiveInvestigationResult(
                UUID.randomUUID().toString(),
                scenarioId,
                RunMode.LIVE_AI,
                TRUTH_LABEL,
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
                properties.promptVersion(),
                modelCalls,
                usage,
                estimateCost(usage),
                toolEvents.size(),
                modelCalls.size(),
                LIMITATIONS
        );
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

    private void enforceRoundBudget(List<CollectionToolCall> calls) {
        if (calls.size() > MAX_TOOL_CALLS_PER_ROUND) {
            throw malformed("Model exceeded the per-round tool-call budget");
        }
    }

    private void enforceToolBudget(
            CollectionToolCall call,
            int completedCalls,
            Map<ToolName, Integer> callsByType,
            Set<String> callIds
    ) {
        if (!callIds.add(call.callId())) {
            throw malformed("Model repeated a function-call ID");
        }
        if (completedCalls >= MAX_TOOL_CALLS_TOTAL) {
            throw malformed("Model exceeded the total tool-call budget");
        }
        int nextCount = callsByType.getOrDefault(call.toolName(), 0) + 1;
        if (nextCount > MAX_TOOL_CALLS_PER_TYPE) {
            throw malformed("Model exceeded the per-tool call budget");
        }
        callsByType.put(call.toolName(), nextCount);
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
        Duration timeout = min(PROVIDER_CALL_CAP, available);
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
        int input = calls.stream()
                .map(ModelCallMetadata::tokenUsage)
                .mapToInt(ModelTokenUsage::inputTokens)
                .sum();
        int output = calls.stream()
                .map(ModelCallMetadata::tokenUsage)
                .mapToInt(ModelTokenUsage::outputTokens)
                .sum();
        int total = calls.stream()
                .map(ModelCallMetadata::tokenUsage)
                .mapToInt(ModelTokenUsage::totalTokens)
                .sum();
        return new ModelTokenUsage(input, output, total);
    }

    private BigDecimal estimateCost(ModelTokenUsage usage) {
        BigDecimal inputCost = BigDecimal.valueOf(usage.inputTokens())
                .multiply(INPUT_USD_PER_MILLION)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(usage.outputTokens())
                .multiply(OUTPUT_USD_PER_MILLION)
                .divide(ONE_MILLION, 12, RoundingMode.HALF_UP);
        return inputCost.add(outputCost).setScale(8, RoundingMode.HALF_UP);
    }

    private ModelProviderException malformed(String message) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                message
        );
    }
}
