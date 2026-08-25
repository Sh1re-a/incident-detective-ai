package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

@JsonTypeName("trace")
public record TraceEvidence(
        @NotBlank String evidenceId,
        @NotBlank String scenarioId,
        @NotNull Instant observedAt,
        @NotBlank String displaySummary,
        @NotBlank String sourceRef,
        @NotNull @Valid TraceContent content
) implements Evidence {

    @Override
    public EvidenceType type() {
        return EvidenceType.TRACE;
    }

    public record TraceContent(
            @NotBlank String traceId,
            @NotEmpty List<@Valid TraceSpan> spans
    ) {
        public TraceContent {
            spans = spans == null ? null : List.copyOf(spans);
        }
    }

    public record TraceSpan(
            @NotBlank String spanId,
            @NotBlank
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
            String service,
            @NotBlank String operation,
            @PositiveOrZero long durationMs,
            @NotBlank String status
    ) {
    }
}
