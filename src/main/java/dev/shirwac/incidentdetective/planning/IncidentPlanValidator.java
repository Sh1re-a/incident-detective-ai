package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Java-owned policy boundary between a model proposal and synthetic generation.
 */
public final class IncidentPlanValidator {

    private static final IncidentSeverity RUNNABLE_SEVERITY =
            IncidentSeverity.HIGH;
    private static final Set<GeneratedIncidentFamily> RUNNABLE_FAMILIES =
            Set.copyOf(EnumSet.allOf(GeneratedIncidentFamily.class));
    private static final Map<GeneratedIncidentFamily, List<IncidentService>>
            FAMILY_SERVICES = familyServices();
    private static final Map<GeneratedIncidentFamily, IncidentService>
            CANONICAL_SERVICES = canonicalServices();
    private static final Map<GeneratedIncidentFamily, String>
            CANONICAL_SUMMARIES = canonicalSummaries();

    public IncidentPlanValidationResult validate(
            IncidentPlanProposal proposal
    ) {
        Objects.requireNonNull(proposal, "proposal must not be null");

        if (proposal.requestedBlastRadius()
                == IncidentBlastRadius.REAL_ENVIRONMENT) {
            return IncidentPlanValidationResult.rejected(
                    IncidentPlanRejectionCode.REAL_ENVIRONMENT_NOT_ALLOWED,
                    "Only request-local synthetic incidents are allowed."
            );
        }
        if (proposal.status() == IncidentPlanProposalStatus.UNSUPPORTED) {
            return IncidentPlanValidationResult.rejected(
                    IncidentPlanRejectionCode.UNSUPPORTED_REQUEST,
                    "The request does not match a supported synthetic incident family."
            );
        }
        if (!RUNNABLE_FAMILIES.contains(proposal.incidentFamily())) {
            return IncidentPlanValidationResult.rejected(
                    IncidentPlanRejectionCode.INCIDENT_FAMILY_NOT_RUNNABLE,
                    "This incident family is recognized but is not enabled in the runnable demo."
            );
        }

        List<IncidentService> allowedServices = FAMILY_SERVICES.get(
                proposal.incidentFamily()
        );
        Set<IncidentService> requestedServices = EnumSet.copyOf(
                proposal.affectedServices()
        );
        List<IncidentPlanAdjustmentCode> adjustments = new ArrayList<>();

        List<IncidentService> selectedServices = allowedServices.stream()
                .filter(requestedServices::contains)
                .toList();
        if (selectedServices.isEmpty()) {
            return IncidentPlanValidationResult.rejected(
                    IncidentPlanRejectionCode.SERVICE_SCOPE_NOT_SUPPORTED,
                    "None of the requested services belong to the selected incident family."
            );
        }

        if (proposal.requestedBlastRadius()
                == IncidentBlastRadius.WHOLE_DEMO_WORLD) {
            adjustments.add(
                    IncidentPlanAdjustmentCode.WHOLE_SYSTEM_NARROWED_TO_ONE_FAMILY
            );
        }

        IncidentService canonicalService = CANONICAL_SERVICES.get(
                proposal.incidentFamily()
        );
        List<IncidentService> canonicalServices = List.of(canonicalService);
        if (!requestedServices.equals(EnumSet.of(canonicalService))) {
            adjustments.add(
                    IncidentPlanAdjustmentCode
                            .SERVICES_NARROWED_TO_RUNNABLE_CORE
            );
        }
        selectedServices = canonicalServices;

        IncidentSeverity severity = RUNNABLE_SEVERITY;
        if (proposal.requestedSeverity() != RUNNABLE_SEVERITY) {
            adjustments.add(
                    IncidentPlanAdjustmentCode.SEVERITY_CANONICALIZED_TO_HIGH
            );
        }

        IncidentPlan plan = new IncidentPlan(
                IncidentPlan.CONTRACT_VERSION,
                proposal.incidentFamily(),
                severity,
                selectedServices,
                CANONICAL_SUMMARIES.get(proposal.incidentFamily()),
                true,
                false,
                true
        );
        return IncidentPlanValidationResult.accepted(
                plan,
                List.copyOf(adjustments)
        );
    }

    public List<IncidentService> allowedServices(
            GeneratedIncidentFamily family
    ) {
        return FAMILY_SERVICES.get(Objects.requireNonNull(family));
    }

    public Set<GeneratedIncidentFamily> runnableFamilies() {
        return RUNNABLE_FAMILIES;
    }

    private static Map<GeneratedIncidentFamily, List<IncidentService>>
            familyServices() {
        Map<GeneratedIncidentFamily, List<IncidentService>> services =
                new LinkedHashMap<>();
        services.put(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                List.of(
                        IncidentService.STOREFRONT,
                        IncidentService.CHECKOUT_API,
                        IncidentService.PAYMENT_ADAPTER
                )
        );
        services.put(
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                List.of(
                        IncidentService.STOREFRONT,
                        IncidentService.CATALOG_SERVICE
                )
        );
        services.put(
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                List.of(
                        IncidentService.ORDER_SERVICE,
                        IncidentService.ORDER_EVENT_CONSUMER,
                        IncidentService.INVENTORY_SERVICE
                )
        );
        services.put(
                GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
                List.of(
                        IncidentService.STOREFRONT,
                        IncidentService.CHECKOUT_API,
                        IncidentService.ORDER_SERVICE
                )
        );
        if (!services.keySet().equals(
                EnumSet.allOf(GeneratedIncidentFamily.class)
        )) {
            throw new IllegalStateException(
                    "Every generated incident family requires a planning policy"
            );
        }
        return Map.copyOf(services);
    }

    private static Map<GeneratedIncidentFamily, IncidentService>
            canonicalServices() {
        return Map.of(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentService.PAYMENT_ADAPTER,
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                IncidentService.CATALOG_SERVICE,
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                IncidentService.ORDER_EVENT_CONSUMER,
                GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
                IncidentService.ORDER_SERVICE
        );
    }

    private static Map<GeneratedIncidentFamily, String> canonicalSummaries() {
        return Map.of(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                "Synthetic PAYMENT_ADAPTER timeout with three HTTP 504 responses.",
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                "Synthetic CATALOG_SERVICE incident with catalog version divergence.",
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                "Synthetic ORDER_EVENT_CONSUMER incident with elevated consumer lag.",
                GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
                "Synthetic ORDER_SERVICE incident with duplicate order creation."
        );
    }
}
