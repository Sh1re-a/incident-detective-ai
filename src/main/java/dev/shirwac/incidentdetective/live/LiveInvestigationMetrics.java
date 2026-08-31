package dev.shirwac.incidentdetective.live;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Low-cardinality runtime metrics for live investigations. No scenario IDs,
 * run IDs, provider response IDs, prompts, exception messages, or secrets are
 * used as tags.
 */
@Component
public final class LiveInvestigationMetrics {

    static final String RUNS = "incident.detective.live.runs";
    static final String DURATION = "incident.detective.live.duration";
    static final String TOOL_CALLS = "incident.detective.live.tool.calls";
    static final String MODEL_CALLS = "incident.detective.live.model.calls";

    private final MeterRegistry registry;

    public LiveInvestigationMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(
                registry,
                "registry must not be null"
        );
    }

    public void recordResult(
            LiveRunStatus status,
            long durationMs,
            int toolCallCount,
            int modelCallCount
    ) {
        Objects.requireNonNull(status, "status must not be null");
        String outcome = status == LiveRunStatus.COMPLETED
                ? "success"
                : "failure";
        record(outcome, durationMs, toolCallCount, modelCallCount);
    }

    public void recordFailure(long durationMs) {
        record("failure", durationMs, 0, 0);
    }

    private void record(
            String outcome,
            long durationMs,
            int toolCallCount,
            int modelCallCount
    ) {
        if (durationMs < 0 || toolCallCount < 0 || modelCallCount < 0) {
            throw new IllegalArgumentException(
                    "duration and call counts must not be negative"
            );
        }
        Counter.builder(RUNS)
                .description("Live investigation runs")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder(DURATION)
                .description("Live investigation duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(Duration.ofMillis(durationMs));
        Counter.builder(TOOL_CALLS)
                .description("Tool calls observed in live investigations")
                .tag("outcome", outcome)
                .register(registry)
                .increment(toolCallCount);
        Counter.builder(MODEL_CALLS)
                .description("Model calls observed in live investigations")
                .tag("outcome", outcome)
                .register(registry)
                .increment(modelCallCount);
    }
}
