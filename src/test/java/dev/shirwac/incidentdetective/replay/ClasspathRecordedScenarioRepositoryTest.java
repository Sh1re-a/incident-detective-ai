package dev.shirwac.incidentdetective.replay;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JsonTest
class ClasspathRecordedScenarioRepositoryTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void loadsBothValidatedPackagesFromTheClasspathIndex() {
        ClasspathRecordedScenarioRepository repository =
                new ClasspathRecordedScenarioRepository(
                        jsonMapper,
                        Validation.buildDefaultValidatorFactory().getValidator()
                );

        RecordedScenarioPackage paymentScenario = repository
                .findById("checkout-orders-at-risk-v1")
                .orElseThrow();
        RecordedScenarioPackage inventoryScenario = repository
                .findById("checkout-cart-segment-failures-v1")
                .orElseThrow();

        assertEquals(
                "PAYMENT_TIMEOUT_CONFIG",
                paymentScenario.groundTruth().rootCauseCode()
        );
        assertEquals(
                "INVENTORY_SCHEMA_MISMATCH",
                inventoryScenario.groundTruth().rootCauseCode()
        );
        assertTrue(repository.findById("unknown-scenario").isEmpty());
    }
}
