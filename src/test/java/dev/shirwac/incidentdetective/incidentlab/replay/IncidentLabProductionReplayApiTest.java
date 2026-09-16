package dev.shirwac.incidentdetective.incidentlab.replay;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("replay")
class IncidentLabProductionReplayApiTest {

    private static final String REPLAY_ID =
            "nordly-payment-timeout-withheld-2026-09-16";
    private static final String SOURCE_CONTENT =
            "f890fbfe4afd50bb91d87dce3956e9d22696a494";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesTheVerifiedCapturedReplayWithoutCurrentExecution()
            throws Exception {
        mockMvc.perform(get("/api/v1/incident-lab/recorded-replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason_code").value("ready"));

        mockMvc.perform(post(
                        "/api/v1/incident-lab/runs/recorded-replay"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replay_id").value(REPLAY_ID))
                .andExpect(jsonPath("$.mode").value("recorded_replay"))
                .andExpect(jsonPath(
                        "$.recorded_plan.provider_receipt.transport"
                ).value("developer_api"))
                .andExpect(jsonPath(
                        "$.recorded_run.answer_state"
                ).value("withheld"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.outcome"
                ).value("verification_failed"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.workflow.type"
                ).value("sequential_agent"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.tool_events[2]"
                                + ".runbook_retrieval.backend"
                ).value("pgvector_exact_cosine"))
                .andExpect(jsonPath(
                        "$.provenance.recording_source"
                ).value("captured_public_api"))
                .andExpect(jsonPath(
                        "$.provenance.source_content_git_sha"
                ).value(SOURCE_CONTENT))
                .andExpect(jsonPath(
                        "$.provenance.runtime_build_git_sha"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.provenance.runtime_build_identity_verified"
                ).value(false))
                .andExpect(jsonPath(
                        "$.provenance.resource_sha256"
                ).value(matchesPattern("^[0-9a-f]{64}$")))
                .andExpect(jsonPath(
                        "$.provenance.resource_sha256_verified_at_startup"
                ).value(true))
                .andExpect(jsonPath(
                        "$.playback_receipt.provider_calls"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.model_calls"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.adk_tool_calls"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.read_operations"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.embedding_calls"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.vector_search_executed"
                ).value(false))
                .andExpect(jsonPath(
                        "$.playback_receipt.live_quota_consumed"
                ).value(false))
                .andExpect(jsonPath(
                        "$.playback_receipt.write_tools_available"
                ).value(false))
                .andExpect(jsonPath(
                        "$.playback_receipt.action_executed"
                ).value(false))
                .andExpect(jsonPath(
                        "$.playback_receipt.estimated_cost_usd"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.playback_receipt.cost_status"
                ).value("not_incurred"));
    }

    @Test
    void doesNotExposeTheCapturedFixtureAsAStaticResource() throws Exception {
        mockMvc.perform(get(
                        "/incident-lab/replays/"
                                + "nordly-payment-timeout-withheld-"
                                + "2026-09-16.json"
                ))
                .andExpect(status().isNotFound());
    }
}
