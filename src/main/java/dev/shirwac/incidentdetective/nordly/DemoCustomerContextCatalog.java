package dev.shirwac.incidentdetective.nordly;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

@Service
public final class DemoCustomerContextCatalog {

    static final String RESOURCE = "demo/nordly-demo-customer-v1.json";
    static final String CONTEXT_VERSION = "nordly-demo-customer-v1";
    static final String CONTEXT_ID = "public-demo-customer";
    static final String SOURCE_REF =
            "demo/nordly-demo-customer-v1#current-order";
    static final String EVIDENCE_ID =
            "nordly-demo-customer-current-order-context";

    private final DemoCustomerContextManifest manifest;
    private final DemoOrderSnapshot currentOrder;

    public DemoCustomerContextCatalog(
            JsonMapper jsonMapper,
            DemoOrderCatalog orders
    ) {
        DemoCustomerContextManifest loaded = read(jsonMapper);
        validate(loaded);
        DemoOrderSnapshot order = orders.findById(loaded.currentOrderId());
        if (!"shipped".equals(order.statusCode())) {
            throw new IllegalStateException(
                    "Invalid Nordly demo-customer context: current order must be shipped"
            );
        }
        manifest = loaded;
        currentOrder = order;
    }

    public String contextVersion() {
        return manifest.contextVersion();
    }

    public String contextId() {
        return manifest.contextId();
    }

    public String sourceRef() {
        return manifest.sourceRef();
    }

    public String customerDisplayName() {
        return manifest.customerDisplayName();
    }

    public String customerPreferredName() {
        return manifest.customerPreferredName();
    }

    public String evidenceId() {
        return manifest.evidenceId();
    }

    public DemoOrderSnapshot currentOrder() {
        return currentOrder;
    }

    private static DemoCustomerContextManifest read(JsonMapper jsonMapper) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readValue(
                    input,
                    DemoCustomerContextManifest.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load the synthetic Nordly demo-customer context",
                    exception
            );
        }
    }

    private static void validate(DemoCustomerContextManifest manifest) {
        if (manifest == null) {
            throw invalid("manifest is required");
        }
        requireEqual(
                CONTEXT_VERSION,
                manifest.contextVersion(),
                "context version"
        );
        requireEqual(CONTEXT_ID, manifest.contextId(), "context ID");
        requireEqual(
                "Shirwac \"Shirre\" Abib",
                manifest.customerDisplayName(),
                "customer display name"
        );
        requireEqual(
                "Shirre",
                manifest.customerPreferredName(),
                "customer preferred name"
        );
        requireEqual(SOURCE_REF, manifest.sourceRef(), "source reference");
        requireEqual(EVIDENCE_ID, manifest.evidenceId(), "evidence ID");
        requireEqual("NORD-2051", manifest.currentOrderId(), "current order ID");
        if (!manifest.syntheticOnly()) {
            throw invalid("synthetic_only must be true");
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

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "Invalid Nordly demo-customer context: " + message
        );
    }
}

record DemoCustomerContextManifest(
        String contextVersion,
        String contextId,
        String customerDisplayName,
        String customerPreferredName,
        String currentOrderId,
        String sourceRef,
        String evidenceId,
        boolean syntheticOnly
) {
}
