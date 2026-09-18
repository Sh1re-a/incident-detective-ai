package dev.shirwac.incidentdetective.planning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentPlanningRequestTest {

    @Test
    void trimsARealNaturalLanguageRequest() {
        IncidentPlanningRequest request = new IncidentPlanningRequest(
                "  Låt checkout få timeout efter en release.  "
        );

        assertEquals(
                "Låt checkout få timeout efter en release.",
                request.instruction()
        );
    }

    @Test
    void rejectsBlankAndOversizedInputBeforeAProviderCall() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentPlanningRequest("   ")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentPlanningRequest(
                        "x".repeat(
                                IncidentPlanningRequest.MAX_INSTRUCTION_LENGTH + 1
                        )
                )
        );
    }
}
