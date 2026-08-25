package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record GetTraceResult(
        @NotBlank String traceId,
        boolean found,
        @Valid TraceEvidence evidence
) {
    public GetTraceResult {
        if (found != (evidence != null)) {
            throw new IllegalArgumentException(
                    "found must match whether trace evidence is present"
            );
        }
        if (evidence != null
                && !traceId.equals(evidence.content().traceId())) {
            throw new IllegalArgumentException(
                    "returned evidence must match the requested trace ID"
            );
        }
    }

    static GetTraceResult found(String traceId, TraceEvidence evidence) {
        return new GetTraceResult(traceId, true, evidence);
    }

    static GetTraceResult missing(String traceId) {
        return new GetTraceResult(traceId, false, null);
    }
}
