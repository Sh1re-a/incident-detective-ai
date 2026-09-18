package dev.shirwac.incidentdetective.planning;

import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentPlanProposalJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void decodesAProviderNeutralCandidate() throws Exception {
        IncidentPlanProposal proposal = jsonMapper.readValue(
                """
                {
                  "status": "candidate",
                  "summary": "Simulera timeout i betalningsflödet.",
                  "incident_family": "payment_timeout",
                  "requested_severity": "high",
                  "affected_services": ["checkout_api", "payment_adapter"],
                  "requested_blast_radius": "service_chain"
                }
                """,
                IncidentPlanProposal.class
        );

        assertEquals(
                GeneratedIncidentFamily.PAYMENT_TIMEOUT,
                proposal.incidentFamily()
        );
        assertEquals(IncidentSeverity.HIGH, proposal.requestedSeverity());
        assertEquals(
                IncidentService.PAYMENT_ADAPTER,
                proposal.affectedServices().get(1)
        );
    }

    @Test
    void malformedCandidateDoesNotBecomeAnImplicitFallback() {
        assertThrows(
                Exception.class,
                () -> jsonMapper.readValue(
                        """
                        {
                          "status": "candidate",
                          "summary": "Gör något okänt.",
                          "incident_family": null,
                          "requested_severity": null,
                          "affected_services": [],
                          "requested_blast_radius": "whole_demo_world"
                        }
                        """,
                        IncidentPlanProposal.class
                )
        );
    }
}
