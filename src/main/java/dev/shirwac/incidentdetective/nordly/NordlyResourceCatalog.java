package dev.shirwac.incidentdetective.nordly;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public final class NordlyResourceCatalog {

    static final String WORLD_RESOURCE = "demo/nordly-demo-world-v1.json";
    static final String CORPUS_RESOURCE =
            "knowledge/nordly-knowledge-corpus-v2.json";
    static final String REPLAY_RESOURCE =
            "knowledge/replays/nordly-knowledge-replays-v1.json";

    private static final Set<String> LIFECYCLES = Set.of(
            "APPROVED",
            "DEPRECATED",
            "UNTRUSTED"
    );
    private static final Set<String> ANSWER_STATUSES = Set.of(
            "answered",
            "insufficient_evidence",
            "refused"
    );
    private static final Set<String> RAG_EXAMPLE_CATEGORIES = Set.of(
            "SAFE",
            "PII",
            "EMPLOYEE_COMPENSATION",
            "SECRET",
            "PROMPT_INJECTION",
            "FINANCIAL_ACTION",
            "NO_MATCH"
    );
    private static final Set<String> RAG_EXAMPLE_BOUNDARIES = Set.of(
            "rag",
            "blocked_before_provider",
            "insufficient_evidence"
    );

    private final DemoWorldResponse world;
    private final KnowledgeCorpusManifest corpus;
    private final Map<String, KnowledgeReplayResponse> replays;

    public NordlyResourceCatalog(JsonMapper jsonMapper) {
        DemoWorldResponse loadedWorld = read(
                jsonMapper,
                WORLD_RESOURCE,
                DemoWorldResponse.class
        );
        KnowledgeCorpusManifest loadedCorpus = read(
                jsonMapper,
                CORPUS_RESOURCE,
                KnowledgeCorpusManifest.class
        );
        KnowledgeReplayFixtureCollection fixtures = read(
                jsonMapper,
                REPLAY_RESOURCE,
                KnowledgeReplayFixtureCollection.class
        );

        validateWorldAndCorpus(loadedWorld, loadedCorpus);
        Map<String, KnowledgeReplayResponse> loadedReplays = materializeReplays(
                loadedWorld,
                loadedCorpus,
                fixtures
        );
        validateReplayCoverage(loadedWorld, loadedReplays);

        world = loadedWorld;
        corpus = loadedCorpus;
        replays = Map.copyOf(loadedReplays);
    }

    public DemoWorldResponse world() {
        return world;
    }

    public KnowledgeReplayResponse replay(String questionId) {
        KnowledgeReplayResponse replay = replays.get(questionId);
        if (replay == null) {
            throw new KnowledgeQuestionNotFoundException(questionId);
        }
        return replay;
    }

    KnowledgeCorpusManifest knowledgeManifest() {
        return corpus;
    }

    private static Map<String, KnowledgeReplayResponse> materializeReplays(
            DemoWorldResponse world,
            KnowledgeCorpusManifest corpus,
            KnowledgeReplayFixtureCollection fixtures
    ) {
        requireEqual(
                KnowledgeReplayFixtureCollection.COLLECTION_VERSION,
                fixtures.collectionVersion(),
                "replay collection version"
        );
        requireNonEmpty(fixtures.replays(), "replays");

        Map<String, DemoWorldResponse.DemoQuestion> questionsById =
                indexQuestions(world.questions());
        Map<ChunkKey, CorpusChunk> chunksById = indexCorpusChunks(corpus);
        Map<String, KnowledgeReplayResponse> responses = new LinkedHashMap<>();

        for (KnowledgeReplayFixtureCollection.ReplayFixture fixture
                : fixtures.replays()) {
            requireNonBlank(fixture.questionId(), "replay question ID");
            DemoWorldResponse.DemoQuestion question = questionsById.get(
                    fixture.questionId()
            );
            if (question == null) {
                throw invalid("replay references unknown question "
                        + fixture.questionId());
            }

            KnowledgeReplayResponse response = materializeReplay(
                    corpus,
                    question,
                    fixture,
                    chunksById
            );
            validateReplay(response);
            if (responses.putIfAbsent(question.id(), response) != null) {
                throw invalid("duplicate replay question " + question.id());
            }
        }
        return responses;
    }

    private static KnowledgeReplayResponse materializeReplay(
            KnowledgeCorpusManifest corpus,
            DemoWorldResponse.DemoQuestion question,
            KnowledgeReplayFixtureCollection.ReplayFixture fixture,
            Map<ChunkKey, CorpusChunk> chunksById
    ) {
        requireNonEmptyOrEmptyList(fixture.rankedMatches(), "ranked matches");
        List<KnowledgeReplayResponse.RankedMatch> matches = fixture.rankedMatches()
                .stream()
                .map(reference -> materializeMatch(reference, chunksById))
                .toList();

        KnowledgeReplayFixtureCollection.AnswerFixture answer = fixture.answer();
        if (answer == null) {
            throw invalid("replay answer must be present");
        }
        KnowledgeReplayFixtureCollection.VerificationFixture verification =
                fixture.verification();
        if (verification == null) {
            throw invalid("replay verification must be present");
        }
        KnowledgeReplayFixtureCollection.ReceiptFixture receipt = fixture.receipt();
        if (receipt == null) {
            throw invalid("replay receipt must be present");
        }

        KnowledgeCorpusManifest.CorpusEmbeddingProfile embedding =
                corpus.embeddingProfile();
        return new KnowledgeReplayResponse(
                fixture.contractVersion(),
                fixture.runId(),
                fixture.mode(),
                fixture.truthLabel(),
                fixture.modelBacked(),
                fixture.currentVectorSearch(),
                new KnowledgeReplayResponse.ReplayQuestion(
                        question.id(),
                        question.promptSv(),
                        question.promptEn()
                ),
                new KnowledgeReplayResponse.RetrievalSnapshot(
                        "recorded_snapshot",
                        corpus.corpusVersion(),
                        new KnowledgeReplayResponse.EmbeddingProfile(
                                embedding.provider(),
                                embedding.modelId(),
                                embedding.dimensions()
                        ),
                        new KnowledgeReplayResponse.QueryEmbeddingSnapshot(
                                false,
                                null
                        ),
                        matches
                ),
                new KnowledgeReplayResponse.KnowledgeAnswer(
                        answer.status(),
                        answer.summarySv(),
                        answer.summaryEn(),
                        answer.claims()
                ),
                new KnowledgeReplayResponse.ReplayVerification(
                        verification.schemaPass(),
                        verification.citationsSeen(),
                        verification.approvedDocumentsOnly(),
                        verification.claimSupportPass(),
                        verification.actionWithinScope(),
                        verification.overallOutcome()
                ),
                new KnowledgeReplayResponse.ReplayReceipt(
                        receipt.toolCalls(),
                        receipt.writeToolsAvailable(),
                        receipt.actionExecuted(),
                        receipt.latencyMs(),
                        receipt.totalTokens(),
                        receipt.estimatedCostUsd(),
                        receipt.costStatus()
                ),
                fixture.limitations()
        );
    }

    private static KnowledgeReplayResponse.RankedMatch materializeMatch(
            KnowledgeReplayFixtureCollection.MatchReference reference,
            Map<ChunkKey, CorpusChunk> chunksById
    ) {
        CorpusChunk corpusChunk = chunksById.get(new ChunkKey(
                reference.documentId(),
                reference.chunkId()
        ));
        if (corpusChunk == null) {
            throw invalid("replay references unknown chunk "
                    + reference.documentId() + "#" + reference.chunkId());
        }
        KnowledgeCorpusManifest.KnowledgeDocument document =
                corpusChunk.document();
        KnowledgeCorpusManifest.KnowledgeChunk chunk = corpusChunk.chunk();
        return new KnowledgeReplayResponse.RankedMatch(
                reference.rank(),
                reference.similarity(),
                document.lifecycle(),
                document.id(),
                chunk.id(),
                document.version(),
                document.title(),
                chunk.sectionHeading(),
                document.ownerTeam(),
                chunk.sourceRef(),
                chunk.evidenceId(),
                chunk.displaySummarySv(),
                chunk.displaySummaryEn(),
                chunk.text()
        );
    }

    private static void validateWorldAndCorpus(
            DemoWorldResponse world,
            KnowledgeCorpusManifest corpus
    ) {
        requireEqual(
                DemoWorldResponse.CONTRACT_VERSION,
                world.contractVersion(),
                "demo world contract version"
        );
        requireNonBlank(world.truthLabel(), "demo world truth label");
        requireNonBlank(world.truthLabelEn(), "English demo world truth label");
        if (world.company() == null) {
            throw invalid("company must be present");
        }
        requireNonBlank(world.company().id(), "company ID");
        requireNonBlank(world.company().displayName(), "company display name");
        requireNonBlank(world.company().storefrontName(), "storefront name");
        requireNonBlank(world.company().description(), "company description");
        requireNonBlank(world.company().descriptionEn(), "English company description");
        requireNonBlank(world.company().industry(), "company industry");
        requireNonBlank(world.company().industryEn(), "English company industry");
        requireNonEmpty(world.company().markets(), "company markets");
        requireUniqueNonBlank(world.company().markets(), "company market");
        requireNonEmpty(world.company().marketsEn(), "English company markets");
        requireUniqueNonBlank(world.company().marketsEn(), "English company market");
        if (world.company().markets().size() != world.company().marketsEn().size()) {
            throw invalid("English company markets must match company market count");
        }

        requireNonEmpty(world.services(), "services");
        Set<String> serviceIds = new HashSet<>();
        for (DemoWorldResponse.ServiceProfile service : world.services()) {
            if (service == null) {
                throw invalid("services must not contain null");
            }
            requireNonBlank(service.id(), "service ID");
            requireNonBlank(service.displayName(), "service display name");
            requireNonBlank(service.displayNameEn(), "English service display name");
            requireNonBlank(service.role(), "service role");
            requireNonBlank(service.roleEn(), "English service role");
            if (!serviceIds.add(service.id())) {
                throw invalid("duplicate service ID " + service.id());
            }
        }

        requireNonEmpty(world.questions(), "questions");
        indexQuestions(world.questions());
        validateRagExamples(world.ragExamples());
        requireNonBlank(world.featuredScenarioId(), "featured scenario ID");

        requireEqual(
                KnowledgeCorpusManifest.MANIFEST_VERSION,
                corpus.manifestVersion(),
                "knowledge manifest version"
        );
        requireNonBlank(corpus.truthLabel(), "knowledge truth label");
        requireNonBlank(corpus.corpusVersion(), "knowledge corpus version");
        if (corpus.embeddingProfile() == null) {
            throw invalid("corpus embedding profile must be present");
        }
        requireNonBlank(
                corpus.embeddingProfile().provider(),
                "embedding provider"
        );
        requireNonBlank(
                corpus.embeddingProfile().modelId(),
                "embedding model ID"
        );
        if (corpus.embeddingProfile().dimensions() != 768) {
            throw invalid("Nordly embedding dimensions must be 768");
        }

        CorpusCounts actualCounts = validateCorpus(corpus);
        DemoWorldResponse.CorpusSummary summary = world.corpus();
        if (summary == null) {
            throw invalid("demo corpus summary must be present");
        }
        requireEqual(corpus.corpusVersion(), summary.version(), "corpus version");
        if (summary.documentCount() != actualCounts.documents()
                || summary.chunkCount() != actualCounts.chunks()
                || summary.approvedDocuments() != actualCounts.approved()
                || summary.deprecatedDocuments() != actualCounts.deprecated()
                || summary.untrustedDocuments() != actualCounts.untrusted()) {
            throw invalid("demo corpus counts do not match the manifest");
        }
    }

    private static CorpusCounts validateCorpus(KnowledgeCorpusManifest corpus) {
        requireNonEmpty(corpus.documents(), "knowledge documents");
        Set<String> documentIds = new HashSet<>();
        Set<String> chunkIds = new HashSet<>();
        Set<String> sourceRefs = new HashSet<>();
        Set<String> evidenceIds = new HashSet<>();
        Map<String, List<String>> relatedDocuments = new LinkedHashMap<>();
        int chunks = 0;
        int approved = 0;
        int deprecated = 0;
        int untrusted = 0;

        for (KnowledgeCorpusManifest.KnowledgeDocument document
                : corpus.documents()) {
            if (document == null) {
                throw invalid("knowledge documents must not contain null");
            }
            requireNonBlank(document.id(), "document ID");
            requireNonBlank(document.version(), "document version");
            requireNonBlank(document.displayFilename(), "display filename");
            requireNonBlank(document.documentType(), "document type");
            requireNonBlank(document.classification(), "document classification");
            requireNonBlank(document.title(), "document title");
            requireNonBlank(document.titleSv(), "Swedish document title");
            requireNonBlank(document.summarySv(), "Swedish document summary");
            requireNonBlank(document.summaryEn(), "English document summary");
            requireNonBlank(document.ownerTeam(), "document owner team");
            requireNonBlank(document.lifecycle(), "document lifecycle");
            if (!documentIds.add(document.id())) {
                throw invalid("duplicate document ID " + document.id());
            }
            if (!LIFECYCLES.contains(document.lifecycle())) {
                throw invalid("unsupported document lifecycle "
                        + document.lifecycle());
            }
            switch (document.lifecycle()) {
                case "APPROVED" -> approved++;
                case "DEPRECATED" -> deprecated++;
                case "UNTRUSTED" -> untrusted++;
                default -> throw invalid("unsupported document lifecycle");
            }
            validateEffectiveDates(document);
            requireNonEmpty(document.accessScopes(), "document access scopes");
            requireUniqueNonBlank(document.accessScopes(), "document access scope");
            requireNonEmptyOrEmptyList(
                    document.relatedDocumentIds(),
                    "related document IDs"
            );
            requireUniqueNonBlank(
                    document.relatedDocumentIds(),
                    "related document ID"
            );
            if (document.relatedDocumentIds().contains(document.id())) {
                throw invalid("document cannot relate to itself " + document.id());
            }
            relatedDocuments.put(document.id(), document.relatedDocumentIds());
            requireNonEmpty(document.chunks(), "document chunks");

            for (KnowledgeCorpusManifest.KnowledgeChunk chunk
                    : document.chunks()) {
                if (chunk == null) {
                    throw invalid("knowledge chunks must not contain null");
                }
                requireNonBlank(chunk.id(), "chunk ID");
                requireNonBlank(chunk.sectionHeading(), "section heading");
                requireNonBlank(chunk.sourceRef(), "chunk source ref");
                requireNonBlank(chunk.evidenceId(), "chunk evidence ID");
                requireNonBlank(
                        chunk.displaySummarySv(),
                        "Swedish chunk summary"
                );
                requireNonBlank(
                        chunk.displaySummaryEn(),
                        "English chunk summary"
                );
                requireNonBlank(chunk.text(), "chunk text");

                String qualifiedChunkId = document.id() + "#" + chunk.id();
                if (!chunkIds.add(qualifiedChunkId)) {
                    throw invalid("duplicate chunk identity " + qualifiedChunkId);
                }
                if (!sourceRefs.add(chunk.sourceRef())) {
                    throw invalid("duplicate source ref " + chunk.sourceRef());
                }
                if (!evidenceIds.add(chunk.evidenceId())) {
                    throw invalid("duplicate evidence ID " + chunk.evidenceId());
                }
                String expectedSourceRef = "knowledge/" + document.id()
                        + "#" + chunk.id();
                requireEqual(expectedSourceRef, chunk.sourceRef(), "source ref");
                chunks++;
            }
        }

        for (Map.Entry<String, List<String>> relationship
                : relatedDocuments.entrySet()) {
            for (String relatedId : relationship.getValue()) {
                if (!documentIds.contains(relatedId)) {
                    throw invalid("document " + relationship.getKey()
                            + " relates to unknown document " + relatedId);
                }
            }
        }

        return new CorpusCounts(
                corpus.documents().size(),
                chunks,
                approved,
                deprecated,
                untrusted
        );
    }

    private static void validateEffectiveDates(
            KnowledgeCorpusManifest.KnowledgeDocument document
    ) {
        LocalDate from = parseDate(
                document.effectiveFrom(),
                document.id() + " effective_from"
        );
        if (document.effectiveUntil() == null) {
            return;
        }
        LocalDate until = parseDate(
                document.effectiveUntil(),
                document.id() + " effective_until"
        );
        if (until.isBefore(from)) {
            throw invalid("document effective_until precedes effective_from for "
                    + document.id());
        }
    }

    private static LocalDate parseDate(String value, String field) {
        requireNonBlank(value, field);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw invalid(field + " must use ISO-8601 date format");
        }
    }

    private static Map<String, DemoWorldResponse.DemoQuestion> indexQuestions(
            List<DemoWorldResponse.DemoQuestion> questions
    ) {
        requireNonEmpty(questions, "questions");
        Map<String, DemoWorldResponse.DemoQuestion> questionsById =
                new LinkedHashMap<>();
        for (DemoWorldResponse.DemoQuestion question : questions) {
            if (question == null) {
                throw invalid("questions must not contain null");
            }
            requireNonBlank(question.id(), "question ID");
            requireNonBlank(question.promptSv(), "Swedish question prompt");
            requireNonBlank(question.promptEn(), "English question prompt");
            requireNonBlank(
                    question.customerContextSv(),
                    "Swedish customer context"
            );
            requireNonBlank(
                    question.customerContextEn(),
                    "English customer context"
            );
            if (!ANSWER_STATUSES.contains(question.outcome())) {
                throw invalid("unsupported question outcome " + question.outcome());
            }
            if (question.estimatedSeconds() < 1) {
                throw invalid("question estimated seconds must be positive");
            }
            if (questionsById.putIfAbsent(question.id(), question) != null) {
                throw invalid("duplicate question ID " + question.id());
            }
        }
        return questionsById;
    }

    private static void validateRagExamples(
            List<DemoWorldResponse.RagExample> examples
    ) {
        requireNonEmpty(examples, "RAG examples");
        Set<String> ids = new HashSet<>();
        for (DemoWorldResponse.RagExample example : examples) {
            if (example == null) {
                throw invalid("RAG examples must not contain null");
            }
            requireNonBlank(example.id(), "RAG example ID");
            requireNonBlank(example.category(), "RAG example category");
            requireNonBlank(example.promptSv(), "Swedish RAG example prompt");
            requireNonBlank(example.promptEn(), "English RAG example prompt");
            requireNonBlank(
                    example.expectedBoundary(),
                    "RAG example expected boundary"
            );
            if (!ids.add(example.id())) {
                throw invalid("duplicate RAG example ID " + example.id());
            }
            if (!RAG_EXAMPLE_CATEGORIES.contains(example.category())) {
                throw invalid("unsupported RAG example category "
                        + example.category());
            }
            if (!RAG_EXAMPLE_BOUNDARIES.contains(example.expectedBoundary())) {
                throw invalid("unsupported RAG example boundary "
                        + example.expectedBoundary());
            }
        }
    }

    private static Map<ChunkKey, CorpusChunk> indexCorpusChunks(
            KnowledgeCorpusManifest corpus
    ) {
        Map<ChunkKey, CorpusChunk> chunks = new HashMap<>();
        for (KnowledgeCorpusManifest.KnowledgeDocument document
                : corpus.documents()) {
            for (KnowledgeCorpusManifest.KnowledgeChunk chunk
                    : document.chunks()) {
                chunks.put(
                        new ChunkKey(document.id(), chunk.id()),
                        new CorpusChunk(document, chunk)
                );
            }
        }
        return chunks;
    }

    private static void validateReplay(KnowledgeReplayResponse replay) {
        requireEqual(
                KnowledgeReplayResponse.CONTRACT_VERSION,
                replay.contractVersion(),
                "knowledge replay contract version"
        );
        requireEqual(
                KnowledgeReplayResponse.MODE,
                replay.mode(),
                "knowledge replay mode"
        );
        requireNonBlank(replay.runId(), "replay run ID");
        requireNonBlank(replay.truthLabel(), "replay truth label");
        if (replay.modelBacked() || replay.currentVectorSearch()) {
            throw invalid("recorded replay must not claim current AI or vector work");
        }
        if (replay.question() == null
                || replay.retrieval() == null
                || replay.answer() == null
                || replay.verification() == null
                || replay.receipt() == null) {
            throw invalid("replay sections must be present");
        }
        requireEqual(
                "recorded_snapshot",
                replay.retrieval().backend(),
                "replay retrieval backend"
        );
        if (replay.retrieval().queryEmbedding() == null
                || replay.retrieval().queryEmbedding().executedInThisRun()
                || replay.retrieval().queryEmbedding().latencyMs() != null) {
            throw invalid("replay query embedding must report no current execution");
        }

        List<KnowledgeReplayResponse.RankedMatch> matches =
                replay.retrieval().rankedMatches();
        requireNonEmptyOrEmptyList(matches, "ranked matches");
        Set<String> evidenceIds = new HashSet<>();
        int expectedRank = 1;
        for (KnowledgeReplayResponse.RankedMatch match : matches) {
            if (match == null) {
                throw invalid("ranked matches must not contain null");
            }
            if (match.rank() != expectedRank++) {
                throw invalid("ranked match ranks must be contiguous from one");
            }
            if (match.similarity() != null) {
                throw invalid("recorded snapshot must not invent similarity values");
            }
            requireEqual("APPROVED", match.status(), "ranked match status");
            if (!evidenceIds.add(match.evidenceId())) {
                throw invalid("duplicate ranked evidence ID " + match.evidenceId());
            }
        }

        validateAnswer(replay, evidenceIds);
        validateReceipt(replay);
        requireNonEmpty(replay.limitations(), "replay limitations");
    }

    private static void validateAnswer(
            KnowledgeReplayResponse replay,
            Set<String> evidenceIds
    ) {
        KnowledgeReplayResponse.KnowledgeAnswer answer = replay.answer();
        requireNonBlank(answer.status(), "answer status");
        if (!ANSWER_STATUSES.contains(answer.status())) {
            throw invalid("unsupported answer status " + answer.status());
        }
        requireNonBlank(answer.summarySv(), "Swedish answer summary");
        requireNonBlank(answer.summaryEn(), "English answer summary");
        requireNonEmptyOrEmptyList(answer.claims(), "answer claims");

        Set<String> citations = new HashSet<>();
        for (KnowledgeReplayResponse.AnswerClaim claim : answer.claims()) {
            if (claim == null) {
                throw invalid("answer claims must not contain null");
            }
            requireNonBlank(claim.textSv(), "Swedish answer claim");
            requireNonBlank(claim.textEn(), "English answer claim");
            requireNonEmpty(claim.citationIds(), "claim citations");
            for (String citationId : claim.citationIds()) {
                requireNonBlank(citationId, "citation ID");
                if (!evidenceIds.contains(citationId)) {
                    throw invalid("claim cites unknown evidence " + citationId);
                }
                citations.add(citationId);
            }
        }

        KnowledgeReplayResponse.ReplayVerification verification =
                replay.verification();
        if (!verification.schemaPass()
                || !verification.approvedDocumentsOnly()
                || !verification.claimSupportPass()) {
            throw invalid("published replay verification must pass");
        }
        if (verification.citationsSeen() != citations.size()) {
            throw invalid("verification citation count does not match claims");
        }

        switch (answer.status()) {
            case "answered" -> {
                if (answer.claims().isEmpty() || evidenceIds.isEmpty()) {
                    throw invalid("answered replay requires claims and evidence");
                }
                if (!verification.actionWithinScope()) {
                    throw invalid("answered replay must stay within read-only scope");
                }
            }
            case "insufficient_evidence" -> {
                if (!answer.claims().isEmpty()
                        || !evidenceIds.isEmpty()
                        || verification.citationsSeen() != 0
                        || !verification.actionWithinScope()) {
                    throw invalid("insufficient evidence replay must abstain cleanly");
                }
            }
            case "refused" -> {
                if (!answer.claims().isEmpty()
                        || verification.citationsSeen() != 0
                        || verification.actionWithinScope()) {
                    throw invalid("refused replay must reject the requested action");
                }
            }
            default -> throw invalid("unsupported answer status");
        }
    }

    private static void validateReceipt(KnowledgeReplayResponse replay) {
        KnowledgeReplayResponse.ReplayReceipt receipt = replay.receipt();
        requireNonEmptyOrEmptyList(receipt.toolCalls(), "receipt tool calls");
        if (!receipt.toolCalls().isEmpty()) {
            throw invalid("current replay request must not claim tool calls");
        }
        if (receipt.writeToolsAvailable() || receipt.actionExecuted()) {
            throw invalid("knowledge replay must never expose or execute write tools");
        }
        if (receipt.latencyMs() != null
                || receipt.totalTokens() != null
                || receipt.estimatedCostUsd() != null) {
            throw invalid("replay runtime, token and cost values must be null");
        }
        requireEqual("not_incurred", receipt.costStatus(), "cost status");
    }

    private static void validateReplayCoverage(
            DemoWorldResponse world,
            Map<String, KnowledgeReplayResponse> replays
    ) {
        for (DemoWorldResponse.DemoQuestion question : world.questions()) {
            if (question.replayAvailable() != replays.containsKey(question.id())) {
                throw invalid("question replay availability does not match resource "
                        + question.id());
            }
            KnowledgeReplayResponse replay = replays.get(question.id());
            if (replay != null
                    && !question.outcome().equals(replay.answer().status())) {
                throw invalid("question outcome does not match replay "
                        + question.id());
            }
        }
    }

    private static <T> T read(
            JsonMapper jsonMapper,
            String resourcePath,
            Class<T> type
    ) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.isReadable()) {
            throw invalid("resource is not readable: " + resourcePath);
        }
        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(input);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load Nordly resource " + resourcePath,
                    exception
            );
        }
    }

    private static void requireNonEmpty(List<?> values, String field) {
        if (values == null || values.isEmpty()) {
            throw invalid(field + " must not be empty");
        }
    }

    private static void requireNonEmptyOrEmptyList(
            List<?> values,
            String field
    ) {
        if (values == null) {
            throw invalid(field + " must be present");
        }
    }

    private static void requireUniqueNonBlank(
            List<String> values,
            String field
    ) {
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            requireNonBlank(value, field);
            if (!unique.add(value)) {
                throw invalid("duplicate " + field + " " + value);
            }
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " must not be blank");
        }
    }

    private static void requireEqual(
            String expected,
            String actual,
            String field
    ) {
        if (!expected.equals(actual)) {
            throw invalid(field + " must be " + expected);
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid Nordly resources: " + message);
    }

    private record ChunkKey(String documentId, String chunkId) {
    }

    private record CorpusChunk(
            KnowledgeCorpusManifest.KnowledgeDocument document,
            KnowledgeCorpusManifest.KnowledgeChunk chunk
    ) {
    }

    private record CorpusCounts(
            int documents,
            int chunks,
            int approved,
            int deprecated,
            int untrusted
    ) {
    }
}
