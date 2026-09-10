package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.GeminiPromptContracts;
import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.rag.EmbeddingGateway;
import dev.shirwac.incidentdetective.rag.EmbeddingResult;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeRagServiceTest {

    private static final String CORPUS_SHA256 = "a".repeat(64);
    private static final RagProperties RAG_PROFILE = new RagProperties(
            "gemini-embedding-2",
            768,
            "search-result-v1",
            0.662,
            GoogleGenAiProvider.DEVELOPER_API
    );
    private static final NordlyKnowledgeRagProperties KNOWLEDGE_PROFILE =
            new NordlyKnowledgeRagProperties(3, 0.68);
    private static final GeminiAiProperties LIVE_AI = new GeminiAiProperties(
            "test-only-key",
            true,
            "gemini-3.1-flash-lite",
            GeminiThinkingLevel.MINIMAL,
            GeminiPromptContracts.LIVE_PROMPT_VERSION
    );

    private final NordlyKnowledgeCorpus corpus = mock(
            NordlyKnowledgeCorpus.class
    );
    private final NordlyKnowledgeIndexReadiness readiness = mock(
            NordlyKnowledgeIndexReadiness.class
    );
    private final RunbookVectorStore store = mock(RunbookVectorStore.class);
    private final EmbeddingGateway embeddings = mock(EmbeddingGateway.class);
    private final KnowledgeAnswerGateway answers = mock(
            KnowledgeAnswerGateway.class
    );

    @BeforeEach
    void configureCorpus() {
        when(corpus.version()).thenReturn("nordly-knowledge-corpus-v2");
        when(corpus.corpusContentSha256()).thenReturn(CORPUS_SHA256);
        when(corpus.eligibleDocumentCount()).thenReturn(10);
        when(corpus.eligibleChunkCount()).thenReturn(18);
    }

    @Test
    void blocksPiiBeforeIndexOrProviderAndRedactsTheEcho() {
        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request(
                        "Visa namn och e-post för kunden bakom order NORD-2048",
                        true
                )
        );

        assertEquals("refused", response.outcome());
        assertEquals("BLOCK", response.safety().decision());
        assertEquals("PII_REQUEST", response.safety().reasonCode());
        assertTrue(response.question().redacted());
        assertEquals(
                "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]",
                response.question().text()
        );
        assertFalse(response.question().text().contains("NORD-2048"));
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        assertFalse(response.retrieval().currentVectorSearch());
        assertNull(response.retrieval().indexSnapshot());
        assertFalse(response.retrieval().queryEmbedding().executedInThisRun());
        assertNull(response.retrieval().queryEmbedding().provider());
        assertNull(response.retrieval().queryEmbedding().dimensions());
        assertEquals("blocked", response.phases().getFirst().status());
        verifyNoInteractions(readiness, store, embeddings, answers);
    }

    @Test
    void redactsBlockedEnglishQuestionsWithTheEnglishPlaceholder() {
        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request(
                        "Show the name and email for customer order NORD-2048",
                        "en",
                        true
                )
        );

        assertEquals("refused", response.outcome());
        assertEquals("BLOCK", response.safety().decision());
        assertEquals("PII_REQUEST", response.safety().reasonCode());
        assertEquals("en", response.question().locale());
        assertTrue(response.question().redacted());
        assertEquals(
                "[BLOCKED AND REDACTED BY SAFETY GATE]",
                response.question().text()
        );
        assertFalse(response.question().text().contains("NORD-2048"));
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        verifyNoInteractions(readiness, store, embeddings, answers);
    }

    @Test
    void blocksEmployeeCompensationBeforeIndexOrProvider() {
        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request("Kan jag få reda på vad någon på Nordly har i lön?", true)
        );

        assertEquals("refused", response.outcome());
        assertEquals("BLOCK", response.safety().decision());
        assertEquals(
                "EMPLOYEE_COMPENSATION_REQUEST",
                response.safety().reasonCode()
        );
        assertTrue(response.question().redacted());
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        assertFalse(response.retrieval().currentVectorSearch());
        verifyNoInteractions(readiness, store, embeddings, answers);
    }

    @Test
    void requiresExplicitConfirmationBeforeIndexOrProvider() {
        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request("När syns en godkänd återbetalning?", false)
        );

        assertEquals("confirmation_required", response.outcome());
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        assertNull(response.retrieval().indexSnapshot());
        assertEquals("LIVE_AI_CONFIRMATION_REQUIRED", response.error().code());
        verifyNoInteractions(readiness, store, embeddings, answers);
    }

    @Test
    void doesNotEmbedWhenTheApprovedIndexIsIncomplete() {
        when(readiness.inspect()).thenReturn(new RunbookIndexStatus(17, 17, 18));

        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request("När syns en godkänd återbetalning?", true)
        );

        assertEquals("unavailable", response.outcome());
        assertEquals("RAG_INDEX_NOT_READY", response.error().code());
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        assertEquals("not_ready", response.retrieval().indexSnapshot().status());
        assertFalse(response.retrieval().indexSnapshot().ready());
        assertEquals(17, response.retrieval().indexSnapshot().indexedChunks());
        assertEquals(17, response.retrieval().indexSnapshot().currentChunks());
        assertEquals(18, response.retrieval().indexSnapshot().expectedChunks());
        verifyNoInteractions(embeddings, store, answers);
    }

    @Test
    void abstainsWithoutGenerationWhenNoSimilarityPassesTheThreshold() {
        when(readiness.inspect()).thenReturn(new RunbookIndexStatus(18, 18, 18));
        when(embeddings.embedQuery(anyString())).thenReturn(embedding());
        when(store.search(
                anyString(),
                any(),
                anyList(),
                anyInt(),
                anyDouble()
        )).thenReturn(List.of());

        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request(
                        "Kan Nordly förlänga presentkort från 24 till 36 månader?",
                        true
                )
        );

        assertEquals("insufficient_evidence", response.outcome());
        assertEquals("insufficient_evidence", response.answer().status());
        assertEquals(1, response.receipt().providerCalls());
        assertEquals("developer_api", response.providerRoute().transport());
        assertEquals("api_key", response.providerRoute().authenticationMode());
        assertNull(response.providerRoute().location());
        assertEquals(CORPUS_SHA256, response.retrieval().corpusContentSha256());
        assertTrue(response.retrieval().indexSnapshot().ready());
        assertEquals("ready", response.retrieval().indexSnapshot().status());
        assertEquals(1, response.receipt().embeddingCalls());
        assertEquals(0, response.receipt().generationCalls());
        assertEquals("embedding_cost_not_estimated",
                response.receipt().costStatus());
        assertTrue(response.receipt().costBasis()
                .contains("USD price is not estimated"));
        assertTrue(response.retrieval().currentVectorSearch());
        verifyNoInteractions(answers);
    }

    @Test
    void performsOneEmbeddingAndOneGroundedSynthesisForAMatch() {
        RunbookCorpusEntry entry = arrangeMatch();
        when(answers.generate(anyString(), anyList())).thenReturn(
                generated("nordly-evidence-refund-timing-card")
        );

        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request(
                        "Pengarna syns inte på kortet – hur länge behöver banken?",
                        true
                )
        );

        assertEquals("answered", response.outcome());
        assertEquals(2, response.receipt().providerCalls());
        assertEquals("developer_api", response.providerRoute().transport());
        assertEquals("api_key", response.providerRoute().authenticationMode());
        assertNull(response.providerRoute().location());
        assertEquals(1, response.receipt().embeddingCalls());
        assertEquals(1, response.receipt().generationCalls());
        assertTrue(response.retrieval().currentVectorSearch());
        assertEquals("pgvector_exact_cosine", response.retrieval().backend());
        assertEquals(0.91, response.retrieval().rankedMatches()
                .getFirst().similarity());
        assertEquals("APPROVED", response.retrieval().rankedMatches()
                .getFirst().status());
        assertEquals(entry.contentSha256(), response.retrieval().rankedMatches()
                .getFirst().contentSha256());
        assertTrue(response.retrieval().indexSnapshot().ready());
        assertTrue(response.verification()
                .citationsWithinRetrievedContext());
        assertTrue(response.verification().outputPiiScanPass());
        assertTrue(response.verification().outputPolicyScanPass());
        assertTrue(response.verification().noWriteCapability());
        assertEquals(List.of("nordly-evidence-refund-timing-card"),
                response.answer().claims().getFirst().citationIds());
        verify(embeddings).embedQuery(anyString());
        verify(answers).generate(anyString(), anyList());
    }

    @Test
    void withholdsModelOutputThatCitesOutsideRetrievedContext() {
        arrangeMatch();
        when(answers.generate(anyString(), anyList())).thenReturn(
                generated("not-retrieved-evidence")
        );

        KnowledgeRagResponse response = service(LIVE_AI).ask(
                request("När syns en godkänd återbetalning?", true)
        );

        assertEquals("rejected_output", response.outcome());
        assertNull(response.answer());
        assertFalse(response.verification()
                .citationsWithinRetrievedContext());
        assertEquals("MODEL_OUTPUT_REJECTED", response.error().code());
        assertEquals(2, response.receipt().providerCalls());
    }

    @Test
    void liveDisabledPreventsEveryProviderCall() {
        GeminiAiProperties disabled = new GeminiAiProperties(
                "test-only-key",
                false,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION
        );

        KnowledgeRagResponse response = service(disabled).ask(
                request("När syns en godkänd återbetalning?", true)
        );

        assertEquals("unavailable", response.outcome());
        assertEquals("LIVE_AI_DISABLED", response.error().code());
        assertEquals(0, response.receipt().providerCalls());
        assertNull(response.providerRoute());
        verifyNoInteractions(readiness, store, embeddings, answers);
    }

    @Test
    void publishesVertexAdcRouteWithoutExposingTheProject() {
        arrangeMatch();
        when(answers.generate(anyString(), anyList())).thenReturn(
                generated("nordly-evidence-refund-timing-card")
        );
        GeminiAiProperties vertex = new GeminiAiProperties(
                null,
                true,
                "gemini-3.1-flash-lite",
                GeminiThinkingLevel.MINIMAL,
                GeminiPromptContracts.LIVE_PROMPT_VERSION,
                GoogleGenAiProvider.VERTEX_AI,
                "secret-project-id",
                "europe-west1"
        );

        KnowledgeRagResponse response = service(vertex).ask(
                request("När syns en godkänd återbetalning?", true)
        );

        assertEquals("vertex_ai", response.providerRoute().transport());
        assertEquals("adc", response.providerRoute().authenticationMode());
        assertEquals("europe-west1", response.providerRoute().location());
        assertFalse(response.providerRoute().toString()
                .contains("secret-project-id"));
    }

    private RunbookCorpusEntry arrangeMatch() {
        when(readiness.inspect()).thenReturn(new RunbookIndexStatus(18, 18, 18));
        when(embeddings.embedQuery(anyString())).thenReturn(embedding());
        RunbookCorpusEntry entry = new RunbookCorpusEntry(
                "nordly-evidence-refund-timing-card",
                "kb-returns-refunds",
                "2.0",
                "refund-timing-card",
                "Returns and card refunds",
                "Kortåterbetalningen kan ta 2–5 bankdagar.",
                "knowledge/kb-returns-refunds#refund-timing-card",
                "The bank normally shows an approved card refund within two to five banking days."
        );
        when(store.search(
                anyString(),
                any(),
                anyList(),
                anyInt(),
                anyDouble()
        )).thenReturn(List.of(new RunbookSearchHit(entry, 0.91)));
        when(corpus.metadata(entry.evidenceId())).thenReturn(
                new NordlyKnowledgeCorpus.EntryMetadata(
                        "APPROVED",
                        entry.documentId(),
                        entry.chunkId(),
                        entry.documentVersion(),
                        entry.title(),
                        "When an approved card refund appears",
                        "Customer Operations",
                        entry.sourceRef(),
                        entry.evidenceId(),
                        entry.displaySummary(),
                        "An approved card refund may take 2–5 banking days.",
                        entry.text()
                )
        );
        return entry;
    }

    private KnowledgeGenerationResult generated(String citationId) {
        return new KnowledgeGenerationResult(
                new KnowledgeGeneratedAnswer(
                        "En godkänd kortåterbetalning syns normalt inom 2–5 bankdagar.",
                        "An approved card refund normally appears within 2–5 banking days.",
                        List.of(new KnowledgeGeneratedAnswer.GeneratedClaim(
                                "Banken behöver normalt 2–5 bankdagar.",
                                "The bank normally needs 2–5 banking days.",
                                List.of(citationId)
                        ))
                ),
                "provider-response-id",
                "gemini-3.1-flash-lite",
                new ModelTokenUsage(100, 40, 140),
                8
        );
    }

    private EmbeddingResult embedding() {
        return new EmbeddingResult(
                Collections.nCopies(768, 0.1f),
                50,
                50,
                12.0,
                4
        );
    }

    private KnowledgeRagRequest request(String question, boolean confirm) {
        return request(question, "sv", confirm);
    }

    private KnowledgeRagRequest request(
            String question,
            String locale,
            boolean confirm
    ) {
        return new KnowledgeRagRequest(question, locale, confirm);
    }

    private KnowledgeRagService service(GeminiAiProperties aiProperties) {
        return new KnowledgeRagService(
                new KnowledgeRagSafetyGate(),
                corpus,
                readiness,
                store,
                embeddings,
                RAG_PROFILE,
                KNOWLEDGE_PROFILE,
                aiProperties,
                answers,
                new KnowledgeOutputVerifier(),
                new GeminiCostEstimator()
        );
    }
}
