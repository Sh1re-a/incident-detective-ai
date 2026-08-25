package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "evidence_type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MetricEvidence.class, name = "metric"),
        @JsonSubTypes.Type(value = LogEvidence.class, name = "log"),
        @JsonSubTypes.Type(value = TraceEvidence.class, name = "trace"),
        @JsonSubTypes.Type(value = RunbookEvidence.class, name = "runbook")
})
public sealed interface Evidence
        permits MetricEvidence, LogEvidence, TraceEvidence, RunbookEvidence {

    String evidenceId();

    String scenarioId();

    String displaySummary();

    String sourceRef();

    EvidenceType type();
}
