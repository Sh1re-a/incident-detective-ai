package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Bounded synthetic incident families available in the Nordly demo world. */
public enum GeneratedIncidentFamily {
    PAYMENT_TIMEOUT("payment_timeout"),
    CATALOG_CACHE_INVALIDATION("catalog_cache_invalidation"),
    ORDER_EVENT_BACKLOG("order_event_backlog"),
    ORDER_IDEMPOTENCY_FAILURE("order_idempotency_failure");

    private final String wireValue;

    GeneratedIncidentFamily(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static GeneratedIncidentFamily fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(family -> family.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown generated incident family: " + value
                ));
    }
}
