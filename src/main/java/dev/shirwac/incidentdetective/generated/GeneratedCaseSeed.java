package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/** The resolved seed and whether it came from the caller or this server. */
public record GeneratedCaseSeed(
        long value,
        Origin origin
) {
    public GeneratedCaseSeed {
        Objects.requireNonNull(origin, "origin must not be null");
    }

    public static GeneratedCaseSeed explicit(long value) {
        return new GeneratedCaseSeed(value, Origin.EXPLICIT);
    }

    public static GeneratedCaseSeed serverGenerated(long value) {
        return new GeneratedCaseSeed(value, Origin.SERVER_GENERATED);
    }

    public enum Origin {
        EXPLICIT("explicit"),
        SERVER_GENERATED("server_generated");

        private final String wireValue;

        Origin(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }
}
