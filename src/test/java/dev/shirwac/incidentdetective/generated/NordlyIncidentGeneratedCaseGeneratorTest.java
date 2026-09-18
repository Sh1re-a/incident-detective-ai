package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;
import dev.shirwac.incidentdetective.domain.evidence.MetricEvidence;
import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NordlyIncidentGeneratedCaseGeneratorTest {

    private static final Map<GeneratedIncidentFamily, ExpectedFamily> EXPECTED = Map.of(
            GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
            new ExpectedFamily(
                    "generated-catalog-cache-invalidation-",
                    "CATALOG_CACHE_INVALIDATION_FAILURE",
                    "CATALOG_SERVICE",
                    "stale_catalog_responses",
                    "CATALOG_SOURCE_OF_TRUTH_VERSION"
            ),
            GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
            new ExpectedFamily(
                    "generated-order-event-backlog-",
                    "ORDER_EVENT_CONSUMER_BACKLOG",
                    "ORDER_EVENT_CONSUMER",
                    "delayed_orders",
                    "ORDER_CONSUMER_CONFIG_AUDIT"
            ),
            GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
            new ExpectedFamily(
                    "generated-order-idempotency-failure-",
                    "ORDER_IDEMPOTENCY_FAILURE",
                    "ORDER_SERVICE",
                    "duplicate_orders",
                    "ORDER_IDEMPOTENCY_STORAGE_AUDIT"
            )
    );

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final NordlyIncidentGeneratedCaseGenerator generator =
            new NordlyIncidentGeneratedCaseGenerator();

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void eachDiagnosticFamilyProducesValidIsolatedEvidenceAndGroundTruth() {
        EXPECTED.forEach((family, expected) -> {
            GeneratedCase generated = generator.generate(request(
                    family,
                    GeneratedEvidenceMode.DIAGNOSTIC,
                    GeneratedNoiseLevel.NONE
            ));

            assertTrue(generated.scenario().scenarioId().startsWith(expected.idPrefix()));
            assertEquals(
                    expected.rootCause(),
                    generated.hiddenGroundTruth().rootCauseCode()
            );
            assertEquals(
                    expected.affectedService(),
                    generated.hiddenGroundTruth().affectedService()
            );
            assertEquals(DiagnosisStatus.DIAGNOSED,
                    generated.hiddenGroundTruth().expectedStatus());
            assertTrue(metricNames(generated).contains(expected.primaryCountMetric()));
            assertEquals(7, generated.investigationData().evidenceInventory().size());
            assertEquals(3, count(generated, MetricEvidence.class));
            assertEquals(3, count(generated, LogEvidence.class));
            assertEquals(1, count(generated, TraceEvidence.class));
            assertValidAndIsolated(generated);
        });
    }

    @Test
    void eachInsufficientFamilyRemovesCausalEvidenceAndRequiresAbstention() {
        EXPECTED.forEach((family, expected) -> {
            GeneratedCase generated = generator.generate(request(
                    family,
                    GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                    GeneratedNoiseLevel.NONE
            ));

            assertEquals(DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                    generated.hiddenGroundTruth().expectedStatus());
            assertNull(generated.hiddenGroundTruth().rootCauseCode());
            assertNull(generated.hiddenGroundTruth().affectedService());
            assertEquals(0, count(generated, TraceEvidence.class));
            assertFalse(evidenceIds(generated).stream().anyMatch(
                    id -> id.endsWith("-log-causal-config")
            ));
            assertTrue(generated.hiddenGroundTruth().expectedClaims().stream()
                    .anyMatch(claim -> claim.claimCode() == ClaimCode.MISSING_EVIDENCE
                            && expected.missingEvidence().equals(
                            claim.claimValueCode()
                    )));
            assertValidAndIsolated(generated);
        });
    }

    @Test
    void familyAndSeedAreDeterministicButFamiliesRemainDistinct() {
        GeneratedCase catalogFirst = generator.generate(request(
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        ));
        GeneratedCase catalogSecond = generator.generate(request(
                GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        ));
        GeneratedCase backlog = generator.generate(request(
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        ));

        assertEquals(catalogFirst, catalogSecond);
        assertNotEquals(catalogFirst.scenario().scenarioId(), backlog.scenario().scenarioId());
        assertTrue(disjoint(evidenceIds(catalogFirst), evidenceIds(backlog)));
    }

    @Test
    void lowNoiseAddsOneUnsupportedDistractor() {
        EXPECTED.keySet().forEach(family -> {
            GeneratedCase withoutNoise = generator.generate(request(
                    family,
                    GeneratedEvidenceMode.DIAGNOSTIC,
                    GeneratedNoiseLevel.NONE
            ));
            GeneratedCase withNoise = generator.generate(request(
                    family,
                    GeneratedEvidenceMode.DIAGNOSTIC,
                    GeneratedNoiseLevel.LOW
            ));

            assertEquals(
                    withoutNoise.investigationData().evidenceInventory().size() + 1,
                    withNoise.investigationData().evidenceInventory().size()
            );
            String noiseId = evidenceIds(withNoise).stream()
                    .filter(id -> id.endsWith("-log-unrelated-noise"))
                    .findFirst()
                    .orElseThrow();
            assertFalse(supportedEvidenceIds(withNoise).contains(noiseId));
        });
    }

    private GeneratedCaseRequest request(
            GeneratedIncidentFamily family,
            GeneratedEvidenceMode evidenceMode,
            GeneratedNoiseLevel noiseLevel
    ) {
        return new GeneratedCaseRequest(42L, family, evidenceMode, noiseLevel);
    }

    private void assertValidAndIsolated(GeneratedCase generated) {
        assertTrue(validator.validate(generated.scenario()).isEmpty());
        assertTrue(validator.validate(generated.investigationData()).isEmpty());
        assertTrue(validator.validate(generated.hiddenGroundTruth()).isEmpty());
        String scenarioId = generated.scenario().scenarioId();
        List<String> ids = evidenceIds(generated);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(id -> id.startsWith(scenarioId + "-")));
        assertTrue(ids.containsAll(supportedEvidenceIds(generated)));
        assertTrue(generated.investigationData().evidenceInventory().stream()
                .allMatch(evidence -> scenarioId.equals(evidence.scenarioId())));
    }

    private List<String> metricNames(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(MetricEvidence.class::isInstance)
                .map(MetricEvidence.class::cast)
                .map(metric -> metric.content().metricName())
                .toList();
    }

    private List<String> evidenceIds(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .toList();
    }

    private Set<String> supportedEvidenceIds(GeneratedCase generated) {
        return generated.hiddenGroundTruth().claimSupport().stream()
                .map(ClaimSupport::allowedEvidenceIds)
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toSet());
    }

    private long count(GeneratedCase generated, Class<? extends Evidence> type) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(type::isInstance)
                .count();
    }

    private boolean disjoint(List<String> first, List<String> second) {
        Set<String> values = new HashSet<>(first);
        return second.stream().noneMatch(values::contains);
    }

    private record ExpectedFamily(
            String idPrefix,
            String rootCause,
            String affectedService,
            String primaryCountMetric,
            String missingEvidence
    ) {
    }
}
