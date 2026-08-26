package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.ClasspathRunbookCorpus;
import dev.shirwac.incidentdetective.rag.EmbeddingGateway;
import dev.shirwac.incidentdetective.rag.EmbeddingResult;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import dev.shirwac.incidentdetective.rag.RunbookIndexReadiness;
import dev.shirwac.incidentdetective.rag.RunbookSearchHit;
import dev.shirwac.incidentdetective.rag.RunbookVectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
@Profile("rag")
public final class RunbookRetrievalEvaluator {

    private static final String UNSAFE_EVIDENCE_ID =
            "runbook-unsafe-legacy-instructions";
    private static final double THRESHOLD_MATCH_TOLERANCE = 1e-12;

    private final ClasspathRunbookRetrievalEvalSuite suiteResource;
    private final ClasspathRunbookCorpus corpus;
    private final RunbookVectorStore store;
    private final EmbeddingGateway embeddings;
    private final RagProperties properties;
    private final RunbookIndexReadiness indexReadiness;
    private final RunbookSimilarityThresholdCalibrator calibrator;

    public RunbookRetrievalEvaluator(
            ClasspathRunbookRetrievalEvalSuite suiteResource,
            ClasspathRunbookCorpus corpus,
            RunbookVectorStore store,
            EmbeddingGateway embeddings,
            RagProperties properties,
            RunbookIndexReadiness indexReadiness
    ) {
        this.suiteResource = suiteResource;
        this.corpus = corpus;
        this.store = store;
        this.embeddings = embeddings;
        this.properties = properties;
        this.indexReadiness = indexReadiness;
        calibrator = new RunbookSimilarityThresholdCalibrator();
    }

    public RunbookRetrievalEvalReport evaluate(String gitSha) {
        String normalizedGitSha = normalizeGitSha(gitSha);
        indexReadiness.requireReady();
        RunbookRetrievalEvalSuite suite = suiteResource.suite();
        long evaluationStarted = System.nanoTime();

        List<RawCaseResult> rawCases = suite.cases().stream()
                .map(evalCase -> retrieve(evalCase, suite.retrievalContract().topK()))
                .toList();
        RunbookSimilarityThresholdCalibrator.Calibration calibration =
                calibrator.calibrate(rawCases.stream()
                        .filter(raw -> "development".equals(raw.evalCase().split()))
                        .filter(raw -> !"adversarial".equals(
                                raw.evalCase().caseType()
                        ))
                        .map(this::asCalibrationCase)
                        .toList());
        double frozenThreshold = calibration.threshold();
        List<RunbookRetrievalEvalReport.CaseResult> caseResults = rawCases.stream()
                .map(raw -> score(raw, frozenThreshold))
                .toList();
        long evaluationLatencyMs = elapsedMillis(evaluationStarted);

        return new RunbookRetrievalEvalReport(
                suite.suiteVersion(),
                suiteResource.resourceSha256(),
                suite.corpusVersion(),
                corpusContentSha256(),
                normalizedGitSha,
                Instant.now(),
                "pgvector_exact_cosine",
                embeddingProfile(suite.retrievalContract()),
                suite.retrievalContract().topK(),
                properties.minimumSimilarity(),
                calibrationReport(calibration),
                Math.abs(properties.minimumSimilarity() - frozenThreshold)
                        <= THRESHOLD_MATCH_TOLERANCE,
                splitMetrics(caseResults, "development"),
                splitMetrics(caseResults, "held_out"),
                embeddingUsage(rawCases),
                new RunbookRetrievalEvalReport.CostEstimate(
                        null,
                        "not_calculated: provider usage metadata and billing tier are not both verified"
                ),
                false,
                "not_evaluated: retrieval of untrusted text is observed here; synthesis safety needs a separate pipeline eval",
                evaluationLatencyMs,
                caseResults
        );
    }

