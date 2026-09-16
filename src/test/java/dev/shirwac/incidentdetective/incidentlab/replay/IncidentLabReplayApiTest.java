package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties =
        "incident-detective.incident-lab-replay.resource="
                + "incident-lab/replays/test-golden-v1.json")
@AutoConfigureMockMvc
@ActiveProfiles("replay")
class IncidentLabReplayApiTest {

    private static final int DAILY_START_LIMIT = 20;
    private static final long DAILY_BUDGET_MICRO_USD = 200_000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private GlobalDailyLiveQuota dailyQuota;

    @Test
    void exposesTheConfiguredRecordedContractWithoutCurrentExecution()
            throws Exception {
        mockMvc.perform(get("/api/v1/incident-lab/recorded-replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason_code").value("ready"));

        GlobalDailyLiveQuota.Snapshot before = dailyQuota.snapshot(
                DAILY_START_LIMIT,
                DAILY_BUDGET_MICRO_USD
        );
        MvcResult first = mockMvc.perform(post(
                        "/api/v1/incident-lab/runs/recorded-replay"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract_version").value(
                        "incident-lab-replay-v1"
                ))
                .andExpect(jsonPath("$.replay_id").value(
                        "test-contract-fixture-v1"
                ))
                .andExpect(jsonPath("$.playback_id").value(matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
                )))
                .andExpect(jsonPath("$.mode").value("recorded_replay"))
                .andExpect(jsonPath("$.delivery").value(
                        "synchronous_recorded_playback"
                ))
                .andExpect(jsonPath("$.truth_label").value(
                        IncidentLabReplayResponse.TRUTH_LABEL
                ))
                .andExpect(jsonPath("$.recorded_plan.contract_version").value(
                        "incident-lab-plan-v1"
                ))
                .andExpect(jsonPath("$.recorded_plan.provider_receipt").value(
                        nullValue()
                ))
                .andExpect(jsonPath("$.recorded_run.contract_version").value(
                        "incident-lab-run-v3"
                ))
                .andExpect(jsonPath("$.recorded_run.answer_state").value(
                        "diagnosed"
                ))
                .andExpect(jsonPath(
                        "$.recorded_run.developer_response"
                                + ".highlighted_log_evidence_ids[0]"
                ).value("test-log-catalog-cache"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.workflow.type"
                ).value("sequential_agent"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.tool_events[2]"
                                + ".runbook_retrieval.backend"
                ).value("pgvector_exact_cosine"))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.receipt.model_calls"
                ).value(2))
                .andExpect(jsonPath(
                        "$.recorded_run.agent_turn.receipt.embedding_calls"
                ).value(1))
                .andExpect(jsonPath(
                        "$.provenance.recording_source"
                ).value("test_contract_fixture"))
                .andExpect(jsonPath(
                        "$.provenance.resource_sha256_verified_at_startup"
                ).value(true))
                .andExpect(jsonPath(
                        "$.provenance.resource_sha256"
                ).value(matchesPattern("^[0-9a-f]{64}$")))
                .andExpect(jsonPath(
                        "$.playback_receipt.provider_calls"
                ).value(0))
                .andExpect(jsonPath(
                        "$.playback_receipt.model_calls"
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
                ).value(nullValue()))
                .andExpect(jsonPath(
                        "$.playback_receipt.cost_status"
                ).value("not_incurred"))
                .andReturn();

        GlobalDailyLiveQuota.Snapshot after = dailyQuota.snapshot(
                DAILY_START_LIMIT,
                DAILY_BUDGET_MICRO_USD
        );
        assertEquals(before.consumed(), after.consumed());
        assertEquals(before.consumedMicroUsd(), after.consumedMicroUsd());

        MvcResult second = mockMvc.perform(post(
                        "/api/v1/incident-lab/runs/recorded-replay"
                ))
                .andExpect(status().isOk())
                .andReturn();
        assertNotEquals(playbackId(first), playbackId(second));
    }

    @Test
    void doesNotServeTheConfiguredFixtureAsAStaticResource() throws Exception {
        mockMvc.perform(get(
                        "/incident-lab/replays/test-golden-v1.json"
                ))
                .andExpect(status().isNotFound());
    }

    private String playbackId(MvcResult result) throws Exception {
        JsonNode body = jsonMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
        return body.get("playback_id").asText();
    }
}
