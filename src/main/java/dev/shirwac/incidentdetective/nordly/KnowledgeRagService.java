package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GeminiCostEstimator;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.rag.EmbeddingGateway;
import dev.shirwac.incidentdetective.rag.EmbeddingResult;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookEmbeddingException;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("rag")
public final class KnowledgeRagService {

    private static final String TRUTH_LABEL =
            "LIVE LOCAL RAG · SYNTETISKA NORDLY-DOKUMENT · "
                    + "MAX 1 EMBEDDING + 1 GEMINI-SYNTES · INGA SKRIVVERKTYG";
    private static final String TRUTH_LABEL_EN =
            "LIVE LOCAL RAG · SYNTHETIC NORDLY DOCUMENTS · "
                    + "MAX 1 EMBEDDING + 1 GEMINI SYNTHESIS · NO WRITE TOOLS";
    private static final String REDACTED_QUESTION_SV =
            "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]";
    private static final String REDACTED_QUESTION_EN =
            "[BLOCKED AND REDACTED BY SAFETY GATE]";
    private static final List<String> PHASE_ORDER = List.of(
            "safety",
            "eligibility_filter",
            "query_embedding",
            "vector_search",
            "bounded_context",
            "generation",
            "java_verification"
    );
    private static final List<String> LIMITATIONS = List.of(
            "All company documents, customer identifiers and examples are synthetic.",
            "The deterministic input gate and output pattern scan are narrow public-demo controls, not complete DLP or detection of every name and address.",
            "Cosine similarity is a ranking signal, not answer confidence.",
            "Java verifies structure, retrieved citation IDs and forbidden output patterns; it does not prove semantic entailment of every claim.",
            "The Nordly similarity threshold is a demo setting and is not yet production-calibrated.",
            "Estimated generation cost excludes embedding price and is not a provider invoice."
    );

    private final KnowledgeRagSafetyGate safetyGate;
    private final NordlyKnowledgeCorpus corpus;
    private final NordlyKnowledgeIndexReadiness readiness;
    private final RunbookVectorStore store;
    private final EmbeddingGateway embeddings;
    private final RagProperties ragProperties;
    private final NordlyKnowledgeRagProperties knowledgeProperties;
    private final GeminiAiProperties aiProperties;
    private final KnowledgeAnswerGateway answerGateway;
    private final KnowledgeOutputVerifier verifier;
    private final GeminiCostEstimator costEstimator;

    public KnowledgeRagService(
            KnowledgeRagSafetyGate safetyGate,
            NordlyKnowledgeCorpus corpus,
            NordlyKnowledgeIndexReadiness readiness,
            RunbookVectorStore store,
            EmbeddingGateway embeddings,
            RagProperties ragProperties,
            NordlyKnowledgeRagProperties knowledgeProperties,
            GeminiAiProperties aiProperties,
            KnowledgeAnswerGateway answerGateway,
            KnowledgeOutputVerifier verifier,
            GeminiCostEstimator costEstimator
    ) {
        this.safetyGate = safetyGate;
        this.corpus = corpus;
        this.readiness = readiness;
        this.store = store;
        this.embeddings = embeddings;
        this.ragProperties = ragProperties;
        this.knowledgeProperties = knowledgeProperties;
        this.aiProperties = aiProperties;
        this.answerGateway = answerGateway;
        this.verifier = verifier;
        this.costEstimator = costEstimator;
    }