    private RawCaseResult retrieve(
            RunbookRetrievalEvalSuite.EvalCase evalCase,
            int topK
    ) {
        long started = System.nanoTime();
        EmbeddingResult embedding = embeddings.embedQuery(evalCase.query());
        List<RunbookSearchHit> hits = store.search(
                corpus.version(),
                properties,
                embedding.values(),
                topK,
                -1.0
        );
        return new RawCaseResult(
                evalCase,
                embedding,
                hits,
                elapsedMillis(started)
        );
    }

    private RunbookSimilarityThresholdCalibrator.CalibrationCase asCalibrationCase(
            RawCaseResult raw
    ) {
        return new RunbookSimilarityThresholdCalibrator.CalibrationCase(
                raw.evalCase().expectedEmpty(),
                Set.copyOf(raw.evalCase().relevantEvidenceIds()),
                raw.hits()
        );
    }

    private RunbookRetrievalEvalReport.CaseResult score(
            RawCaseResult raw,
            double threshold
    ) {
        var evalCase = raw.evalCase();
        List<RunbookRetrievalEvalReport.Hit> hits = new ArrayList<>();
        Integer firstRelevantRank = null;
        int acceptedCount = 0;
        for (int index = 0; index < raw.hits().size(); index++) {
            RunbookSearchHit hit = raw.hits().get(index);
            int rank = index + 1;
            boolean accepted = hit.cosineSimilarity() >= threshold;
            boolean relevant = evalCase.relevantEvidenceIds().contains(
                    hit.entry().evidenceId()
            );
            if (accepted) {
                acceptedCount++;
                if (relevant && firstRelevantRank == null) {
                    firstRelevantRank = rank;
                }
            }
            hits.add(new RunbookRetrievalEvalReport.Hit(
                    rank,
                    hit.entry().evidenceId(),
                    hit.cosineSimilarity(),
                    accepted,
                    relevant
            ));
        }
        boolean expectationMet = evalCase.expectedEmpty()
                ? acceptedCount == 0
                : firstRelevantRank != null;
        return new RunbookRetrievalEvalReport.CaseResult(
                evalCase.caseId(),
                evalCase.split(),
                evalCase.caseType(),
                evalCase.scenarioId(),
                evalCase.relevantEvidenceIds(),
                evalCase.expectedEmpty(),
                expectationMet,
                firstRelevantRank,
                firstRelevantRank == null ? 0.0 : 1.0 / firstRelevantRank,
                acceptedCount,
                raw.embedding().inputCharacters(),
                raw.embedding().providerBillableCharacters(),
                raw.embedding().providerInputTokens(),
                raw.embedding().latencyMs(),
                raw.retrievalLatencyMs(),
                evalCase.safetyFollowUp(),
                hits
        );
    }

    private RunbookRetrievalEvalReport.SplitMetrics splitMetrics(
            List<RunbookRetrievalEvalReport.CaseResult> cases,
            String split
    ) {
        List<RunbookRetrievalEvalReport.CaseResult> splitCases = cases.stream()
                .filter(evalCase -> split.equals(evalCase.split()))
                .toList();
        List<RunbookRetrievalEvalReport.CaseResult> positives = splitCases.stream()
                .filter(evalCase -> "positive".equals(evalCase.caseType()))
                .toList();
        List<RunbookRetrievalEvalReport.CaseResult> noMatches = splitCases.stream()
                .filter(evalCase -> "no_match".equals(evalCase.caseType()))
                .toList();
        long positiveHits = positives.stream()
                .filter(RunbookRetrievalEvalReport.CaseResult::retrievalExpectationMet)
                .count();
        double reciprocalRankSum = positives.stream()
                .mapToDouble(RunbookRetrievalEvalReport.CaseResult::reciprocalRank)
                .sum();
        long correctNoMatches = noMatches.stream()
                .filter(RunbookRetrievalEvalReport.CaseResult::retrievalExpectationMet)
                .count();
        long unsafeTop1 = positives.stream()
                .filter(evalCase -> !evalCase.hits().isEmpty())
                .filter(evalCase -> UNSAFE_EVIDENCE_ID.equals(
                        evalCase.hits().getFirst().evidenceId()
                ))
                .count();
        return new RunbookRetrievalEvalReport.SplitMetrics(
                split,
                positives.size(),
                Math.toIntExact(positiveHits),
                ratio(positiveHits, positives.size()),
                reciprocalRankSum / positives.size(),
                noMatches.size(),
                Math.toIntExact(correctNoMatches),
                ratio(correctNoMatches, noMatches.size()),
                ratio(unsafeTop1, positives.size())
        );
    }

