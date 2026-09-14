package dev.shirwac.incidentdetective.alarm;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable proof of the exact signal observation that raised an alarm. */
public record SignalAlarmReceipt(
        String alarmId,
        String ruleId,
        GeneratedIncidentFamily incidentFamily,
        String scenarioId,
        String service,
        Instant triggeredAt,
        SignalObservation signal,
        List<String> evidenceIds
) {

    public SignalAlarmReceipt {
        requireText(alarmId, "alarmId");
        requireText(ruleId, "ruleId");
        Objects.requireNonNull(
                incidentFamily,
                "incidentFamily must not be null"
        );
        requireText(scenarioId, "scenarioId");
        requireText(service, "service");
        Objects.requireNonNull(triggeredAt, "triggeredAt must not be null");
        Objects.requireNonNull(signal, "signal must not be null");
        Objects.requireNonNull(evidenceIds, "evidenceIds must not be null");
        if (evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("evidenceIds must not be empty");
        }
        if (evidenceIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException(
                    "evidenceIds must not contain blank values"
            );
        }
        if (new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
            throw new IllegalArgumentException(
                    "evidenceIds must not contain duplicates"
            );
        }
        evidenceIds = List.copyOf(evidenceIds);
    }

    public record SignalObservation(
            String name,
            Comparison comparison,
            double thresholdValue,
            double observedValue,
            String unit,
            Long lookbackSeconds
    ) {

        public SignalObservation {
            requireText(name, "signal.name");
            Objects.requireNonNull(
                    comparison,
                    "signal.comparison must not be null"
            );
            if (!Double.isFinite(thresholdValue) || thresholdValue < 0) {
                throw new IllegalArgumentException(
                        "signal.thresholdValue must be a non-negative finite number"
                );
            }
            if (!Double.isFinite(observedValue)) {
                throw new IllegalArgumentException(
                        "signal.observedValue must be finite"
                );
            }
            if (!comparison.matches(observedValue, thresholdValue)) {
                throw new IllegalArgumentException(
                        "signal.observedValue must satisfy the alarm threshold"
                );
            }
            requireText(unit, "signal.unit");
            if (lookbackSeconds != null && lookbackSeconds < 1) {
                throw new IllegalArgumentException(
                        "signal.lookbackSeconds must be positive when present"
                );
            }
        }
    }

    public enum Comparison {
        AT_LEAST("at_least");

        private final String wireValue;

        Comparison(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }

        boolean matches(double observedValue, double thresholdValue) {
            return observedValue >= thresholdValue;
        }

        @JsonCreator
        public static Comparison fromWireValue(String value) {
            return Arrays.stream(values())
                    .filter(comparison -> comparison.wireValue.equals(value))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown alarm comparison: " + value
                    ));
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
