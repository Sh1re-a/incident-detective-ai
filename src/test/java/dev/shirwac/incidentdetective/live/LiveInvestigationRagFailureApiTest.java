package dev.shirwac.incidentdetective.live;

import dev.shirwac.incidentdetective.ai.CollectionModelResult;
import dev.shirwac.incidentdetective.ai.CollectionToolCall;
import dev.shirwac.incidentdetective.ai.InvestigationModelGateway;
import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.investigation.tools.InvestigationToolExecutor;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingException;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingFailure;
import dev.shirwac.incidentdetective.rag.RunbookIndexNotReadyException;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "incident-detective.ai.live-enabled=true",
        "incident-detective.ai.gemini-api-key=test-only-key"
})
@AutoConfigureMockMvc
class LiveInvestigationRagFailureApiTest {

    private static final String PATH =
            "/api/v1/scenarios/{scenarioId}/runs/live-ai";
    private static final String SCENARIO_ID = "checkout-orders-at-risk-v1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationModelGateway model;

    @MockitoBean
    private InvestigationToolExecutor tools;

    @BeforeEach
    void prepareRunbookToolCall() {
        reset(model, tools);
        when(tools.availableMetricNames(SCENARIO_ID)).thenReturn(List.of());
        when(model.collect(
                any(), anyList(), anyList(), any(), eq(1), any()
        )).thenReturn(new CollectionModelResult(
                List.of(new CollectionToolCall(
                        "runbook-call-1",
                        ToolName.RETRIEVE_RUNBOOKS,
                        Map.of(
                                "query", "payment timeout",
                                "max_results", 1
                        )
                )),
                new ModelCallMetadata(
                        ModelPhase.COLLECT,
                        1,
                        "private-provider-response-id",
                        "private-provider-model-version",
                        null,
                        1
                )
        ));
    }

    @Test
    void mapsUnreadyRunbookIndexToSanitizedUnavailableProblem()
            throws Exception {
        assertApiProblem(
                new RunbookIndexNotReadyException(
                        new RunbookIndexStatus(11, 10, 12)
                ),
                status().isServiceUnavailable(),
                "RAG_INDEX_NOT_READY",
                "indexed 11"
        );
    }

    @Test
    void mapsEmbeddingProviderFailureToSanitizedBadGatewayProblem()
            throws Exception {
        assertApiProblem(
                new RunbookEmbeddingException(
                        RunbookEmbeddingFailure.UPSTREAM,
                        "private provider response with request-id request-123"
                ),
                status().isBadGateway(),
                "RAG_EMBEDDING_PROVIDER_ERROR",
                "request-123"
        );
    }

    @Test
    void mapsRunbookDatabaseFailureToSanitizedUnavailableProblem()
            throws Exception {
        assertApiProblem(
                new DataAccessResourceFailureException(
                        "private jdbc url jdbc:postgresql://secret-host/database"
                ),
                status().isServiceUnavailable(),
                "RAG_DATABASE_UNAVAILABLE",
                "secret-host"
        );
    }

    private void assertApiProblem(
            RuntimeException failure,
            ResultMatcher expectedStatus,
            String expectedCode,
            String privateText
    ) throws Exception {
        when(tools.execute(eq(SCENARIO_ID), any())).thenThrow(failure);

        MvcResult result = mockMvc.perform(post(PATH, SCENARIO_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm_live_ai\":true}"))
                .andExpect(expectedStatus)
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString()
                .contains(privateText));
        verify(model, never()).synthesize(any(), anyList(), any());
    }
}
