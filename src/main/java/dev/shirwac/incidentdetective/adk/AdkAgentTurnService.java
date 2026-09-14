package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse.ControlReceipt;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse.RuntimeProvenance;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse.SafetyDecision;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse.VerificationEvent;
import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse.WorkflowReceipt;
import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiDiagnosisDecoder;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.ModelResponseFailureMetadata;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.GroundTruthInvestigationVerifier;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.live.LiveInvestigationException;
import dev.shirwac.incidentdetective.live.LiveInvestigationFailure;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.genai.errors.ApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Runs one bounded, request-local Nordly incident turn through Google ADK. */
@Service
public final class AdkAgentTurnService {

    private static final Logger LOG = LoggerFactory.getLogger(
            AdkAgentTurnService.class
    );
    private static final String ADK_PROMPT_VERSION = "nordly-adk-sequential-v2";
    private static final String DELIVERY = "synchronous_post_run";

    private final AdkProperties adk;
    private final GeminiAiProperties ai;
    private final GeneratedCaseFactory cases;
    private final KnowledgeRagSafetyGate safetyGate;
    private final LiveAiRunGuard liveRunGuard;
    private final AdkGeminiModelFactory models;
    private final AdkAgentRuntime runtime;
    private final GeminiDiagnosisDecoder diagnosisDecoder;
    private final GroundTruthInvestigationVerifier verifier;
    private final GeminiCostEstimator costEstimator;
    private final Clock clock;

    public AdkAgentTurnService(
            AdkProperties adk,
            GeminiAiProperties ai,
            GeneratedCaseFactory cases,
            KnowledgeRagSafetyGate safetyGate,
            LiveAiRunGuard liveRunGuard,
            AdkGeminiModelFactory models,
            AdkAgentRuntime runtime,
            GeminiDiagnosisDecoder diagnosisDecoder,
            GroundTruthInvestigationVerifier verifier,
            GeminiCostEstimator costEstimator,
            Clock clock
    ) {
        this.adk = adk;
        this.ai = ai;
        this.cases = cases;
        this.safetyGate = safetyGate;
        this.liveRunGuard = liveRunGuard;
        this.models = models;
        this.runtime = runtime;
        this.diagnosisDecoder = diagnosisDecoder;
        this.verifier = verifier;
        this.costEstimator = costEstimator;
        this.clock = clock;
    }

    public AdkAgentTurnResponse run(AdkAgentTurnRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        KnowledgeRagSafetyGate.Decision safety = safetyGate.evaluate(
                request.message()
        );
        if (!safety.allowed()) {
            return blocked(safety);
        }
        if (!adk.enabled()) {
            throw new LiveInvestigationException(
                    LiveInvestigationFailure.LIVE_AI_DISABLED,
                    "Google ADK turns are disabled by server configuration"
            );
        }
        return liveRunGuard.runConfirmed(
                request.confirmLiveAi(),
                () -> runAdmitted(request, safety)
        );
    }

