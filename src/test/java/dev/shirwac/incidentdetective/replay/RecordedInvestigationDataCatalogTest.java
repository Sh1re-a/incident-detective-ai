package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.evidence.EvidenceType;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RecordedInvestigationDataCatalogTest {

    @Autowired
    private InvestigationDataCatalog catalog;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void projectsScenarioAndEvidenceWithoutRecordedAnswersOrGroundTruth()
            throws Exception {
        InvestigationData data = catalog.findById("checkout-orders-at-risk-v1")
                .orElseThrow();

        assertEquals("checkout-orders-at-risk-v1", data.scenario().scenarioId());
        assertEquals(9, data.evidenceInventory().size());
        assertEquals(
                Set.of(
                        EvidenceType.METRIC,
                        EvidenceType.LOG,
                        EvidenceType.TRACE,
                        EvidenceType.RUNBOOK
                ),
                data.evidenceInventory().stream()
                        .map(evidence -> evidence.type())
                        .collect(Collectors.toSet())
        );

        String json = jsonMapper.writeValueAsString(data);
        assertTrue(json.contains("\"evidence_inventory\""));
        assertFalse(json.contains("recorded_diagnosis"));
        assertFalse(json.contains("ground_truth"));
        assertFalse(json.contains("root_cause_code"));
        assertFalse(json.contains("claim_support"));
        assertFalse(json.contains("allowed_evidence_ids"));
    }

    @Test
    void exposesBothRecordedScenariosThroughTheSafeBoundary() {
        assertTrue(catalog.findById("checkout-orders-at-risk-v1").isPresent());
        assertTrue(catalog.findById("checkout-cart-segment-failures-v1").isPresent());
        assertTrue(catalog.findById("missing-scenario").isEmpty());
    }
}
