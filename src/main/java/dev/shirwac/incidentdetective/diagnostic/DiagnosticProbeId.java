package dev.shirwac.incidentdetective.diagnostic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Fixed read-only diagnostics that may run against one generated case. */
public enum DiagnosticProbeId {
    SERVICE_HEALTH("service_health"),
    DEPENDENCY_STATUS("dependency_status"),
    RELEASE_METADATA("release_metadata"),
    CONFIG_FINGERPRINT_DIFF("config_fingerprint_diff");

    private final String wireValue;

    DiagnosticProbeId(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static DiagnosticProbeId fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(probe -> probe.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown diagnostic probe: " + value
                ));
    }
}
