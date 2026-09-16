package dev.shirwac.incidentdetective.nordly;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public final class DemoOrderCatalog {

    static final String RESOURCE = "demo/nordly-demo-orders-v1.json";
    static final String CATALOG_VERSION = "nordly-demo-orders-v1";

    private static final Pattern ORDER_ID = Pattern.compile("^NORD-\\d{4}$");
    private static final Pattern EVIDENCE_ID = Pattern.compile(
            "^nordly-demo-order-\\d{4}-snapshot$"
    );
    private static final Set<String> MARKETS = Set.of("SE", "DK", "FI");
    private static final Set<String> STATUS_CODES = Set.of(
            "confirmed",
            "packing",
            "shipped"
    );
    private static final Map<String, List<String>> STATUS_LABELS = Map.of(
            "confirmed", List.of("Bekräftad", "Confirmed"),
            "packing", List.of("Packas", "Packing"),
            "shipped", List.of("Skickad", "Shipped")
    );

    private final DemoOrderManifest manifest;
    private final List<DemoOrderSnapshot> orders;
    private final Map<String, DemoOrderSnapshot> ordersById;

    public DemoOrderCatalog(JsonMapper jsonMapper) {
        DemoOrderManifest loaded = read(jsonMapper);
        validate(loaded);

        LinkedHashMap<String, DemoOrderSnapshot> indexed = new LinkedHashMap<>();
        for (DemoOrderSnapshot order : loaded.orders()) {
            indexed.put(order.orderId(), order);
        }
        manifest = loaded;
        orders = List.copyOf(loaded.orders());
        ordersById = Map.copyOf(indexed);
    }

    public String catalogVersion() {
        return manifest.catalogVersion();
    }

    public String truthLabel() {
        return manifest.truthLabel();
    }

    public String truthLabelEn() {
        return manifest.truthLabelEn();
    }

    public Instant snapshotAt() {
        return manifest.snapshotAt();
    }

    public List<DemoOrderSnapshot> orders() {
        return orders;
    }

    public DemoOrderSnapshot findById(String orderId) {
        DemoOrderSnapshot order = ordersById.get(orderId);
        if (order == null) {
            throw new DemoOrderNotFoundException(orderId);
        }
        return order;
    }

    private static DemoOrderManifest read(JsonMapper jsonMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readValue(input, DemoOrderManifest.class);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load synthetic Nordly demo orders",
                    exception
            );
        }
    }

    private static void validate(DemoOrderManifest manifest) {
        if (manifest == null) {
            throw invalid("manifest is required");
        }
        requireEqual(CATALOG_VERSION, manifest.catalogVersion(), "catalog version");
        if (manifest.snapshotAt() == null) {
            throw invalid("snapshot timestamp is required");
        }
        requireNonBlank(manifest.truthLabel(), "truth label");
        requireNonBlank(manifest.truthLabelEn(), "English truth label");
        if (manifest.orders() == null
                || manifest.orders().size() < 2
                || manifest.orders().size() > 3) {
            throw invalid("catalog must contain two or three demo orders");
        }

        Set<String> orderIds = new HashSet<>();
        Set<String> evidenceIds = new HashSet<>();
        for (DemoOrderSnapshot order : manifest.orders()) {
            validateOrder(order);
            if (order.updatedAt().isAfter(manifest.snapshotAt())) {
                throw invalid("order updated after catalog snapshot " + order.orderId());
            }
            if (!orderIds.add(order.orderId())) {
                throw invalid("duplicate order ID " + order.orderId());
            }
            if (!evidenceIds.add(order.evidenceId())) {
                throw invalid("duplicate evidence ID " + order.evidenceId());
            }
        }
        if (!orderIds.contains("NORD-2048")) {
            throw invalid("NORD-2048 must be present");
        }
    }

    private static void validateOrder(DemoOrderSnapshot order) {
        if (order == null) {
            throw invalid("order is required");
        }
        if (order.orderId() == null || !ORDER_ID.matcher(order.orderId()).matches()) {
            throw invalid("invalid order ID");
        }
        if (!MARKETS.contains(order.market())) {
            throw invalid("invalid market for " + order.orderId());
        }
        if (order.itemCount() < 1 || order.itemCount() > 20) {
            throw invalid("invalid item count for " + order.orderId());
        }
        if (order.createdAt() == null
                || order.updatedAt() == null
                || order.updatedAt().isBefore(order.createdAt())) {
            throw invalid("invalid timestamps for " + order.orderId());
        }
        if (order.estimatedDeliveryFrom() == null
                || order.estimatedDeliveryThrough() == null
                || order.estimatedDeliveryThrough().isBefore(
                order.estimatedDeliveryFrom()
        )) {
            throw invalid("invalid delivery window for " + order.orderId());
        }
        if (!STATUS_CODES.contains(order.statusCode())) {
            throw invalid("invalid status for " + order.orderId());
        }
        List<String> expectedLabels = STATUS_LABELS.get(order.statusCode());
        if (!expectedLabels.get(0).equals(order.statusSv())
                || !expectedLabels.get(1).equals(order.statusEn())) {
            throw invalid("status labels do not match code for " + order.orderId());
        }
        requireNonBlank(order.statusSv(), "Swedish status");
        requireNonBlank(order.statusEn(), "English status");
        requireNonBlank(order.paymentState(), "payment state");
        requireNonBlank(order.fulfilmentState(), "fulfilment state");
        requireNonBlank(order.summarySv(), "Swedish summary");
        requireNonBlank(order.summaryEn(), "English summary");
        requireNonBlank(order.nextStepSv(), "Swedish next step");
        requireNonBlank(order.nextStepEn(), "English next step");
        requireEqual(
                "demo/nordly-demo-orders-v1#" + order.orderId(),
                order.sourceRef(),
                "source reference"
        );
        String expectedEvidenceId = "nordly-demo-order-"
                + order.orderId().substring("NORD-".length())
                + "-snapshot";
        if (order.evidenceId() == null
                || !EVIDENCE_ID.matcher(order.evidenceId()).matches()
                || !expectedEvidenceId.equals(order.evidenceId())) {
            throw invalid("invalid evidence ID for " + order.orderId());
        }
    }

    private static void requireEqual(
            String expected,
            String actual,
            String description
    ) {
        if (!expected.equals(actual)) {
            throw invalid(description + " must be " + expected);
        }
    }

    private static void requireNonBlank(String value, String description) {
        if (value == null || value.isBlank()) {
            throw invalid(description + " must not be blank");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid Nordly demo-order catalog: " + message);
    }
}

record DemoOrderManifest(
        String catalogVersion,
        Instant snapshotAt,
        String truthLabel,
        String truthLabelEn,
        List<DemoOrderSnapshot> orders
) {
    DemoOrderManifest {
        orders = orders == null ? null : List.copyOf(orders);
    }
}

final class DemoOrderNotFoundException extends RuntimeException {

    DemoOrderNotFoundException(String orderId) {
        super("Synthetic Nordly demo order not found: " + orderId);
    }
}
