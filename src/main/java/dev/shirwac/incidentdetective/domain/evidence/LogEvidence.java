package dev.shirwac.incidentdetective.domain.evidence;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;

@JsonTypeName("log")
public record LogEvidence(
        @NotBlank String evidenceId,
        @NotBlank String scenarioId,
        @NotNull Instant observedAt,
        @NotBlank String displaySummary,
        @NotBlank String sourceRef,
        @NotNull @Valid LogContent content
) implements Evidence {

    @Override
    public EvidenceType type() {
        return EvidenceType.LOG;
    }

    public record LogContent(
            @NotBlank
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
            String service,
            @NotBlank String level,
            @NotBlank String message,
            @NotNull Map<String, String> attributes
    ) {
        public LogContent {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
