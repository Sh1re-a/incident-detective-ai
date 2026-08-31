package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentTimeoutGeneratedCaseGeneratorTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final PaymentTimeoutGeneratedCaseGenerator generator =
            new PaymentTimeoutGeneratedCaseGenerator();

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
    void sameRequestProducesExactlyTheSameCase() {
        GeneratedCaseRequest request = request(
                42L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        );

        assertEquals(generator.generate(request), generator.generate(request));
    }

    @Test
    void differentSeedsProduceDifferentServerGeneratedIdentifiers() {
        GeneratedCase first = generator.generate(request(
                1L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        ));
        GeneratedCase second = generator.generate(request(
                2L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        ));

        assertNotEquals(first.scenario().scenarioId(), second.scenario().scenarioId());
        assertTrue(disjoint(evidenceIds(first), evidenceIds(second)));
        assertTrue(disjoint(traceIds(first), traceIds(second)));
        assertTrue(disjoint(spanIds(first), spanIds(second)));
    }

    @Test
    void diagnosticModeProducesAValidRequestLocalCaseAndHiddenGroundTruth() {
        GeneratedCase generated = generator.generate(request(
                99L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        ));

        assertTrue(validator.validate(generated.scenario()).isEmpty());
        assertTrue(validator.validate(generated.investigationData()).isEmpty());
        assertTrue(validator.validate(generated.hiddenGroundTruth()).isEmpty());
        assertEquals(generated.scenario(), generated.investigationData().scenario());
        assertEquals(
                generated.scenario().scenarioId(),
                generated.hiddenGroundTruth().scenarioId()
        );
        assertEquals(DiagnosisStatus.DIAGNOSED, generated.hiddenGroundTruth().expectedStatus());
        assertEquals(
                "PAYMENT_TIMEOUT_CONFIG",
                generated.hiddenGroundTruth().rootCauseCode()
        );
        assertEquals(
                "PAYMENT_ADAPTER",
                generated.hiddenGroundTruth().affectedService()
        );
        assertEquals(7, generated.investigationData().evidenceInventory().size());
        assertEquals(3, count(generated, MetricEvidence.class));
        assertEquals(3, count(generated, LogEvidence.class));
        assertEquals(1, count(generated, TraceEvidence.class));
        assertGeneratedIdsAreUniqueAndIsolated(generated);
        assertGroundTruthUsesOnlyGeneratedEvidence(generated);
    }

    @Test
    void hiddenGroundTruthIsNotSerializedWithTheGeneratedCase() throws Exception {
        GeneratedCase generated = generator.generate(request(
                99L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        ));

        String json = new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(generated);

        assertFalse(json.contains("hiddenGroundTruth"));
        assertFalse(json.contains("PAYMENT_TIMEOUT_CONFIG"));
    }

    @Test
    void insufficientModeRemovesCausalEvidenceAndRequiresAbstention() {
        GeneratedCase generated = generator.generate(request(
                99L,
                GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                GeneratedNoiseLevel.NONE
        ));

        assertTrue(validator.validate(generated.hiddenGroundTruth()).isEmpty());
        assertEquals(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                generated.hiddenGroundTruth().expectedStatus()
        );
        assertNull(generated.hiddenGroundTruth().rootCauseCode());
        assertNull(generated.hiddenGroundTruth().affectedService());
        assertEquals(0, count(generated, TraceEvidence.class));
        assertFalse(evidenceIds(generated).stream().anyMatch(
                id -> id.endsWith("-log-timeout-config")
        ));
        assertEquals(
                Set.of(ClaimCode.OBSERVED_SYMPTOM, ClaimCode.MISSING_EVIDENCE),
                generated.hiddenGroundTruth().expectedClaims().stream()
                        .map(claim -> claim.claimCode())
                        .collect(java.util.stream.Collectors.toSet())
        );
        assertGroundTruthUsesOnlyGeneratedEvidence(generated);
    }

    @Test
    void lowNoiseAddsOneUnscoredEvidenceItemWithoutChangingTheCoreCase() {
        GeneratedCase withoutNoise = generator.generate(request(
                7L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        ));
        GeneratedCase withNoise = generator.generate(request(
                7L,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        ));

        assertEquals(withoutNoise.scenario(), withNoise.scenario());
        assertEquals(withoutNoise.hiddenGroundTruth(), withNoise.hiddenGroundTruth());
        assertEquals(
                withoutNoise.investigationData().evidenceInventory().size() + 1,
                withNoise.investigationData().evidenceInventory().size()
        );
        String noiseId = withNoise.investigationData().evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .filter(id -> id.endsWith("-log-inventory-noise"))
                .findFirst()
                .orElseThrow();
        assertFalse(supportedEvidenceIds(withNoise).contains(noiseId));
    }

    @Test
    void requestRequiresBothBoundedModes() {
        assertThrows(
                NullPointerException.class,
                () -> new GeneratedCaseRequest(
                        1L,
                        null,
                        GeneratedNoiseLevel.NONE
                )
        );
        assertThrows(
                NullPointerException.class,
                () -> new GeneratedCaseRequest(
                        1L,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        null
                )
        );
    }

    private GeneratedCaseRequest request(
            long seed,
            GeneratedEvidenceMode evidenceMode,
            GeneratedNoiseLevel noiseLevel
    ) {
        return new GeneratedCaseRequest(seed, evidenceMode, noiseLevel);
    }

    private void assertGeneratedIdsAreUniqueAndIsolated(GeneratedCase generated) {
        String scenarioId = generated.scenario().scenarioId();
        assertTrue(scenarioId.matches("^[a-z][a-z0-9-]{1,127}$"));
        List<String> ids = evidenceIds(generated);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertTrue(ids.stream().allMatch(id -> id.startsWith(scenarioId + "-")));
        assertTrue(generated.investigationData().evidenceInventory().stream()
                .allMatch(evidence -> scenarioId.equals(evidence.scenarioId())));
    }

    private void assertGroundTruthUsesOnlyGeneratedEvidence(GeneratedCase generated) {
        assertTrue(evidenceIds(generated).containsAll(supportedEvidenceIds(generated)));
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

    private List<String> traceIds(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(TraceEvidence.class::isInstance)
                .map(TraceEvidence.class::cast)
                .map(evidence -> evidence.content().traceId())
                .toList();
    }

    private List<String> spanIds(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(TraceEvidence.class::isInstance)
                .map(TraceEvidence.class::cast)
                .flatMap(evidence -> evidence.content().spans().stream())
                .map(TraceEvidence.TraceSpan::spanId)
                .toList();
    }

    private boolean disjoint(List<String> first, List<String> second) {
        Set<String> values = new HashSet<>(first);
        return second.stream().noneMatch(values::contains);
    }

    private long count(GeneratedCase generated, Class<? extends Evidence> type) {
        return generated.investigationData().evidenceInventory().stream()
                .filter(type::isInstance)
                .count();
    }
}
