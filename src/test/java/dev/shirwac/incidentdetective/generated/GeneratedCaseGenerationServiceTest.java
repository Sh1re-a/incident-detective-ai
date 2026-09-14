package dev.shirwac.incidentdetective.generated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedCaseGenerationServiceTest {

    private final PaymentTimeoutGeneratedCaseGenerator generator =
            new PaymentTimeoutGeneratedCaseGenerator();

    @Test
    void explicitSeedIsReproducibleAndDoesNotConsumeAServerSeed() {
        AtomicLong serverSeedCalls = new AtomicLong();
        GeneratedCaseGenerationService service = service(() -> {
            serverSeedCalls.incrementAndGet();
            return 999L;
        });
        GeneratedCaseGenerationRequest request = request(42L);

        GeneratedCaseGeneration first = service.generate(request);
        GeneratedCaseGeneration second = service.generate(request);

        assertEquals(first, second);
        assertEquals(0L, serverSeedCalls.get());
        assertEquals(42L, first.receipt().seed());
        assertEquals(
                GeneratedCaseSeed.Origin.EXPLICIT,
                first.receipt().seedOrigin()
        );
        assertEquals(
                first.generatedCase().scenario().scenarioId(),
                first.receipt().scenarioId()
        );
        assertTrue(first.receipt().variant().variantId()
                .matches("payment-timeout-[0-9a-f]{16}"));
        assertTrue(first.receipt().variant().fingerprint()
                .matches("[0-9a-f]{64}"));
    }

    @Test
    void omittedSeedCreatesDistinctReplayableVariants() {
        AtomicLong nextSeed = new AtomicLong(100L);
        GeneratedCaseGenerationService service = service(nextSeed::getAndIncrement);

        GeneratedCaseGeneration first = service.generate(request(null));
        GeneratedCaseGeneration second = service.generate(request(null));
        GeneratedCaseGeneration replay = service.generate(request(
                first.receipt().seed()
        ));

        assertEquals(100L, first.receipt().seed());
        assertEquals(101L, second.receipt().seed());
        assertEquals(
                GeneratedCaseSeed.Origin.SERVER_GENERATED,
                first.receipt().seedOrigin()
        );
        assertNotEquals(
                first.generatedCase().scenario().scenarioId(),
                second.generatedCase().scenario().scenarioId()
        );
        assertNotEquals(first.receipt().variant(), second.receipt().variant());
        assertEquals(first.generatedCase(), replay.generatedCase());
        assertEquals(first.receipt().variant(), replay.receipt().variant());
        assertEquals(
                GeneratedCaseSeed.Origin.EXPLICIT,
                replay.receipt().seedOrigin()
        );
    }

    @Test
    void receiptSerializesSafeReplayProvenanceWithoutGroundTruth()
            throws Exception {
        GeneratedCaseGeneration generation = service(() -> 77L)
                .generate(request(null));
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        String json = mapper.writeValueAsString(generation);

        assertTrue(json.contains("\"seed_origin\":\"server_generated\""));
        assertTrue(json.contains("\"variant_id\":\"payment-timeout-"));
        assertTrue(json.contains("\"fingerprint\":"));
        assertFalse(json.contains("hidden_ground_truth"));
        assertFalse(json.contains("PAYMENT_TIMEOUT_CONFIG"));
    }

    @Test
    void generationRejectsNullRequestsAndMismatchedReceipts() {
        GeneratedCaseGenerationService service = service(() -> 77L);
        assertThrows(NullPointerException.class, () -> service.generate(null));

        GeneratedCaseGeneration generated = service.generate(request(42L));
        GeneratedCaseVariant mismatchedVariant = GeneratedCaseVariant.from(
                generated.receipt().generatorVersion(),
                new GeneratedCaseRequest(
                        generated.receipt().seed(),
                        generated.receipt().incidentFamily(),
                        generated.receipt().evidenceMode(),
                        generated.receipt().noiseLevel()
                ),
                "another-scenario"
        );
        GeneratedCaseReceipt mismatched = new GeneratedCaseReceipt(
                generated.receipt().generatorVersion(),
                generated.receipt().seed(),
                generated.receipt().seedOrigin(),
                generated.receipt().incidentFamily(),
                generated.receipt().evidenceMode(),
                generated.receipt().noiseLevel(),
                "another-scenario",
                mismatchedVariant
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new GeneratedCaseGeneration(
                        generated.generatedCase(),
                        mismatched
                )
        );
    }

    @Test
    void requestDefaultsTheIncidentFamilyAndAllowsAutoEvidenceSelection() {
        GeneratedCaseGenerationRequest request = new GeneratedCaseGenerationRequest(
                null,
                null,
                GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                GeneratedNoiseLevel.NONE
        );

        assertEquals(GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                request.incidentFamily());
        GeneratedCaseGenerationService service = service(() -> 44L);
        GeneratedCaseGeneration auto = service.generate(
                new GeneratedCaseGenerationRequest(
                        null,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        null,
                        GeneratedNoiseLevel.NONE
                )
        );
        assertEquals(
                GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                auto.receipt().evidenceMode()
        );
        assertThrows(
                NullPointerException.class,
                () -> new GeneratedCaseGenerationRequest(
                        null,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        GeneratedEvidenceMode.DIAGNOSTIC,
                        null
                )
        );
    }

    @Test
    void autoEvidenceSelectionIsReplayableFromTheReturnedReceipt() {
        GeneratedCaseGenerationService service = service(() -> 45L);
        GeneratedCaseGeneration auto = service.generate(
                new GeneratedCaseGenerationRequest(
                        null,
                        GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                        null,
                        GeneratedNoiseLevel.LOW
                )
        );
        GeneratedCaseGeneration replay = service.generate(
                new GeneratedCaseGenerationRequest(
                        auto.receipt().seed(),
                        auto.receipt().incidentFamily(),
                        auto.receipt().evidenceMode(),
                        auto.receipt().noiseLevel()
                )
        );

        assertEquals(GeneratedEvidenceMode.DIAGNOSTIC,
                auto.receipt().evidenceMode());
        assertEquals(auto.generatedCase(), replay.generatedCase());
        assertEquals(auto.receipt().variant(), replay.receipt().variant());
    }

    private GeneratedCaseGenerationService service(
            java.util.function.LongSupplier serverSeeds
    ) {
        GeneratedCaseFactory cases = mock(GeneratedCaseFactory.class);
        when(cases.create(any())).thenAnswer(invocation -> {
            GeneratedCaseRequest request = invocation.getArgument(0);
            return generator.generate(request);
        });
        return new GeneratedCaseGenerationService(
                cases,
                new GeneratedCaseSeedResolver(serverSeeds)
        );
    }

    private GeneratedCaseGenerationRequest request(Long seed) {
        return new GeneratedCaseGenerationRequest(
                seed,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        );
    }
}