    public KnowledgeRagResponse ask(KnowledgeRagRequest request) {
        long runStarted = System.nanoTime();
        String runId = "nordly-rag-" + UUID.randomUUID();
        List<KnowledgeRagResponse.PhaseEvent> phases = new ArrayList<>();

        KnowledgeRagSafetyGate.Decision safety = safetyGate.evaluate(
                request.question()
        );
        KnowledgeRagResponse.SafetyDecision safetyResponse = safety(safety);
        if (!safety.allowed()) {
            phases.add(phase(
                    "safety",
                    "blocked",
                    true,
                    safety.summarySv(),
                    safety.summaryEn(),
                    null
            ));
            appendSkipped(phases, "eligibility_filter");
            return response(
                    runId,
                    "refused",
                    new KnowledgeRagResponse.SubmittedQuestion(
                            "sv".equals(request.locale())
                                    ? REDACTED_QUESTION_SV
                                    : REDACTED_QUESTION_EN,
                            request.locale(),
                            true
                    ),
                    safetyResponse,
                    phases,
                    emptyRetrieval(),
                    new KnowledgeRagResponse.Answer(
                            "refused",
                            safety.summarySv(),
                            safety.summaryEn(),
                            List.of()
                    ),
                    verification(true, true, true, true,
                            "refused_before_provider"),
                    receipt(0, 0, elapsedMs(runStarted), null, null, null),
                    null
            );
        }

        phases.add(phase(
                "safety",
                "completed",
                true,
                safety.summarySv(),
                safety.summaryEn(),
                null
        ));
        KnowledgeRagResponse.SubmittedQuestion submittedQuestion =
                new KnowledgeRagResponse.SubmittedQuestion(
                        request.question().strip(),
                        request.locale(),
                        false
                );

        if (!request.confirmLiveAi()) {
            appendSkipped(phases, "eligibility_filter");
            return response(
                    runId,
                    "confirmation_required",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    emptyRetrieval(),
                    null,
                    verification(false, true, true, true,
                            "not_run_confirmation_required"),
                    receipt(0, 0, elapsedMs(runStarted), null, null, null),
                    error(
                            "LIVE_AI_CONFIRMATION_REQUIRED",
                            "Bekräfta den begränsade livekörningen först.",
                            "Confirm the bounded live run first."
                    )
            );
        }
        if (!aiProperties.liveEnabled()) {
            appendSkipped(phases, "eligibility_filter");
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    emptyRetrieval(),
                    null,
                    verification(false, true, true, true,
                            "not_run_live_ai_disabled"),
                    receipt(0, 0, elapsedMs(runStarted), null, null, null),
                    error(
                            "LIVE_AI_DISABLED",
                            "Live AI är avstängd i servern.",
                            "Live AI is disabled on the server."
                    )
            );
        }
        if (!aiProperties.hasApiKey()) {
            appendSkipped(phases, "eligibility_filter");
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    emptyRetrieval(),
                    null,
                    verification(false, true, true, true,
                            "not_run_provider_not_configured"),
                    receipt(0, 0, elapsedMs(runStarted), null, null, null),
                    error(
                            "RAG_EMBEDDING_NOT_CONFIGURED",
                            "Embedding-provider saknas i serverkonfigurationen.",
                            "The embedding provider is not configured on the server."
                    )
            );
        }

        long eligibilityStarted = System.nanoTime();
        try {
            RunbookIndexStatus status = readiness.inspect();
            if (!status.ready()) {
                phases.add(phase(
                        "eligibility_filter",
                        "failed",
                        true,
                        "Endast APPROVED + public_demo valdes, men indexet är inte komplett ("
                                + status.currentChunks() + "/"
                                + status.expectedChunks() + ").",
                        "Only APPROVED + public_demo was selected, but the index is incomplete ("
                                + status.currentChunks() + "/"
                                + status.expectedChunks() + ").",
                        elapsedMs(eligibilityStarted)
                ));
                appendSkipped(phases, "query_embedding");
                return response(
                        runId,
                        "unavailable",
                        submittedQuestion,
                        safetyResponse,
                        phases,
                        emptyRetrieval(),
                        null,
                        verification(false, true, true, true,
                                "not_run_index_not_ready"),
                        receipt(0, 0, elapsedMs(runStarted), null, null, null),
                        error(
                                "RAG_INDEX_NOT_READY",
                                "Nordlys godkända kunskapsindex är inte färdigt.",
                                "Nordly's approved knowledge index is not ready."
                        )
                );
            }
        } catch (DataAccessException exception) {
            phases.add(phase(
                    "eligibility_filter",
                    "failed",
                    true,
                    "Kunskapsindexet kunde inte läsas.",
                    "The knowledge index could not be read.",
                    elapsedMs(eligibilityStarted)
            ));
            appendSkipped(phases, "query_embedding");
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    emptyRetrieval(),
                    null,
                    verification(false, true, true, true,
                            "not_run_database_unavailable"),
                    receipt(0, 0, elapsedMs(runStarted), null, null, null),
                    error(
                            "RAG_DATABASE_UNAVAILABLE",
                            "Vector-databasen är tillfälligt otillgänglig.",
                            "The vector database is temporarily unavailable."
                    )
            );
        }
        phases.add(phase(
                "eligibility_filter",
                "completed",
                true,
                corpus.eligibleDocumentCount() + " godkända dokument och "
                        + corpus.eligibleChunkCount()
                        + " textdelar får delta i rankningen.",
                corpus.eligibleDocumentCount() + " approved documents and "
                        + corpus.eligibleChunkCount()
                        + " chunks may enter ranking.",
                elapsedMs(eligibilityStarted)
        ));

        EmbeddingResult queryEmbedding;
        long embeddingStarted = System.nanoTime();
        try {
            queryEmbedding = embeddings.embedQuery(request.question().strip());
        } catch (RunbookEmbeddingException exception) {
            phases.add(phase(
                    "query_embedding",
                    "failed",
                    true,
                    "Frågan kunde inte omvandlas till en embedding.",
                    "The question could not be converted into an embedding.",
                    elapsedMs(embeddingStarted)
            ));
            appendSkipped(phases, "vector_search");
            String code = switch (exception.failure()) {
                case CONFIGURATION -> "RAG_EMBEDDING_NOT_CONFIGURED";
                case UPSTREAM -> "RAG_EMBEDDING_PROVIDER_ERROR";
                case MALFORMED_RESPONSE -> "RAG_EMBEDDING_RESPONSE_INVALID";
            };
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    retrieval(null, false, List.of()),
                    null,
                    verification(false, true, true, true,
                            "embedding_failed"),
                    receipt(
                            1,
                            0,
                            elapsedMs(runStarted),
                            null,
                            null,
                            "provider_call_failed_cost_unknown"
                    ),
                    error(
                            code,
                            "Embedding-steget misslyckades utan att någon vectorsökning kördes.",
                            "The embedding step failed before any vector search ran."
                    )
            );
        }
        phases.add(phase(
                "query_embedding",
                "completed",
                true,
                "Frågan omvandlades till 768 tal för semantisk jämförelse.",
                "The question was converted into 768 numbers for semantic comparison.",
                queryEmbedding.latencyMs()
        ));

        List<RunbookSearchHit> hits;
        long searchStarted = System.nanoTime();
        try {
            hits = store.search(
                    corpus.version(),
                    ragProperties,
                    queryEmbedding.values(),
                    knowledgeProperties.topK(),
                    knowledgeProperties.minimumSimilarity()
            );
        } catch (DataAccessException exception) {
            phases.add(phase(
                    "vector_search",
                    "failed",
                    true,
                    "Vectorsökningen kunde inte slutföras.",
                    "The vector search could not be completed.",
                    elapsedMs(searchStarted)
            ));
            appendSkipped(phases, "bounded_context");
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    retrieval(queryEmbedding, false, List.of()),
                    null,
                    verification(false, true, true, true,
                            "vector_database_unavailable"),
                    receipt(
                            1,
                            0,
                            elapsedMs(runStarted),
                            null,
                            null,
                            "embedding_cost_not_estimated"
                    ),
                    error(
                            "RAG_DATABASE_UNAVAILABLE",
                            "Vector-databasen är tillfälligt otillgänglig.",
                            "The vector database is temporarily unavailable."
                    )
            );
        }
        List<KnowledgeRagResponse.RankedMatch> matches = mapMatches(hits);
        String vectorSummarySv = matches.isEmpty()
                ? "Ingen textdel passerade similarity-tröskeln."
                : matches.size() == 1
                ? "1 textdel rankades med exakt cosine similarity."
                : matches.size() + " textdelar rankades med exakt cosine similarity.";
        String vectorSummaryEn = matches.isEmpty()
                ? "No chunk passed the similarity threshold."
                : matches.size() == 1
                ? "1 chunk was ranked with exact cosine similarity."
                : matches.size() + " chunks were ranked with exact cosine similarity.";
        phases.add(phase(
                "vector_search",
                "completed",
                true,
                vectorSummarySv,
                vectorSummaryEn,
                elapsedMs(searchStarted)
        ));

        if (matches.isEmpty()) {
            phases.add(phase(
                    "bounded_context",
                    "completed",
                    true,
                    "Ingen osäker källa skickades vidare som modellkontext.",
                    "No uncertain source was sent forward as model context.",
                    null
            ));
            phases.add(skipped("generation"));
            phases.add(phase(
                    "java_verification",
                    "completed",
                    true,
                    "Java valde att avstå i stället för att gissa.",
                    "Java abstained instead of guessing.",
                    null
            ));
            return response(
                    runId,
                    "insufficient_evidence",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    retrieval(queryEmbedding, true, matches),
                    new KnowledgeRagResponse.Answer(
                            "insufficient_evidence",
                            "Jag hittar ingen tillräckligt relevant godkänd källa i Nordlys dokument.",
                            "I cannot find a sufficiently relevant approved source in Nordly's documents.",
                            List.of()
                    ),
                    verification(true, true, true, true,
                            "abstained_below_similarity_threshold"),
                    receipt(
                            1,
                            0,
                            elapsedMs(runStarted),
                            null,
                            null,
                            "embedding_cost_not_estimated"
                    ),
                    null
            );
        }

        phases.add(phase(
                "bounded_context",
                "completed",
                true,
                "Endast de rankade, godkända textdelarna skickades till modellen.",
                "Only the ranked, approved chunks were sent to the model.",
                null
        ));

        KnowledgeGenerationResult generated;
        long generationStarted = System.nanoTime();
        try {
            generated = answerGateway.generate(request.question().strip(), matches);
        } catch (ModelProviderException exception) {
            phases.add(phase(
                    "generation",
                    "failed",
                    true,
                    "Gemini kunde inte skapa ett begränsat svar.",
                    "Gemini could not create a bounded answer.",
                    elapsedMs(generationStarted)
            ));
            phases.add(skipped("java_verification"));
            String code = switch (exception.failure()) {
                case TIMEOUT -> "MODEL_PROVIDER_TIMEOUT";
                case RATE_LIMITED -> "MODEL_PROVIDER_RATE_LIMITED";
                case UPSTREAM -> "MODEL_PROVIDER_ERROR";
                case MALFORMED_RESPONSE -> "MALFORMED_MODEL_RESPONSE";
            };
            return response(
                    runId,
                    "unavailable",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    retrieval(queryEmbedding, true, matches),
                    null,
                    verification(false, true, true, true,
                            "generation_failed"),
                    receipt(
                            1,
                            1,
                            elapsedMs(runStarted),
                            null,
                            null,
                            "provider_calls_cost_not_reported"
                    ),
                    error(
                            code,
                            "Kunskapskällorna hittades, men modellsvaret kunde inte användas.",
                            "Knowledge sources were found, but the model answer could not be used."
                    )
            );
        }
        phases.add(phase(
                "generation",
                "completed",
                true,
                "Gemini skapade ett kort svar utan verktyg eller dold tankekedja.",
                "Gemini created a concise answer without tools or hidden reasoning output.",
                generated.latencyMs()
        ));

        long verificationStarted = System.nanoTime();
        KnowledgeOutputVerifier.Result verification = verifier.verify(
                generated.answer(),
                matches
        );
        ModelCostEstimate estimate = costEstimator.estimate(
                aiProperties.modelId(),
                generated.tokenUsage()
        );
        if (!verification.passed()) {
            phases.add(phase(
                    "java_verification",
                    "blocked",
                    true,
                    "Java stoppade svaret eftersom utdata inte klarade alla kontroller.",
                    "Java blocked the answer because the output did not pass every check.",
                    elapsedMs(verificationStarted)
            ));
            return response(
                    runId,
                    "rejected_output",
                    submittedQuestion,
                    safetyResponse,
                    phases,
                    retrieval(queryEmbedding, true, matches),
                    null,
                    verification(
                            verification.schemaPass(),
                            verification.citationsWithinRetrievedContext(),
                            verification.approvedDocumentsOnly(),
                            verification.outputPiiScanPass(),
                            verification.outputPolicyScanPass(),
                            "model_output_rejected"
                    ),
                    receipt(
                            1,
                            1,
                            elapsedMs(runStarted),
                            generated,
                            estimate.estimatedUsd(),
                            estimate.estimatedUsd() == null
                                    ? "provider_usage_not_reported"
                                    : "estimated_generation_only"
                    ),
                    error(
                            "MODEL_OUTPUT_REJECTED",
                            "Modellsvaret lämnade aldrig backend eftersom Java-kontrollen stoppade det.",
                            "The model answer never left the backend because Java verification blocked it."
                    )
            );
        }

        phases.add(phase(
                "java_verification",
                "completed",
                true,
                "Java verifierade schema, citerade käll-ID:n och förbjudna datamönster.",
                "Java verified the schema, cited source IDs and forbidden data patterns.",
                elapsedMs(verificationStarted)
        ));
        return response(
                runId,
                "answered",
                submittedQuestion,
                safetyResponse,
                phases,
                retrieval(queryEmbedding, true, matches),
                answer(generated.answer()),
                verification(true, true, true, true,
                        "answered_with_verified_retrieved_citations"),
                receipt(
                        1,
                        1,
                        elapsedMs(runStarted),
                        generated,
                        estimate.estimatedUsd(),
                        estimate.estimatedUsd() == null
                                ? "provider_usage_not_reported"
                                : "estimated_generation_only"
                ),
                null
        );
    }

    private List<KnowledgeRagResponse.RankedMatch> mapMatches(
            List<RunbookSearchHit> hits
    ) {
        List<KnowledgeRagResponse.RankedMatch> matches = new ArrayList<>();
        Set<String> seenEvidence = new HashSet<>();
        int rank = 1;
        for (RunbookSearchHit hit : hits) {
            NordlyKnowledgeCorpus.EntryMetadata metadata = corpus.metadata(
                    hit.entry().evidenceId()
            );
            if (!seenEvidence.add(metadata.evidenceId())) {
                throw new IllegalStateException(
                        "Nordly vector search returned duplicate evidence"
                );
            }
            matches.add(new KnowledgeRagResponse.RankedMatch(
                    rank++,
                    hit.cosineSimilarity(),
                    metadata.status(),
                    metadata.documentId(),
                    metadata.chunkId(),
                    metadata.documentVersion(),
                    metadata.title(),
                    metadata.sectionHeading(),
                    metadata.ownerTeam(),
                    metadata.sourceRef(),
                    metadata.evidenceId(),
                    metadata.displaySummarySv(),
                    metadata.displaySummaryEn(),
                    metadata.text()
            ));
        }
        return List.copyOf(matches);
    }

    private KnowledgeRagResponse.RetrievalResult emptyRetrieval() {
        return retrieval(null, false, List.of());
    }

    private KnowledgeRagResponse.RetrievalResult retrieval(
            EmbeddingResult embedding,
            boolean currentVectorSearch,
            List<KnowledgeRagResponse.RankedMatch> matches
    ) {
        KnowledgeRagResponse.QueryEmbedding queryEmbedding = embedding == null
                ? new KnowledgeRagResponse.QueryEmbedding(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        )
                : new KnowledgeRagResponse.QueryEmbedding(
                true,
                "google_genai",
                ragProperties.embeddingModel(),
                ragProperties.embeddingDimensions(),
                embedding.latencyMs(),
                embedding.inputCharacters(),
                embedding.providerBillableCharacters(),
                embedding.providerInputTokens()
        );
        return new KnowledgeRagResponse.RetrievalResult(
                KnowledgeRagResponse.BACKEND,
                corpus.version(),
                NordlyKnowledgeCorpus.REQUIRED_LIFECYCLE,
                NordlyKnowledgeCorpus.REQUIRED_ACCESS_SCOPE,
                corpus.eligibleDocumentCount(),
                corpus.eligibleChunkCount(),
                currentVectorSearch,
                knowledgeProperties.topK(),
                knowledgeProperties.minimumSimilarity(),
                queryEmbedding,
                matches
        );
    }

    private KnowledgeRagResponse.Answer answer(
            KnowledgeGeneratedAnswer generated
    ) {
        return new KnowledgeRagResponse.Answer(
                "answered",
                generated.summarySv(),
                generated.summaryEn(),
                generated.claims().stream()
                        .map(claim -> new KnowledgeRagResponse.AnswerClaim(
                                claim.textSv(),
                                claim.textEn(),
                                claim.citationIds()
                        ))
                        .toList()
        );
    }

    private KnowledgeRagResponse response(
            String runId,
            String outcome,
            KnowledgeRagResponse.SubmittedQuestion question,
            KnowledgeRagResponse.SafetyDecision safety,
            List<KnowledgeRagResponse.PhaseEvent> phases,
            KnowledgeRagResponse.RetrievalResult retrieval,
            KnowledgeRagResponse.Answer answer,
            KnowledgeRagResponse.Verification verification,
            KnowledgeRagResponse.Receipt receipt,
            KnowledgeRagResponse.ErrorDetail error
    ) {
        return new KnowledgeRagResponse(
                KnowledgeRagResponse.CONTRACT_VERSION,
                runId,
                KnowledgeRagResponse.MODE,
                TRUTH_LABEL,
                TRUTH_LABEL_EN,
                outcome,
                question,
                safety,
                phases,
                retrieval,
                answer,
                verification,
                receipt,
                error,
                LIMITATIONS
        );
    }

    private KnowledgeRagResponse.SafetyDecision safety(
            KnowledgeRagSafetyGate.Decision decision
    ) {
        return new KnowledgeRagResponse.SafetyDecision(
                decision.allowed() ? "ALLOW" : "BLOCK",
                decision.reasonCode().name(),
                decision.summarySv(),
                decision.summaryEn()
        );
    }

    private KnowledgeRagResponse.Verification verification(
            boolean schemaPass,
            boolean citationsWithinContext,
            boolean approvedOnly,
            boolean outputPiiScanPass,
            String outcome
    ) {
        return verification(
                schemaPass,
                citationsWithinContext,
                approvedOnly,
                outputPiiScanPass,
                true,
                outcome
        );
    }

    private KnowledgeRagResponse.Verification verification(
            boolean schemaPass,
            boolean citationsWithinContext,
            boolean approvedOnly,
            boolean outputPiiScanPass,
            boolean outputPolicyScanPass,
            String outcome
    ) {
        return new KnowledgeRagResponse.Verification(
                schemaPass,
                citationsWithinContext,
                approvedOnly,
                outputPiiScanPass,
                outputPolicyScanPass,
                true,
                outcome
        );
    }

    private KnowledgeRagResponse.Receipt receipt(
            int embeddingCalls,
            int generationCalls,
            long totalLatencyMs,
            KnowledgeGenerationResult generated,
            BigDecimal estimatedCost,
            String costStatus
    ) {
        return new KnowledgeRagResponse.Receipt(
                embeddingCalls + generationCalls,
                embeddingCalls,
                generationCalls,
                false,
                false,
                totalLatencyMs,
                generated == null
                        ? null
                        : generated.modelVersion() == null
                        ? aiProperties.modelId()
                        : generated.modelVersion(),
                generated == null ? null : generated.providerResponseId(),
                generated == null ? null : generated.tokenUsage(),
                estimatedCost,
                costStatus == null
                        ? embeddingCalls + generationCalls == 0
                        ? "not_incurred"
                        : "provider_usage_not_reported"
                        : costStatus,
                costBasis(
                        embeddingCalls,
                        generationCalls,
                        generated,
                        costStatus
                )
        );
    }

    private String costBasis(
            int embeddingCalls,
            int generationCalls,
            KnowledgeGenerationResult generated,
            String costStatus
    ) {
        if (embeddingCalls + generationCalls == 0) {
            return "No provider call was made.";
        }
        if (generated != null) {
            return costEstimator.estimate(
                    aiProperties.modelId(),
                    generated.tokenUsage()
            ).basis() + " Embedding price is excluded.";
        }
        if ("embedding_cost_not_estimated".equals(costStatus)) {
            return "One embedding completed; its USD price is not estimated by this receipt.";
        }
        return "A provider call was attempted, but billable usage and actual charged cost were not reported.";
    }

    private KnowledgeRagResponse.ErrorDetail error(
            String code,
            String summarySv,
            String summaryEn
    ) {
        return new KnowledgeRagResponse.ErrorDetail(code, summarySv, summaryEn);
    }

    private KnowledgeRagResponse.PhaseEvent phase(
            String id,
            String status,
            boolean executed,
            String summarySv,
            String summaryEn,
            Long latencyMs
    ) {
        return new KnowledgeRagResponse.PhaseEvent(
                id,
                status,
                executed,
                summarySv,
                summaryEn,
                latencyMs
        );
    }

    private KnowledgeRagResponse.PhaseEvent skipped(String id) {
        return phase(
                id,
                "skipped",
                false,
                "Steget kördes inte.",
                "This phase did not run.",
                null
        );
    }

    private void appendSkipped(
            List<KnowledgeRagResponse.PhaseEvent> phases,
            String firstId
    ) {
        int start = PHASE_ORDER.indexOf(firstId);
        if (start < 0) {
            throw new IllegalArgumentException("Unknown Nordly RAG phase " + firstId);
        }
        for (int index = start; index < PHASE_ORDER.size(); index++) {
            phases.add(skipped(PHASE_ORDER.get(index)));
        }
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
