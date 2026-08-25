package dev.shirwac.incidentdetective.investigation.tools;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record SearchLogsArguments(
        @NotNull
        @Size(max = 8)
        List<
                @NotBlank
                @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
                String
                > services,
        @NotNull
        @Size(max = 8)
        List<
                @NotBlank
                @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,31}$")
                String
                > levels,
        @NotBlank
        @Size(max = 160)
        String query,
        @NotNull Instant start,
        @NotNull Instant end
) {
    public SearchLogsArguments {
        services = services == null ? null : List.copyOf(services);
        levels = levels == null ? null : List.copyOf(levels);
    }
}