    private RunbookRetrievalEvalReport.EmbeddingUsage embeddingUsage(
            List<RawCaseResult> cases
    ) {
        long localInputCharacters = cases.stream()
                .mapToLong(raw -> raw.embedding().inputCharacters())
                .sum();
        boolean billableComplete = cases.stream().allMatch(raw ->
                raw.embedding().providerBillableCharacters() != null
        );
        boolean tokensComplete = cases.stream().allMatch(raw ->
                raw.embedding().providerInputTokens() != null
        );
        Integer providerBillableCharacters = billableComplete
                ? Math.toIntExact(cases.stream()
                .mapToLong(raw -> raw.embedding().providerBillableCharacters())
                .sum())
                : null;
        Double providerInputTokens = tokensComplete
                ? cases.stream()
                .mapToDouble(raw -> raw.embedding().providerInputTokens())
                .sum()
                : null;
        long providerLatencyMs = cases.stream()
                .mapToLong(raw -> raw.embedding().latencyMs())
                .sum();
        return new RunbookRetrievalEvalReport.EmbeddingUsage(
                cases.size(),
                localInputCharacters,
                providerBillableCharacters,
                providerInputTokens,
                billableComplete && tokensComplete,
                providerLatencyMs
        );
    }

    private RunbookRetrievalEvalReport.EmbeddingProfile embeddingProfile(
            RunbookRetrievalEvalSuite.RetrievalContract contract
    ) {
        return new RunbookRetrievalEvalReport.EmbeddingProfile(
                contract.provider(),
                contract.model(),
                contract.dimensions(),
                contract.embeddingFormatVersion(),
                contract.queryInputFormat(),
                contract.documentInputFormat()
        );
    }

    private RunbookRetrievalEvalReport.Calibration calibrationReport(
            RunbookSimilarityThresholdCalibrator.Calibration calibration
    ) {
        return new RunbookRetrievalEvalReport.Calibration(
                "development",
                "0.5 * Hit@4 + 0.5 * no-match accuracy",
                "higher no-match accuracy, then higher MRR, then lower threshold",
                calibration.threshold(),
                calibration.objective(),
                calibration.hitAtK(),
                calibration.meanReciprocalRank(),
                calibration.noMatchAccuracy(),
                calibration.candidateCount()
        );
    }

    private String corpusContentSha256() {
        String content = corpus.version() + "\n" + corpus.entries().stream()
                .sorted(java.util.Comparator.comparing(RunbookCorpusEntry::evidenceId))
                .map(entry -> entry.evidenceId() + ":" + entry.contentSha256())
                .collect(java.util.stream.Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    content.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeGitSha(String gitSha) {
        if (gitSha == null || gitSha.isBlank()) {
            return "unknown-local";
        }
        String normalized = gitSha.trim();
        if (!normalized.matches("[0-9a-f]{7,40}")) {
            throw new IllegalArgumentException("git SHA must contain 7 to 40 hex characters");
        }
        return normalized;
    }

    private long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private double ratio(long numerator, int denominator) {
        return (double) numerator / denominator;
    }

    private record RawCaseResult(
            RunbookRetrievalEvalSuite.EvalCase evalCase,
            EmbeddingResult embedding,
            List<RunbookSearchHit> hits,
            long retrievalLatencyMs
    ) {
        private RawCaseResult {
            hits = List.copyOf(hits);
        }
    }
}
