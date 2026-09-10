package dev.shirwac.incidentdetective.observability;

import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalMetadata;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveInvestigationException;
import dev.shirwac.incidentdetective.live.LiveRunStatus;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Manual spans for the bounded investigation workflow.
 *
 * <p>The public methods intentionally accept only server-controlled identifiers,
 * enums, counts and aggregate outcomes. There is no generic attribute method:
 * prompts, provider payloads, evidence content, GroundTruth, tool arguments and
 * chain-of-thought cannot be attached by callers.</p>
 */
@Component
public final class InvestigationTelemetry {

    static final String INSTRUMENTATION_SCOPE =
            "dev.shirwac.incidentdetective.investigation";
    static final String SPAN_INVESTIGATION = "incident.investigation";
    static final String SPAN_COLLECT = "incident.collect";
    static final String SPAN_TOOL = "incident.tool";
    static final String SPAN_RETRIEVAL = "incident.retrieval";
    static final String SPAN_SYNTHESIZE = "incident.synthesize";
    static final String SPAN_VERIFY = "incident.verify";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:/-]{0,79}"
    );

    private static final AttributeKey<String> WORKFLOW_NAME = stringKey(
            "incident.workflow.name"
    );
    private static final AttributeKey<String> SCENARIO_ID = stringKey(
            "incident.scenario.id"
    );
    private static final AttributeKey<String> RUN_ID = stringKey(
            "incident.run.id"
    );
    private static final AttributeKey<String> RUN_MODE = stringKey(
            "incident.run.mode"
    );
    private static final AttributeKey<Boolean> SYNTHETIC_DATA = booleanKey(
            "incident.data.synthetic"
    );
    private static final AttributeKey<Boolean> GENERATED_CASE = booleanKey(
            "incident.case.generated"
    );
    private static final AttributeKey<Boolean> WRITE_ENABLED = booleanKey(
            "incident.capability.write_enabled"
    );
    private static final AttributeKey<String> PHASE = stringKey(
            "incident.phase"
    );
    private static final AttributeKey<Long> COLLECTION_ROUND = longKey(
            "incident.collection.round"
    );
    private static final AttributeKey<Long> ALLOWED_TOOL_COUNT = longKey(
            "incident.tool.allowed.count"
    );
    private static final AttributeKey<Long> REQUESTED_TOOL_COUNT = longKey(
            "incident.tool.requested.count"
    );
    private static final AttributeKey<Long> EVIDENCE_COUNT = longKey(
            "incident.evidence.count"
    );
    private static final AttributeKey<String> RETRIEVAL_BACKEND = stringKey(
            "incident.retrieval.backend"
    );
    private static final AttributeKey<Long> RETRIEVAL_MATCH_COUNT = longKey(
            "incident.retrieval.match.count"
    );
    private static final AttributeKey<Boolean> VECTOR_DATABASE_ACTIVE = booleanKey(
            "incident.retrieval.vector_database.active"
    );
    private static final AttributeKey<Long> EMBEDDING_DIMENSIONS = longKey(
            "incident.embedding.dimension.count"
    );
    private static final AttributeKey<Long> EMBEDDING_LATENCY_MS = longKey(
            "incident.embedding.latency_ms"
    );
    private static final AttributeKey<Long> MODEL_LATENCY_MS = longKey(
            "incident.model.latency_ms"
    );
    private static final AttributeKey<String> DIAGNOSIS_STATUS = stringKey(
            "incident.diagnosis.status"
    );
    private static final AttributeKey<Boolean> VERIFICATION_PASSED = booleanKey(
            "incident.verification.passed"
    );
    private static final AttributeKey<Boolean> DIAGNOSIS_SCHEMA_PASSED = booleanKey(
            "incident.verification.schema_pass"
    );
    private static final AttributeKey<Boolean> CITATIONS_PASSED = booleanKey(
            "incident.verification.citation_pass"
    );
    private static final AttributeKey<Long> HARD_ERROR_COUNT = longKey(
            "incident.verification.hard_error.count"
    );
    private static final AttributeKey<Double> CLAIM_COVERAGE = doubleKey(
            "incident.verification.claim_coverage.score"
    );
    private static final AttributeKey<String> OUTCOME = stringKey(
            "incident.outcome"
    );
    private static final AttributeKey<Long> TOOL_CALL_COUNT = longKey(
            "incident.tool_call.count"
    );
    private static final AttributeKey<Long> MODEL_CALL_COUNT = longKey(
            "incident.model_call.count"
    );
    private static final AttributeKey<Long> MAX_COLLECTION_ROUNDS = longKey(
            "incident.limits.max_collection_rounds"
    );
    private static final AttributeKey<Long> MAX_TOOL_CALLS = longKey(
            "incident.limits.max_tool_calls"
    );
    private static final AttributeKey<Long> HARD_DEADLINE_MS = longKey(
            "incident.limits.hard_deadline_ms"
    );
    private static final AttributeKey<String> GEN_AI_OPERATION = stringKey(
            "gen_ai.operation.name"
    );
    private static final AttributeKey<String> GEN_AI_PROVIDER = stringKey(
            "gen_ai.provider.name"
    );
    private static final AttributeKey<String> GEN_AI_REQUEST_MODEL = stringKey(
            "gen_ai.request.model"
    );
    private static final AttributeKey<String> GEN_AI_TOOL_NAME = stringKey(
            "gen_ai.tool.name"
    );
    private static final AttributeKey<String> GEN_AI_TOOL_TYPE = stringKey(
            "gen_ai.tool.type"
    );
    private static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = longKey(
            "gen_ai.usage.input_tokens"
    );
    private static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = longKey(
            "gen_ai.usage.output_tokens"
    );
    private static final AttributeKey<String> ERROR_TYPE = stringKey(
            "error.type"
    );

    static final Set<String> ATTRIBUTE_ALLOWLIST = Set.of(
            WORKFLOW_NAME.getKey(),
            SCENARIO_ID.getKey(),
            RUN_ID.getKey(),
            RUN_MODE.getKey(),
            SYNTHETIC_DATA.getKey(),
            GENERATED_CASE.getKey(),
            WRITE_ENABLED.getKey(),
            PHASE.getKey(),
            COLLECTION_ROUND.getKey(),
            ALLOWED_TOOL_COUNT.getKey(),
            REQUESTED_TOOL_COUNT.getKey(),
            EVIDENCE_COUNT.getKey(),
            RETRIEVAL_BACKEND.getKey(),
            RETRIEVAL_MATCH_COUNT.getKey(),
            VECTOR_DATABASE_ACTIVE.getKey(),
            EMBEDDING_DIMENSIONS.getKey(),
            EMBEDDING_LATENCY_MS.getKey(),
            MODEL_LATENCY_MS.getKey(),
            DIAGNOSIS_STATUS.getKey(),
            VERIFICATION_PASSED.getKey(),
            DIAGNOSIS_SCHEMA_PASSED.getKey(),
            CITATIONS_PASSED.getKey(),
            HARD_ERROR_COUNT.getKey(),
            CLAIM_COVERAGE.getKey(),
            OUTCOME.getKey(),
            TOOL_CALL_COUNT.getKey(),
            MODEL_CALL_COUNT.getKey(),
            MAX_COLLECTION_ROUNDS.getKey(),
            MAX_TOOL_CALLS.getKey(),
            HARD_DEADLINE_MS.getKey(),
            GEN_AI_OPERATION.getKey(),
            GEN_AI_PROVIDER.getKey(),
            GEN_AI_REQUEST_MODEL.getKey(),
            GEN_AI_TOOL_NAME.getKey(),
            GEN_AI_TOOL_TYPE.getKey(),
            GEN_AI_INPUT_TOKENS.getKey(),
            GEN_AI_OUTPUT_TOKENS.getKey(),
            ERROR_TYPE.getKey()
    );

    private final Tracer tracer;

    @Autowired
    public InvestigationTelemetry(
            ObjectProvider<OpenTelemetry> openTelemetryProvider
    ) {
        this(openTelemetryProvider.getIfAvailable(
                GlobalOpenTelemetry::getOrNoop
        ));
    }

    InvestigationTelemetry(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
    }

    public InvestigationSpan startInvestigation(
            String scenarioId,
            boolean generated,
            int maxCollectionRounds,
            int maxToolCalls,
            long hardDeadlineMs
    ) {
        Span span = tracer.spanBuilder(SPAN_INVESTIGATION)
                .setAttribute(WORKFLOW_NAME, "incident_detective")
                .setAttribute(SCENARIO_ID, safeIdentifier(
                        scenarioId,
                        "synthetic_scenario"
                ))
                .setAttribute(RUN_MODE, "live_ai")
                .setAttribute(SYNTHETIC_DATA, true)
                .setAttribute(GENERATED_CASE, generated)
                .setAttribute(WRITE_ENABLED, false)
                .setAttribute(MAX_COLLECTION_ROUNDS, maxCollectionRounds)
                .setAttribute(MAX_TOOL_CALLS, maxToolCalls)
                .setAttribute(HARD_DEADLINE_MS, hardDeadlineMs)
                .startSpan();
        return new InvestigationSpan(span);
    }

    public CollectionSpan startCollection(
            int round,
            int allowedToolCount,
            String modelId
    ) {
        Span span = tracer.spanBuilder(SPAN_COLLECT)
                .setAttribute(PHASE, "collect")
                .setAttribute(COLLECTION_ROUND, round)
                .setAttribute(ALLOWED_TOOL_COUNT, allowedToolCount)
                .setAttribute(GEN_AI_OPERATION, "generate_content")
                .setAttribute(GEN_AI_PROVIDER, "gcp.gen_ai")
                .setAttribute(GEN_AI_REQUEST_MODEL, safeIdentifier(
                        modelId,
                        "configured_model"
                ))
                .startSpan();
        return new CollectionSpan(span);
    }

    public ToolExecution executeTool(
            int round,
            ToolName toolName,
            Supplier<ToolExecution> operation
    ) {
        Span span = tracer.spanBuilder(SPAN_TOOL)
                .setAttribute(PHASE, "tool")
                .setAttribute(COLLECTION_ROUND, round)
                .setAttribute(GEN_AI_OPERATION, "execute_tool")
                .setAttribute(GEN_AI_TOOL_NAME, toolName.wireValue())
                .setAttribute(GEN_AI_TOOL_TYPE, "function")
                .startSpan();
        return trace(span, () -> {
            ToolExecution execution;
            if (toolName == ToolName.RETRIEVE_RUNBOOKS) {
                execution = executeRetrieval(operation);
            } else {
                execution = operation.get();
            }
            span.setAttribute(EVIDENCE_COUNT, execution.evidence().size());
            return execution;
        }, ignored -> { });
    }

    public SynthesisModelResult synthesize(
            String modelId,
            int evidenceCount,
            Supplier<SynthesisModelResult> operation
    ) {
        Span span = tracer.spanBuilder(SPAN_SYNTHESIZE)
                .setAttribute(PHASE, "synthesize")
                .setAttribute(GEN_AI_OPERATION, "generate_content")
                .setAttribute(GEN_AI_PROVIDER, "gcp.gen_ai")
                .setAttribute(GEN_AI_REQUEST_MODEL, safeIdentifier(
                        modelId,
                        "configured_model"
                ))
                .setAttribute(EVIDENCE_COUNT, evidenceCount)
                .startSpan();
        return trace(span, operation, result -> {
            setModelMetadata(span, result.metadata());
            span.setAttribute(
                    DIAGNOSIS_STATUS,
                    result.diagnosis().status().wireValue()
            );
        });
    }

    public CompletedInvestigationVerification verify(
            Supplier<CompletedInvestigationVerification> operation
    ) {
        Span span = tracer.spanBuilder(SPAN_VERIFY)
                .setAttribute(PHASE, "verify")
                .startSpan();
        return trace(span, operation, result -> {
            var report = result.report();
            span.setAttribute(
                    VERIFICATION_PASSED,
                    report.hardErrors().isEmpty()
            );
            span.setAttribute(
                    DIAGNOSIS_SCHEMA_PASSED,
                    report.diagnosisSchemaPass()
            );
            span.setAttribute(
                    CITATIONS_PASSED,
                    report.citationValidity().valid()
            );
            span.setAttribute(HARD_ERROR_COUNT, report.hardErrors().size());
            if (report.claimCoverage().score() != null) {
                span.setAttribute(
                        CLAIM_COVERAGE,
                        report.claimCoverage().score()
                );
            }
        });
    }

    private ToolExecution executeRetrieval(Supplier<ToolExecution> operation) {
        Span span = tracer.spanBuilder(SPAN_RETRIEVAL)
                .setAttribute(PHASE, "retrieval")
                .setAttribute(GEN_AI_OPERATION, "retrieval")
                .startSpan();
        return trace(span, operation, execution -> {
            RunbookRetrievalMetadata metadata = execution.runbookRetrieval();
            if (metadata == null) {
                span.setAttribute(RETRIEVAL_BACKEND, "not_reported");
                span.setAttribute(RETRIEVAL_MATCH_COUNT, 0);
                span.setAttribute(VECTOR_DATABASE_ACTIVE, false);
                return;
            }
            boolean vectorDatabaseActive = RunbookRetrievalBackend
                    .PGVECTOR_EXACT_COSINE
                    .wireValue()
                    .equals(metadata.backend());
            span.setAttribute(
                    RETRIEVAL_BACKEND,
                    vectorDatabaseActive
                            ? RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE.wireValue()
                            : RunbookRetrievalBackend.DETERMINISTIC_FIXTURE.wireValue()
            );
            span.setAttribute(RETRIEVAL_MATCH_COUNT, metadata.matches().size());
            span.setAttribute(VECTOR_DATABASE_ACTIVE, vectorDatabaseActive);
            if (vectorDatabaseActive) {
                span.setAttribute(
                        EMBEDDING_DIMENSIONS,
                        metadata.embeddingProfile().dimensions()
                );
                span.setAttribute(
                        EMBEDDING_LATENCY_MS,
                        metadata.queryEmbedding().latencyMs()
                );
            }
        });
    }

    private static <T> T trace(
            Span span,
            Supplier<T> operation,
            Consumer<T> onSuccess
    ) {
        try (Scope ignored = span.makeCurrent()) {
            T result = operation.get();
            onSuccess.accept(result);
            span.setStatus(StatusCode.OK);
            return result;
        } catch (RuntimeException exception) {
            markError(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private static void setModelMetadata(
            Span span,
            ModelCallMetadata metadata
    ) {
        span.setAttribute(MODEL_LATENCY_MS, metadata.latencyMs());
        ModelTokenUsage usage = metadata.tokenUsage();
        if (usage == null) {
            return;
        }
        if (usage.inputTokens() != null) {
            span.setAttribute(GEN_AI_INPUT_TOKENS, usage.inputTokens());
        }
        if (usage.outputTokens() != null) {
            span.setAttribute(GEN_AI_OUTPUT_TOKENS, usage.outputTokens());
        }
    }

    private static void markError(Span span, RuntimeException exception) {
        span.setAttribute(ERROR_TYPE, safeErrorType(exception));
        span.setStatus(StatusCode.ERROR);
    }

    private static String safeErrorType(RuntimeException exception) {
        if (exception instanceof ModelProviderException provider) {
            return switch (provider.failure()) {
                case TIMEOUT -> "model_provider.timeout";
                case RATE_LIMITED -> "model_provider.rate_limited";
                case UPSTREAM -> "model_provider.upstream";
                case MALFORMED_RESPONSE -> "model_provider.malformed_response";
            };
        }
        if (exception instanceof LiveInvestigationException live) {
            return switch (live.failure()) {
                case CONFIRMATION_REQUIRED -> "live.confirmation_required";
                case LIVE_AI_DISABLED -> "live.disabled";
                case API_KEY_MISSING -> "live.api_key_missing";
                case DEADLINE_EXCEEDED -> "live.deadline_exceeded";
            };
        }
        if (exception instanceof IllegalArgumentException) {
            return "application.invalid_argument";
        }
        if (exception instanceof IllegalStateException) {
            return "application.state_violation";
        }
        return "application.internal_error";
    }

    private static String safeIdentifier(String value, String fallback) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches()
                ? value
                : fallback;
    }

    private static AttributeKey<String> stringKey(String key) {
        return AttributeKey.stringKey(key);
    }

    private static AttributeKey<Long> longKey(String key) {
        return AttributeKey.longKey(key);
    }

    private static AttributeKey<Double> doubleKey(String key) {
        return AttributeKey.doubleKey(key);
    }

    private static AttributeKey<Boolean> booleanKey(String key) {
        return AttributeKey.booleanKey(key);
    }

    public static final class InvestigationSpan implements AutoCloseable {

        private final Span span;
        private final Scope scope;
        private boolean closed;

        private InvestigationSpan(Span span) {
            this.span = span;
            this.scope = span.makeCurrent();
        }

        public void completed(
                String runId,
                LiveRunStatus status,
                int evidenceCount,
                int toolCallCount,
                int modelCallCount
        ) {
            span.setAttribute(RUN_ID, safeIdentifier(runId, "generated_run"));
            span.setAttribute(OUTCOME, status.wireValue());
            span.setAttribute(EVIDENCE_COUNT, evidenceCount);
            span.setAttribute(TOOL_CALL_COUNT, toolCallCount);
            span.setAttribute(MODEL_CALL_COUNT, modelCallCount);
            span.setStatus(StatusCode.OK);
        }

        public void failed(RuntimeException exception) {
            markError(span, exception);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scope.close();
            span.end();
        }
    }

    public static final class CollectionSpan implements AutoCloseable {

        private final Span span;
        private final Scope scope;
        private boolean closed;

        private CollectionSpan(Span span) {
            this.span = span;
            this.scope = span.makeCurrent();
        }

        public void modelCompleted(
                ModelCallMetadata metadata,
                int requestedToolCount
        ) {
            setModelMetadata(span, metadata);
            span.setAttribute(REQUESTED_TOOL_COUNT, requestedToolCount);
        }

        public void completed(int evidenceCount) {
            span.setAttribute(EVIDENCE_COUNT, evidenceCount);
            span.setStatus(StatusCode.OK);
        }

        public void failed(RuntimeException exception) {
            markError(span, exception);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            scope.close();
            span.end();
        }
    }
}
