package dev.shirwac.incidentdetective.generated;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedCaseVariantTest {

    @Test
    void sameRecipeProducesTheSameIdentity() {
        GeneratedCaseRequest request = request(
                42L,
                GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.LOW
        );

        GeneratedCaseVariant first = GeneratedCaseVariant.from(
                "generator-v2",
                request,
                "generated-order-event-backlog-a"
        );
        GeneratedCaseVariant second = GeneratedCaseVariant.from(
                "generator-v2",
                request,
                "generated-order-event-backlog-a"
        );

        assertEquals(first, second);
    }

    @Test
    void everyReplayControlAndVersionAffectsTheFingerprint() {
        GeneratedCaseRequest baseline = request(
                42L,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        );
        GeneratedCaseVariant expected = variant(
                "generator-v2",
                baseline,
                "generated-payment-timeout-a"
        );

        assertNotEquals(expected, variant(
                "generator-v3",
                baseline,
                "generated-payment-timeout-a"
        ));
        assertNotEquals(expected, variant(
                "generator-v2",
                request(
                        43L,
                        baseline.incidentFamily(),
                        baseline.evidenceMode(),
                        baseline.noiseLevel()
                ),
                "generated-payment-timeout-a"
        ));
        assertNotEquals(expected, variant(
                "generator-v2",
                request(
                        baseline.seed(),
                        GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
                        baseline.evidenceMode(),
                        baseline.noiseLevel()
                ),
                "generated-payment-timeout-a"
        ));
        assertNotEquals(expected, variant(
                "generator-v2",
                request(
                        baseline.seed(),
                        baseline.incidentFamily(),
                        GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                        baseline.noiseLevel()
                ),
                "generated-payment-timeout-a"
        ));
        assertNotEquals(expected, variant(
                "generator-v2",
                request(
                        baseline.seed(),
                        baseline.incidentFamily(),
                        baseline.evidenceMode(),
                        GeneratedNoiseLevel.LOW
                ),
                "generated-payment-timeout-a"
        ));
        assertNotEquals(expected, variant(
                "generator-v2",
                baseline,
                "generated-payment-timeout-b"
        ));
    }

    @Test
    void rejectsAmbiguousOrMalformedIdentityInputs() {
        GeneratedCaseRequest request = request(
                42L,
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                GeneratedEvidenceMode.DIAGNOSTIC,
                GeneratedNoiseLevel.NONE
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedCaseVariant.from(" ", request, "scenario-a")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedCaseVariant.from("generator-v2", request, " ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new GeneratedCaseVariant("variant", "not-a-sha-256")
        );
    }

    private GeneratedCaseVariant variant(
            String generatorVersion,
            GeneratedCaseRequest request,
            String scenarioId
    ) {
        return GeneratedCaseVariant.from(generatorVersion, request, scenarioId);
    }

    private GeneratedCaseRequest request(
            long seed,
            GeneratedIncidentFamily family,
            GeneratedEvidenceMode evidenceMode,
            GeneratedNoiseLevel noiseLevel
    ) {
        return new GeneratedCaseRequest(
                seed,
                family,
                evidenceMode,
                noiseLevel
        );
    }
}
