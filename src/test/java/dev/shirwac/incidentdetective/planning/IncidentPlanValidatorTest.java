package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentPlanValidatorTest {

    private final IncidentPlanValidator validator = new IncidentPlanValidator();

    @Test
    void approvesAnExactBoundedProposalWithoutChangingIt() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                List.of(IncidentService.PAYMENT_ADAPTER),
                IncidentBlastRadius.SINGLE_SERVICE
        ));

        assertTrue(result.accepted());
        assertEquals(IncidentPlanDecision.APPROVED, result.decision());
        assertTrue(result.adjustments().isEmpty());
        assertNull(result.rejection());
        assertEquals(IncidentSeverity.HIGH, result.plan().severity());
        assertEquals(
                List.of(IncidentService.PAYMENT_ADAPTER),
                result.plan().affectedServices()
        );
        assertEquals(
                List.of("PAYMENT_ADAPTER"),
                result.plan().affectedServiceCodes()
        );
        assertEquals(
                "Synthetic PAYMENT_ADAPTER timeout with three HTTP 504 responses.",
                result.plan().summary()
        );
        assertTrue(result.plan().syntheticOnly());
        assertFalse(result.plan().writeActionsAllowed());
        assertTrue(result.plan().humanApprovalRequired());
    }

    @Test
    void narrowsWholeSystemAndCriticalLanguageToOneSafeFamily() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.CRITICAL,
                List.of(
                        IncidentService.STOREFRONT,
                        IncidentService.CHECKOUT_API,
                        IncidentService.PAYMENT_ADAPTER,
                        IncidentService.ORDER_SERVICE
                ),
                IncidentBlastRadius.WHOLE_DEMO_WORLD
        ));

        assertEquals(IncidentPlanDecision.NARROWED, result.decision());
        assertEquals(IncidentSeverity.HIGH, result.plan().severity());
        assertEquals(
                List.of(IncidentService.PAYMENT_ADAPTER),
                result.plan().affectedServices()
        );
        assertEquals(
                List.of(
                        IncidentPlanAdjustmentCode
                                .WHOLE_SYSTEM_NARROWED_TO_ONE_FAMILY,
                        IncidentPlanAdjustmentCode
                                .SERVICES_NARROWED_TO_RUNNABLE_CORE,
                        IncidentPlanAdjustmentCode
                                .SEVERITY_CANONICALIZED_TO_HIGH
                ),
                result.adjustments()
        );
    }

    @Test
    void canonicalizesLowerSeverityToTheHighAlarmTheSimulatorProduces() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.LOW,
                List.of(IncidentService.PAYMENT_ADAPTER),
                IncidentBlastRadius.SINGLE_SERVICE
        ));

        assertEquals(IncidentPlanDecision.NARROWED, result.decision());
        assertEquals(IncidentSeverity.HIGH, result.plan().severity());
        assertEquals(
                List.of(
                        IncidentPlanAdjustmentCode
                                .SEVERITY_CANONICALIZED_TO_HIGH
                ),
                result.adjustments()
        );
    }

    @Test
    void canonicalizesCheckoutLanguageToTheRunnablePaymentAdapter() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                List.of(
                        IncidentService.STOREFRONT,
                        IncidentService.CHECKOUT_API
                ),
                IncidentBlastRadius.SERVICE_CHAIN
        ));

        assertEquals(IncidentPlanDecision.NARROWED, result.decision());
        assertEquals(
                List.of(IncidentService.PAYMENT_ADAPTER),
                result.plan().affectedServices()
        );
        assertEquals(
                List.of(
                        IncidentPlanAdjustmentCode
                                .SERVICES_NARROWED_TO_RUNNABLE_CORE
                ),
                result.adjustments()
        );
    }

    @Test
    void rejectsARecognizedFamilyThatIsNotRunnableInV1() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
                IncidentSeverity.MEDIUM,
                List.of(
                        IncidentService.ORDER_SERVICE,
                        IncidentService.CHECKOUT_API
                ),
                IncidentBlastRadius.SINGLE_SERVICE
        ));

        assertEquals(IncidentPlanDecision.REJECTED, result.decision());
        assertEquals(
                IncidentPlanRejectionCode.INCIDENT_FAMILY_NOT_RUNNABLE,
                result.rejection().code()
        );
    }

    @Test
    void rejectsARequestForRealEnvironmentImpact() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.HIGH,
                List.of(IncidentService.PAYMENT_ADAPTER),
                IncidentBlastRadius.REAL_ENVIRONMENT
        ));

        assertFalse(result.accepted());
        assertEquals(IncidentPlanDecision.REJECTED, result.decision());
        assertNull(result.plan());
        assertEquals(
                IncidentPlanRejectionCode.REAL_ENVIRONMENT_NOT_ALLOWED,
                result.rejection().code()
        );
    }

    @Test
    void rejectsUnsupportedRequestsWithoutInventingAClosestFamily() {
        IncidentPlanProposal proposal = new IncidentPlanProposal(
                IncidentPlanProposalStatus.UNSUPPORTED,
                "Begäran matchar ingen tillåten syntetisk incident.",
                null,
                null,
                List.of(),
                IncidentBlastRadius.WHOLE_DEMO_WORLD
        );

        IncidentPlanValidationResult result = validator.validate(proposal);

        assertEquals(IncidentPlanDecision.REJECTED, result.decision());
        assertEquals(
                IncidentPlanRejectionCode.UNSUPPORTED_REQUEST,
                result.rejection().code()
        );
    }

    @Test
    void rejectsAServiceScopeWithNoFamilyOverlap() {
        IncidentPlanValidationResult result = validator.validate(candidate(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                IncidentSeverity.MEDIUM,
                List.of(IncidentService.CATALOG_SERVICE),
                IncidentBlastRadius.SERVICE_CHAIN
        ));

        assertEquals(IncidentPlanDecision.REJECTED, result.decision());
        assertEquals(
                IncidentPlanRejectionCode.SERVICE_SCOPE_NOT_SUPPORTED,
                result.rejection().code()
        );
    }

    @Test
    void exposesAPolicyForEveryGeneratedFamily() {
        for (GeneratedIncidentFamily family
                : EnumSet.allOf(GeneratedIncidentFamily.class)) {
            assertFalse(validator.allowedServices(family).isEmpty());
        }
        assertEquals(
                Set.of(GeneratedIncidentFamily.PAYMENT_TIMEOUT),
                validator.runnableFamilies()
        );
    }

    @Test
    void planCannotBeConstructedWithWriteCapability() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentPlan(
                        IncidentPlan.CONTRACT_VERSION,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        IncidentSeverity.MEDIUM,
                        List.of(IncidentService.PAYMENT_ADAPTER),
                        "Syntetisk timeout.",
                        true,
                        true,
                        true
                )
        );
    }

    @Test
    void proposalRejectsDuplicateServicesOutsideTheProviderSchema() {
        assertThrows(
                IllegalArgumentException.class,
                () -> candidate(
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        IncidentSeverity.HIGH,
                        List.of(
                                IncidentService.PAYMENT_ADAPTER,
                                IncidentService.PAYMENT_ADAPTER
                        ),
                        IncidentBlastRadius.SINGLE_SERVICE
                )
        );
    }

    private IncidentPlanProposal candidate(
            GeneratedIncidentFamily family,
            IncidentSeverity severity,
            List<IncidentService> services,
            IncidentBlastRadius radius
    ) {
        return new IncidentPlanProposal(
                IncidentPlanProposalStatus.CANDIDATE,
                "Skapa en kontrollerad syntetisk incident.",
                family,
                severity,
                services,
                radius
        );
    }
}
