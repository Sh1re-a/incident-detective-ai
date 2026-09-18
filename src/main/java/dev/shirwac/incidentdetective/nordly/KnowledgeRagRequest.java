package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record KnowledgeRagRequest(
        @NotBlank
        @Size(min = 3, max = 500)
        @Schema(description = "Free-text question. Never logged by this endpoint.")
        String question,
        @NotBlank
        @Pattern(regexp = "sv|en")
        String locale,
        @Schema(
                description = "Explicit opt-in for at most one embedding call "
                        + "and one bounded synthesis call."
        )
        boolean confirmLiveAi
) {
}
