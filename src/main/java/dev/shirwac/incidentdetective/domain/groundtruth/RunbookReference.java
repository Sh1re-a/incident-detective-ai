package dev.shirwac.incidentdetective.domain.groundtruth;

import jakarta.validation.constraints.NotBlank;

public record RunbookReference(
        @NotBlank String documentId,
        @NotBlank String chunkId,
        @NotBlank String documentVersion
) {
}
