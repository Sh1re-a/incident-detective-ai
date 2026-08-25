package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(
        oneOf = {
                MetricEvidence.class,
                LogEvidence.class,
                TraceEvidence.class,
                RunbookEvidence.class
        },
        discriminatorProperty = "evidence_type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "metric", schema = MetricEvidence.class),
                @DiscriminatorMapping(value = "log", schema = LogEvidence.class),
                @DiscriminatorMapping(value = "trace", schema = TraceEvidence.class),
                @DiscriminatorMapping(value = "runbook", schema = RunbookEvidence.class)
        }
)
public sealed interface Evidence
        permits MetricEvidence, LogEvidence, TraceEvidence, RunbookEvidence {

    String evidenceId();

    String scenarioId();

    String displaySummary();

    String sourceRef();

    EvidenceType type();
}
