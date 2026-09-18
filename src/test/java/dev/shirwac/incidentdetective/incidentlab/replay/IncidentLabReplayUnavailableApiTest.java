package dev.shirwac.incidentdetective.incidentlab.replay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "incident-detective.incident-lab-replay.resource=")
@AutoConfigureMockMvc
@ActiveProfiles("replay")
class IncidentLabReplayUnavailableApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsThatProductionReplayIsUnavailableUntilAGoldenCaptureExists()
            throws Exception {
        mockMvc.perform(get("/api/v1/incident-lab/recorded-replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version").value(
                        "incident-lab-replay-availability-v1"
                ))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.replay_contract_version").value(
                        "incident-lab-replay-v1"
                ))
                .andExpect(jsonPath("$.reason_code").value(
                        IncidentLabReplayService.NOT_CONFIGURED
                ));
    }

    @Test
    void refusesPlaybackInsteadOfFabricatingAResult() throws Exception {
        mockMvc.perform(post("/api/v1/incident-lab/runs/recorded-replay"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value(
                        "Incident Lab replay unavailable"
                ))
                .andExpect(jsonPath("$.code").value(
                        "INCIDENT_LAB_REPLAY_NOT_AVAILABLE"
                ));
    }
}
