package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonTypeName("runbook")
public record RunbookEvidence(
        @NotBlank String evidenceId,
        @NotBlank String scenarioId,
        @NotBlank String displaySummary,
        @NotBlank String sourceRef,
        @NotNull @Valid RunbookContent content
) implements Evidence {

    @Override
    public EvidenceType type() {
        return EvidenceType.RUNBOOK;
    }

    public record RunbookContent(
            @NotBlank String documentId,
            @NotBlank String chunkId,
            @NotBlank String documentVersion,
            @NotBlank String text
    ) {
    }
}
