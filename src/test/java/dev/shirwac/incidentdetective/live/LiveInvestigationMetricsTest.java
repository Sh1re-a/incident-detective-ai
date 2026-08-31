package dev.shirwac.incidentdetective.live;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiveInvestigationMetricsTest {

    @Test
    void recordsResultMetricsWithoutHighCardinalityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LiveInvestigationMetrics metrics = new LiveInvestigationMetrics(registry);

        metrics.recordResult(LiveRunStatus.COMPLETED, 81, 4, 3);

        assertEquals(1.0, registry.get(LiveInvestigationMetrics.RUNS)
                .tag("outcome", "success").counter().count());
        assertEquals(81.0, registry.get(LiveInvestigationMetrics.DURATION)
                .tag("outcome", "success").timer()
                .totalTime(TimeUnit.MILLISECONDS));
        assertEquals(4.0, registry.get(LiveInvestigationMetrics.TOOL_CALLS)
                .tag("outcome", "success").counter().count());
        assertEquals(3.0, registry.get(LiveInvestigationMetrics.MODEL_CALLS)
                .tag("outcome", "success").counter().count());
        assertNull(registry.find(LiveInvestigationMetrics.RUNS)
                .tag("scenario_id", "private-scenario").counter());
        assertNull(registry.find(LiveInvestigationMetrics.RUNS)
                .tag("run_id", "private-run").counter());
    }

    @Test
    void treatsVerificationFailureAsAFailedRun() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LiveInvestigationMetrics metrics = new LiveInvestigationMetrics(registry);

        metrics.recordResult(LiveRunStatus.VERIFICATION_FAILED, 17, 2, 2);

        assertEquals(1.0, registry.get(LiveInvestigationMetrics.RUNS)
                .tag("outcome", "failure").counter().count());
        assertEquals(2.0, registry.get(LiveInvestigationMetrics.TOOL_CALLS)
                .tag("outcome", "failure").counter().count());
        assertEquals(2.0, registry.get(LiveInvestigationMetrics.MODEL_CALLS)
                .tag("outcome", "failure").counter().count());
    }
}
