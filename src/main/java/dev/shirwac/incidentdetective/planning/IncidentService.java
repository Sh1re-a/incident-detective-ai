package dev.shirwac.incidentdetective.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Allowlisted services represented by the synthetic Nordly world. */
public enum IncidentService {
    STOREFRONT("storefront", "STOREFRONT"),
    CHECKOUT_API("checkout_api", "CHECKOUT_API"),
    PAYMENT_ADAPTER("payment_adapter", "PAYMENT_ADAPTER"),
    CATALOG_SERVICE("catalog_service", "CATALOG_SERVICE"),
    ORDER_SERVICE("order_service", "ORDER_SERVICE"),
    ORDER_EVENT_CONSUMER("order_event_consumer", "ORDER_EVENT_CONSUMER"),
    INVENTORY_SERVICE("inventory_service", "INVENTORY_SERVICE");

    private final String wireValue;
    private final String serviceCode;

    IncidentService(String wireValue, String serviceCode) {
        this.wireValue = wireValue;
        this.serviceCode = serviceCode;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public String serviceCode() {
        return serviceCode;
    }

    @JsonCreator
    public static IncidentService fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(service -> service.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown incident service: " + value
                ));
    }
}