    private AdkAgentTurnResponse runAdmitted(
            AdkAgentTurnRequest request,
            KnowledgeRagSafetyGate.Decision safety
    ) {
        Instant startedAt = clock.instant();
        GeneratedCase generated = cases.create(new GeneratedCaseRequest(
                request.seed(),
                request.incidentFamily(),
                request.evidenceMode(),
                request.noiseLevel()
        ));

        AdkAgentRuntime.RunResult run;
        try (AdkGeminiModelFactory.ModelLease model = models.create()) {
            run = runtime.run(generated, request.message(), model.model());
        } catch (RuntimeException exception) {
            throw translateAdkFailure(exception);
        }

        AdkAgentRuntime.TrajectoryValidation trajectory =
                runtime.validateTrajectory(run.events());
        String finalText = jsonEnvelope(runtime.finalText(run.events()));
        Diagnosis candidate;
        try {
            candidate = diagnosisDecoder.decode(finalText);
        } catch (ModelProviderException exception) {
            ModelResponseFailureMetadata metadata = exception.safeMetadata()
                    .orElse(null);
            LOG.warn(
                    "ADK final response rejected: failure={}, category={}, reason={}, property_paths={}, events={}, tools={}, chars={}, object_envelope={}",
                    exception.failure(),
                    metadata == null ? "unclassified" : metadata.category(),
                    metadata == null ? "unclassified" : metadata.reason(),
                    metadata == null ? List.of() : metadata.propertyPaths(),
                    run.events().size(),
                    run.toolExecutions().size(),
                    finalText == null ? 0 : finalText.length(),
                    finalText != null
                            && finalText.startsWith("{")
                            && finalText.endsWith("}")
            );
            if (exception.failure() != ModelProviderFailure.MALFORMED_RESPONSE) {
                throw exception;
            }
            return rejectedDiagnosisReceipt(
                    generated,
                    safety,
                    run,
                    trajectory,
                    startedAt
            );
        }
        Set<String> seenEvidenceIds = run.toolExecutions().stream()
                .flatMap(execution -> execution.evidence().stream())
                .map(evidence -> evidence.evidenceId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        CompletedInvestigationVerification checked = verifier.verify(
                generated.hiddenGroundTruth(),
                candidate,
                seenEvidenceIds
        );
        boolean answerReleased = trajectory.completedInOrder()
                && trajectory.agentSequenceValid()
                && trajectory.evidenceHandoffValid()
                && trajectory.toolBoundaryValid()
                && trajectory.finalAuthorValid()
                && trajectory.transferBoundaryValid()
                && run.modelCallCount() == 2
                && run.toolInvocationCount() == 1
                && !run.toolExecutions().isEmpty()
                && checked.report().hardErrors().isEmpty()
                && everyCitationDirectlySupported(checked)
                && factualResultMatches(checked);
        Instant completedAt = clock.instant();
        long latencyMs = Math.max(
                0,
                Duration.between(startedAt, completedAt).toMillis()
        );
        List<LiveToolEvent> toolEvents = run.toolExecutions().stream()
                .map(this::toolEvent)
                .toList();
        int embeddingCallCount = embeddingCalls(toolEvents);
        int providerCallCount = run.modelCallCount() + embeddingCallCount;
        ModelTokenUsage usage = aggregateUsage(run.events().stream()
                .map(event -> event.usageMetadata()
                        .map(runtimeUsage())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList());
        ModelCostEstimate cost = costEstimator.estimate(ai.modelId(), usage);

        return new AdkAgentTurnResponse(
                AdkAgentTurnResponse.CONTRACT_VERSION,
                UUID.randomUUID().toString(),
                run.sessionId(),
                run.turnId(),
                AdkAgentTurnResponse.MODE,
                AdkAgentTurnResponse.TRUTH_LABEL,
                answerReleased ? "completed" : "verification_failed",
                providerRoute(providerCallCount),
                generated.scenario(),
                safety(safety),
                runtimeProvenance(true),
                workflowReceipt(trajectory),
                runtime.projectEvents(run.events(), false),
                toolEvents,
                answerReleased ? candidate : null,
                checked.report(),
                checked.comparison(),
                verificationEvent(
                        checked,
                        trajectory,
                        completedAt,
                        answerReleased
                ),
                new ControlReceipt(
                        run.modelCallCount(),
                        run.toolInvocationCount(),
                        toolEvents.size(),
                        embeddingCallCount,
                        false,
                        false,
                        true,
                        List.of(AdkAgentRuntime.TOOL_NAME),
                        usage,
                        cost.estimatedUsd(),
                        cost.basis(),
                        latencyMs
                ),
                List.of(
                        "All incident data is synthetic and request-local.",
                        "The Google ADK session is in-memory and discarded after this request.",
                        "Events are returned after the synchronous run; this endpoint does not stream hidden reasoning.",
                        "Only the evidence agent has read-only evidence access; neither agent can execute remediation.",
                        "Correctness is checked against this generated case only; it is not a general model-accuracy claim."
                )
        );
    }

    private AdkAgentTurnResponse rejectedDiagnosisReceipt(
            GeneratedCase generated,
            KnowledgeRagSafetyGate.Decision safety,
            AdkAgentRuntime.RunResult run,
            AdkAgentRuntime.TrajectoryValidation trajectory,
            Instant startedAt
    ) {
        Instant completedAt = clock.instant();
        long latencyMs = Math.max(
                0,
                Duration.between(startedAt, completedAt).toMillis()
        );
        List<LiveToolEvent> toolEvents = run.toolExecutions().stream()
                .map(this::toolEvent)
                .toList();
        int embeddingCallCount = embeddingCalls(toolEvents);
        int providerCallCount = run.modelCallCount() + embeddingCallCount;
        ModelTokenUsage usage = aggregateUsage(run.events().stream()
                .map(event -> event.usageMetadata()
                        .map(runtimeUsage())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList());
        ModelCostEstimate cost = costEstimator.estimate(ai.modelId(), usage);

        return new AdkAgentTurnResponse(
                AdkAgentTurnResponse.CONTRACT_VERSION,
                UUID.randomUUID().toString(),
                run.sessionId(),
                run.turnId(),
                AdkAgentTurnResponse.MODE,
                AdkAgentTurnResponse.TRUTH_LABEL,
                "verification_failed",
                providerRoute(providerCallCount),
                generated.scenario(),
                safety(safety),
                runtimeProvenance(true),
                workflowReceipt(trajectory),
                runtime.projectEvents(run.events(), false),
                toolEvents,
                null,
                null,
                null,
                new VerificationEvent(
                        "deterministic_java_contract_gate",
                        completedAt,
                        false,
                        false,
                        false,
                        trajectory.agentSequenceValid(),
                        trajectory.evidenceHandoffValid(),
                        trajectory.toolBoundaryValid()
                                && trajectory.transferBoundaryValid(),
                        trajectory.finalAuthorValid(),
                        false,
                        "Java rejected the model response contract. Citation and factual checks were not run, and no answer was released."
                ),
                new ControlReceipt(
                        run.modelCallCount(),
                        run.toolInvocationCount(),
                        toolEvents.size(),
                        embeddingCallCount,
                        false,
                        false,
                        true,
                        List.of(AdkAgentRuntime.TOOL_NAME),
                        usage,
                        cost.estimatedUsd(),
                        cost.basis(),
                        latencyMs
                ),
                List.of(
                        "All incident data is synthetic and request-local.",
                        "The Google ADK session is in-memory and discarded after this request.",
                        "The rejected model response is never returned to the client.",
                        "Citation and factual checks were not run because the Diagnosis contract failed first.",
                        "Only read-only evidence operations were available; no remediation action was executed."
                )
        );
    }

    private AdkAgentTurnResponse blocked(
            KnowledgeRagSafetyGate.Decision safety
    ) {
        return new AdkAgentTurnResponse(
                AdkAgentTurnResponse.CONTRACT_VERSION,
                UUID.randomUUID().toString(),
                null,
                UUID.randomUUID().toString(),
                AdkAgentTurnResponse.MODE,
                AdkAgentTurnResponse.TRUTH_LABEL,
                "blocked_before_ai",
                null,
                null,
                safety(safety),
                runtimeProvenance(false),
                null,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                new ControlReceipt(
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        true,
                        List.of(),
                        null,
                        null,
                        "No model or embedding call was made.",
                        0
                ),
                List.of("The request was stopped before Google ADK, Gemini, tools, or embeddings ran.")
        );
    }

    private GoogleGenAiProviderRoute providerRoute(int providerCalls) {
        return providerCalls > 0
                ? GoogleGenAiProviderRoute.from(ai)
                : null;
    }

    private RuntimeProvenance runtimeProvenance(boolean invoked) {
        return new RuntimeProvenance(
                invoked ? "google_adk" : "not_invoked",
                invoked ? AdkAgentRuntime.frameworkVersion() : "not_invoked",
                AdkAgentRuntime.AGENT_NAME,
                ai.modelId(),
                ADK_PROMPT_VERSION,
                invoked ? AdkAgentRuntime.SESSION_SERVICE : "none",
                invoked ? DELIVERY : "blocked_before_provider",
                invoked ? "google_adk_runner" : "java_safety_gate",
                invoked,
                false
        );
    }

    private String jsonEnvelope(String text) {
        if (text == null) {
            return null;
        }
        String candidate = text.strip();
        if (!candidate.startsWith("```") || !candidate.endsWith("```")) {
            return candidate;
        }
        int firstLine = candidate.indexOf('\n');
        if (firstLine < 0) {
            return candidate;
        }
        return candidate.substring(firstLine + 1, candidate.length() - 3)
                .strip();
    }

    private SafetyDecision safety(KnowledgeRagSafetyGate.Decision decision) {
        return new SafetyDecision(
                decision.allowed() ? "allowed" : "blocked",
                decision.reasonCode().name().toLowerCase(java.util.Locale.ROOT),
                decision.summarySv(),
                decision.summaryEn()
        );
    }

    private LiveToolEvent toolEvent(ToolExecution execution) {
        return new LiveToolEvent(
                execution.callId(),
                1,
                execution.toolName(),
                execution.arguments(),
                execution.safeSummary(),
                execution.evidence(),
                execution.runbookRetrieval()
        );
    }

    private VerificationEvent verificationEvent(
            CompletedInvestigationVerification checked,
            AdkAgentRuntime.TrajectoryValidation trajectory,
            Instant executedAt,
            boolean answerReleased
    ) {
        return new VerificationEvent(
                "deterministic_java_verifier",
                executedAt,
                checked.report().diagnosisSchemaPass(),
                checked.report().citationValidity().valid(),
                factualResultMatches(checked),
                trajectory.agentSequenceValid(),
                trajectory.evidenceHandoffValid(),
                trajectory.toolBoundaryValid()
                        && trajectory.transferBoundaryValid(),
                trajectory.finalAuthorValid(),
                answerReleased,
                answerReleased
                        ? "Java accepted the agent order, function-response handoff, tool boundary, schema, direct citation support, and factual match against the synthetic case."
                        : "Java withheld the diagnosis because a workflow or evidence verification rule failed."
        );
    }

    private WorkflowReceipt workflowReceipt(
            AdkAgentRuntime.TrajectoryValidation trajectory
    ) {
        return new WorkflowReceipt(
                trajectory.workflowType(),
                trajectory.expectedAgentOrder(),
                trajectory.observedAgentOrder(),
                trajectory.evidenceHandoff(),
                trajectory.finalResponseAuthor(),
                trajectory.completedInOrder()
        );
    }

    private RuntimeException translateAdkFailure(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ModelProviderException provider) {
                return provider;
            }
            if (cause instanceof LiveInvestigationException live) {
                return live;
            }
            if (cause instanceof ApiException api) {
                ModelProviderFailure failure = api.code() == 429
                        ? ModelProviderFailure.RATE_LIMITED
                        : ModelProviderFailure.UPSTREAM;
                return new ModelProviderException(
                        failure,
                        "Gemini provider request failed during the ADK run",
                        exception
                );
            }
            if (cause instanceof java.util.concurrent.TimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return new ModelProviderException(
                        ModelProviderFailure.TIMEOUT,
                        "Gemini timed out during the ADK run",
                        exception
                );
            }
            cause = cause.getCause();
        }
        return new ModelProviderException(
                ModelProviderFailure.UPSTREAM,
                "Google ADK could not complete the bounded run",
                exception
        );
    }

    private boolean factualResultMatches(
            CompletedInvestigationVerification checked
    ) {
        if (checked.comparison().expectedStatus() == DiagnosisStatus.DIAGNOSED) {
            return checked.comparison().rootCauseCorrect()
                    && checked.comparison().affectedServiceCorrect();
        }
        return checked.comparison().abstentionCorrect();
    }

    private boolean everyCitationDirectlySupported(
            CompletedInvestigationVerification checked
    ) {
        if (!checked.report().evidencePrecision().applicable()) {
            return true;
        }
        return checked.report().evidencePrecision().citationSupport().stream()
                .allMatch(result -> result.supported());
    }

    private int embeddingCalls(List<LiveToolEvent> events) {
        return (int) events.stream()
                .map(LiveToolEvent::runbookRetrieval)
                .filter(Objects::nonNull)
                .map(metadata -> metadata.queryEmbedding())
                .filter(Objects::nonNull)
                .count();
    }

    private Function<com.google.genai.types.GenerateContentResponseUsageMetadata,
            ModelTokenUsage> runtimeUsage() {
        return usage -> {
            Integer input = usage.promptTokenCount().orElse(null);
            Integer cached = usage.cachedContentTokenCount().orElse(null);
            Integer uncached = input == null || cached == null
                    ? null
                    : Math.max(0, input - cached);
            Integer candidate = usage.candidatesTokenCount().orElse(null);
            Integer thinking = usage.thoughtsTokenCount().orElse(null);
            Integer output = candidate == null
                    ? null
                    : candidate + (thinking == null ? 0 : thinking);
            return new ModelTokenUsage(
                    input,
                    cached,
                    uncached,
                    candidate,
                    thinking,
                    output,
                    usage.toolUsePromptTokenCount().orElse(null),
                    usage.totalTokenCount().orElse(null)
            );
        };
    }

    private ModelTokenUsage aggregateUsage(List<ModelTokenUsage> usages) {
        if (usages.isEmpty()) {
            return null;
        }
        return new ModelTokenUsage(
                sumWhenComplete(usages, ModelTokenUsage::inputTokens),
                sumWhenReported(usages, ModelTokenUsage::cachedInputTokens),
                sumWhenComplete(usages, ModelTokenUsage::uncachedInputTokens),
                sumWhenComplete(usages, ModelTokenUsage::candidateOutputTokens),
                sumWhenComplete(usages, ModelTokenUsage::thinkingOutputTokens),
                sumWhenComplete(usages, ModelTokenUsage::outputTokens),
                sumWhenComplete(usages, ModelTokenUsage::toolUsePromptTokens),
                sumWhenComplete(usages, ModelTokenUsage::totalTokens)
        );
    }

    private Integer sumWhenComplete(
            List<ModelTokenUsage> usages,
            Function<ModelTokenUsage, Integer> value
    ) {
        if (usages.stream().map(value).anyMatch(Objects::isNull)) {
            return null;
        }
        return usages.stream().map(value).mapToInt(Integer::intValue).sum();
    }

    private Integer sumWhenReported(
            List<ModelTokenUsage> usages,
            Function<ModelTokenUsage, Integer> value
    ) {
        List<Integer> reported = usages.stream()
                .map(value)
                .filter(Objects::nonNull)
                .toList();
        return reported.isEmpty()
                ? null
                : reported.stream().mapToInt(Integer::intValue).sum();
    }
}
