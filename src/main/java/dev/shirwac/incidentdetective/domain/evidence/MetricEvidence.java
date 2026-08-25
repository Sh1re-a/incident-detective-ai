package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

@JsonTypeName("metric")
public record MetricEvidence(
        @NotBlank String evidenceId,
        @NotBlank String scenarioId,
        @NotNull Instant observedAt,
        @NotBlank String displaySummary,
        @NotBlank String sourceRef,
        @NotNull @Valid MetricContent content
) implements Evidence {

    @Override
    public EvidenceType type() {
        return EvidenceType.METRIC;
    }

    public record MetricContent(
            @NotBlank String metricName,
            double value,
            @NotBlank String unit,
            @NotNull Map<String, String> labels
    ) {
        public MetricContent {
            labels = labels == null ? Map.of() : Map.copyOf(labels);
        }
    }
}
