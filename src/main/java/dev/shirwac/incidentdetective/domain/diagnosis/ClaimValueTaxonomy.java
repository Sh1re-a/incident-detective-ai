package dev.shirwac.incidentdetective.domain.diagnosis;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClaimValueTaxonomy {

    public static final String VERSION = "claim-taxonomy-v2";

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
                "INVENTORY_SCHEMA_MISMATCH",
                "CHECKOUT_DB_POOL_EXHAUSTION",
                "CATALOG_CACHE_INVALIDATION_FAILURE",
                "ORDER_EVENT_CONSUMER_BACKLOG",
                "ORDER_IDEMPOTENCY_FAILURE"
        ));
        values.put(ClaimCode.AFFECTED_SERVICE, List.of(
                "PAYMENT_ADAPTER",
                "INVENTORY_SERVICE",
                "CHECKOUT_API",
                "CATALOG_SERVICE",
                "ORDER_EVENT_CONSUMER",
                "ORDER_SERVICE"
        ));
        values.put(ClaimCode.TRIGGER, List.of(
                "PAYMENT_ADAPTER_RELEASE",
                "INVENTORY_SERVICE_RELEASE",
                "CHECKOUT_POOL_CONFIG_CHANGE",
                "CHECKOUT_TRANSACTION_REGRESSION",
                "CATALOG_INVALIDATION_CONFIG_CHANGE",
                "ORDER_CONSUMER_CONFIG_CHANGE",
                "ORDER_CONSUMER_POISON_EVENT",
                "ORDER_CONSUMER_REBALANCE",
                "ORDER_IDEMPOTENCY_POLICY_CHANGE",
                "ORDER_IDEMPOTENCY_STORAGE_CHANGE"
        ));
        values.put(ClaimCode.CUSTOMER_IMPACT, List.of(
                "CHECKOUT_PAYMENT_FAILURES",
                "MULTI_ITEM_CHECKOUT_FAILURES",
                "CHECKOUT_REQUEST_FAILURES",
                "STALE_CATALOG_RESULTS",
                "ORDER_PROCESSING_DELAYS",
                "DUPLICATE_ORDERS"
        ));
        values.put(ClaimCode.OBSERVED_SYMPTOM, List.of(
                "PAYMENT_LATENCY_SPIKE",
                "INVENTORY_CONTRACT_VALIDATION_ERRORS",
                "DATABASE_POOL_WAIT_SPIKE",
                "CATALOG_VERSION_DIVERGENCE",
                "ORDER_CONSUMER_LAG",
                "DUPLICATE_ORDER_CREATION"
        ));
        values.put(ClaimCode.MISSING_EVIDENCE, List.of(
                "PAYMENT_PROVIDER_RESPONSE",
                "PAYMENT_TIMEOUT_CONFIG_AUDIT",
                "CATALOG_SOURCE_OF_TRUTH_VERSION",
                "CATALOG_TAX_CALCULATION_TRACE"
        ));
        return Collections.unmodifiableMap(values);
    }
}
