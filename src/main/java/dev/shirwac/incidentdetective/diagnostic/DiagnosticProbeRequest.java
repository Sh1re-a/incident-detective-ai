package dev.shirwac.incidentdetective.diagnostic;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bounded input for one read-only diagnostic.
 *
 * <p>There is deliberately no command, shell, path, host, or URL field. A
 * caller can select only one allowlisted probe for the exact generated case
 * it already holds.</p>
 */
public record DiagnosticProbeRequest(
        String scenarioId,
        DiagnosticProbeId probeId
) {
    public static final int MAX_SCENARIO_ID_LENGTH = 128;

    private static final Pattern SCENARIO_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9-]{1,127}$");

    public DiagnosticProbeRequest {
        if (scenarioId == null || scenarioId.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        scenarioId = scenarioId.strip();
        if (scenarioId.length() > MAX_SCENARIO_ID_LENGTH
                || !SCENARIO_ID_PATTERN.matcher(scenarioId).matches()) {
            throw new IllegalArgumentException(
                    "scenarioId must use 2-128 lowercase letters, numbers, and hyphens"
            );
        }
        Objects.requireNonNull(probeId, "probeId must not be null");
    }
}
