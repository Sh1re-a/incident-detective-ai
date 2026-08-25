package dev.shirwac.incidentdetective.domain.diagnosis;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClaimValueTaxonomy {

    private static final Map<ClaimCode, List<String>> VALUES = values();

    private ClaimValueTaxonomy() {
    }

    public static boolean contains(ClaimCode claimCode, String claimValueCode) {
        if (claimCode == null || claimValueCode == null) {
            return false;
        }
        return VALUES.getOrDefault(claimCode, List.of()).contains(claimValueCode);
    }

    public static Map<String, List<String>> wireValues() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (ClaimCode claimCode : ClaimCode.values()) {
            values.put(claimCode.wireValue(), VALUES.getOrDefault(claimCode, List.of()));
        }
        return Collections.unmodifiableMap(values);
    }

    public static Set<String> allValues() {
        Set<String> values = new LinkedHashSet<>();
        VALUES.values().forEach(values::addAll);
        return Collections.unmodifiableSet(values);
    }

    private static Map<ClaimCode, List<String>> values() {
        Map<ClaimCode, List<String>> values = new EnumMap<>(ClaimCode.class);
        values.put(ClaimCode.ROOT_CAUSE, List.of(
                "PAYMENT_TIMEOUT_CONFIG",
                "INVENTORY_SCHEMA_MISMATCH"
        ));
        values.put(ClaimCode.AFFECTED_SERVICE, List.of(
                "PAYMENT_ADAPTER",
                "INVENTORY_SERVICE"
        ));
        values.put(ClaimCode.TRIGGER, List.of(
                "PAYMENT_ADAPTER_RELEASE",
                "INVENTORY_SERVICE_RELEASE"
        ));
        values.put(ClaimCode.CUSTOMER_IMPACT, List.of(
                "CHECKOUT_PAYMENT_FAILURES",
                "MULTI_ITEM_CHECKOUT_FAILURES"
        ));
        values.put(ClaimCode.OBSERVED_SYMPTOM, List.of(
                "PAYMENT_LATENCY_SPIKE",
                "INVENTORY_CONTRACT_VALIDATION_ERRORS"
        ));
        values.put(ClaimCode.MISSING_EVIDENCE, List.of(
                "PAYMENT_PROVIDER_RESPONSE"
        ));
        return Collections.unmodifiableMap(values);
    }
}
