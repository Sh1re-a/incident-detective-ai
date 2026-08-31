package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.EmbeddingCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ModeCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ToolBudgetCapability;
import dev.shirwac.incidentdetective.investigation.tools.RetrieveRunbooksArguments;
import dev.shirwac.incidentdetective.investigation.tools.RetrieveRunbooksResult;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalStrategy;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.LiveInvestigationService;
import dev.shirwac.incidentdetective.live.PromptCacheStrategy;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.replay.RecordedReplayService;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitiesServiceTest {

    private static final RagProperties RAG = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.6620781500197453
    );

    @Test
    void describesTheFixtureBoundaryAndEnforcedLiveLimits() {
        GeminiAiProperties ai = new GeminiAiProperties(
                "must-never-be-returned",
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                "gemini-live-v6"
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("replay");
        CapabilitiesResponse response = new CapabilitiesService(
                ai,
                retrieval(RunbookRetrievalBackend.DETERMINISTIC_FIXTURE),
                RAG,
                environment,
                quota(GlobalDailyLiveQuota.Scope.PROCESS_LOCAL)
        ).describe();

        assertEquals("capabilities-v2", response.contractVersion());
        assertTrue(response.syntheticOnly());
        assertFalse(response.remediationEnabled());
        assertEquals(List.of(
                new ModeCapability(
                        RunMode.RECORDED_REPLAY,
                        RecordedReplayService.TRUTH_LABEL,
                        false,
                        false
                ),
                new ModeCapability(
                        RunMode.LIVE_AI,
                        LiveInvestigationService.TRUTH_LABEL,
                        true,
                        true
                )
        ), response.modes());
        assertEquals(List.of(ToolName.values()), response.tools().stream()
                .map(CapabilitiesResponse.ToolCapability::name)
                .toList());
        assertTrue(response.tools().stream().allMatch(
                CapabilitiesResponse.ToolCapability::readOnly
        ));

        assertTrue(response.liveAi().enabledByConfiguration());
        assertTrue(response.liveAi().credentialsConfigured());
        assertTrue(response.liveAi().requestConfigured());
        assertTrue(response.liveAi().explicitConfirmationRequired());
        assertEquals("gemini-3.1-flash-lite", response.liveAi().modelId());
        assertEquals(GeminiThinkingLevel.MINIMAL, response.liveAi().thinkingLevel());
        assertEquals("gemini-live-v6", response.liveAi().promptVersion());
        assertEquals(2, response.liveAi().budget().maxCollectionRounds());
        assertEquals(8, response.liveAi().budget().maxToolCallsTotal());
        assertEquals(3, response.liveAi().budget().maxToolCallsPerRound());
        assertEquals(45_000, response.liveAi().budget().hardDeadlineMs());
        assertEquals(28_000, response.liveAi().budget().providerCallCapMs());
        assertEquals(20, response.liveAi().budget().dailyLiveRunLimit());
        assertEquals(
                GlobalDailyLiveQuota.Scope.PROCESS_LOCAL,
                response.liveAi().budget().dailyQuotaScope()
        );
        Map<ToolName, Integer> toolBudgets = response.liveAi()
                .budget()
                .maxCallsByTool()
                .stream()
                .collect(Collectors.toMap(
                        ToolBudgetCapability::tool,
                        ToolBudgetCapability::maxCallsPerInvestigation
                ));
        assertEquals(Map.of(
                ToolName.GET_METRICS, 1,
                ToolName.SEARCH_LOGS, 2,
                ToolName.GET_TRACE, 2,
                ToolName.RETRIEVE_RUNBOOKS, 1
        ), toolBudgets);

        assertTrue(response.generatedCases().enabled());
        assertFalse(response.generatedCases().userSuppliedDataAccepted());
        assertTrue(response.generatedCases().requestLocalOnly());
        assertEquals(
                LiveInvestigationService.GENERATED_TRUTH_LABEL,
                response.generatedCases().truthLabel()
        );
        assertEquals(
                List.of("diagnostic", "insufficient_evidence"),
                response.generatedCases().evidenceModes()
        );
        assertEquals(List.of("none", "low"), response.generatedCases().noiseLevels());
        assertEquals(
                List.of(ToolName.values()),
                response.generatedCases().allowedTools()
        );

        assertEquals(
                RunbookRetrievalBackend.DETERMINISTIC_FIXTURE,
                response.retrieval().backend()
        );
        assertEquals(List.of("replay"), response.retrieval().activeProfiles());
        assertFalse(response.retrieval().vectorDatabaseBackendActive());
        assertNull(response.retrieval().activeEmbeddingProfile());
        assertEquals(
                PromptCacheStrategy.PROVIDER_IMPLICIT,
                response.promptCache().strategy()
        );
        assertFalse(response.promptCache().explicitCachingEnabled());
        assertTrue(response.promptCache().cacheHitClaimsRequireProviderMetadata());
    }

    @Test
    void exposesTheEmbeddingProfileOnlyWhenPgvectorIsActive() {
        GeminiAiProperties ai = new GeminiAiProperties(
                "configured-but-disabled",
                false,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                "gemini-live-v6"
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("rag");

        CapabilitiesResponse response = new CapabilitiesService(
                ai,
                retrieval(RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE),
                RAG,
                environment,
                quota(GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL)
        ).describe();

        assertEquals(
                RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE,
                response.retrieval().backend()
        );
        assertEquals(List.of("rag"), response.retrieval().activeProfiles());
        assertTrue(response.retrieval().vectorDatabaseBackendActive());
        assertEquals(new EmbeddingCapability(
                "gemini-embedding-2",
                768,
                "search-result-v1",
                0.6620781500197453
        ), response.retrieval().activeEmbeddingProfile());
        assertTrue(response.liveAi().credentialsConfigured());
        assertFalse(response.liveAi().requestConfigured());
        assertEquals(
                GlobalDailyLiveQuota.Scope.DATABASE_GLOBAL,
                response.liveAi().budget().dailyQuotaScope()
        );
    }

    private GlobalDailyLiveQuota quota(GlobalDailyLiveQuota.Scope scope) {
        return new GlobalDailyLiveQuota() {
            @Override
            public Decision tryConsume(int dailyLimit) {
                throw new UnsupportedOperationException("Not used by capability test");
            }

            @Override
            public Scope scope() {
                return scope;
            }
        };
    }

    private RunbookRetrievalStrategy retrieval(
            RunbookRetrievalBackend backend
    ) {
        return new RunbookRetrievalStrategy() {
            @Override
            public RetrieveRunbooksResult retrieve(
                    String scenarioId,
                    RetrieveRunbooksArguments arguments
            ) {
                throw new UnsupportedOperationException("not needed by this test");
            }

            @Override
            public RunbookRetrievalBackend backend() {
                return backend;
            }

            @Override
            public String safeModeDescription() {
                return backend == RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE
                        ? "Gemini embeddings with exact pgvector cosine retrieval"
                        : "deterministic fixture retrieval";
            }

            @Override
            public String limitation() {
                return "Synthetic corpus only.";
            }
        };
    }
}
