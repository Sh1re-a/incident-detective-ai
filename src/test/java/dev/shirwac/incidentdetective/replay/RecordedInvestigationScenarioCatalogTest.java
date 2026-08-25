package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.scenario.Scenario;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RecordedInvestigationScenarioCatalogTest {

    @Autowired
    private InvestigationScenarioCatalog catalog;

    @Test
    void exposesOnlyTheScenarioProjection() {
        Scenario scenario = catalog.findById("checkout-orders-at-risk-v1")
                .orElseThrow();

        assertEquals("Checkout errors threaten orders", scenario.title());
        assertTrue(catalog.findById("unknown-scenario").isEmpty());
    }
}
