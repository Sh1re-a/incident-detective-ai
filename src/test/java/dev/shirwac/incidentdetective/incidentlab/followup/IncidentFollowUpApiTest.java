package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.api.ApiCorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IncidentFollowUpController.class)
@ActiveProfiles("rag")
@EnableConfigurationProperties(ApiCorsProperties.class)
class IncidentFollowUpApiTest {

    private static final String ENDPOINT =
            "/api/v1/incident-lab/follow-ups";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentFollowUpService service;

    @Test
    void returnsSourceBoundReportAndObservableSteps() throws Exception {
        when(service.answer(any())).thenReturn(response());

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "run_reference": "ilr_12345678901234567890123456789012",
                                  "client_turn_id": "turn_12345678",
                                  "question": "Hur kom du fram till slutsatsen?",
                                  "locale": "sv",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version").value(
                        "incident-lab-follow-up-v1"
                ))
                .andExpect(jsonPath("$.mode").value("live_ai"))
                .andExpect(jsonPath("$.answer.problem_location.service")
                        .value("CATALOG_SERVICE"))
                .andExpect(jsonPath("$.claims[0].section")
                        .value("cause"))
                .andExpect(jsonPath("$.claims[0].citation_ids[0]")
                        .value("log-1"))
                .andExpect(jsonPath("$.citations[0].target_scene")
                        .value("logs"))
                .andExpect(jsonPath("$.steps[3].code")
                        .value("java_verify"))
                .andExpect(jsonPath("$.verification.status")
                        .value("verified_from_frozen_receipt"))
                .andExpect(jsonPath("$.receipt.write_tools_available")
                        .value(false))
                .andExpect(jsonPath("$.receipt.action_executed")
                        .value(false));
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeService() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "run_reference": "ilr_12345678901234567890123456789012",
                                  "question": "Vad hände?",
                                  "locale": "sv",
                                  "confirm_live_ai": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).answer(any());
    }

    private IncidentFollowUpResponse response() {
        return new IncidentFollowUpResponse(
                IncidentFollowUpResponse.CONTRACT_VERSION,
                "ilr_12345678901234567890123456789012",
                "turn_12345678",
                IncidentFollowUpResponse.Mode.LIVE_AI,
                "synchronous_frozen_snapshot",
                IncidentFollowUpResponse.AnswerState.ANSWERED,
                new IncidentFollowUpResponse.Answer(
                        "Katalogtjänsten hade en verifierad cacheavvikelse.",
                        new IncidentFollowUpResponse.ProblemLocation(
                                "CATALOG_SERVICE",
                                "Berörd tjänst: Katalogtjänsten.",
                                "Verifierad i det syntetiska fallet"
                        ),
                        new IncidentFollowUpResponse.Finding(
                                "Cache-invalideringen misslyckades.",
                                "Verifierad i det syntetiska fallet"
                        ),
                        "Produktdata blev inaktuell.",
                        List.of("Avvikelsen är verifierad."),
                        List.of("Verklig produktion är inte undersökt."),
                        "Ingen åtgärd utfördes."
                ),
                List.of(new IncidentFollowUpResponse.Claim(
                        IncidentFollowUpResponse.ClaimSection.CAUSE,
                        "Cache-invalideringen misslyckades.",
                        List.of("log-1")
                )),
                List.of(new IncidentFollowUpResponse.Citation(
                        "log-1",
                        "synthetic/log-1",
                        "log",
                        "Cacheversion mismatch",
                        IncidentFollowUpResponse.TargetScene.LOGS,
                        "log-1"
                )),
                List.of(
                        new IncidentFollowUpResponse.Step(1, "screen_question",
                                "completed", "Kontrollerad.", List.of()),
                        new IncidentFollowUpResponse.Step(2, "select_frozen_evidence",
                                "completed", "Källor valda.", List.of("log-1")),
                        new IncidentFollowUpResponse.Step(3, "compose_bounded_report",
                                "completed", "Rapport skapad.", List.of("log-1")),
                        new IncidentFollowUpResponse.Step(4, "java_verify",
                                "completed", "Verifierad.", List.of("java-1"))
                ),
                new IncidentFollowUpResponse.Verification(
                        "verified_from_frozen_receipt",
                        "a".repeat(64), true, true, true, false, false
                ),
                new IncidentFollowUpResponse.Receipt(
                        1, 1, 0, 0, true, false, false
                ),
                null,
                List.of(new IncidentFollowUpResponse.SuggestedQuestion(
                        "show_sources", "Visa källorna"
                )),
                List.of("Endast den avslutade körningen.")
        );
    }
}
