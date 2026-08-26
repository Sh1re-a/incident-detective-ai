package dev.shirwac.incidentdetective.rag.eval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Component
@Profile("rag")
public final class RunbookRetrievalEvalReportWriter {

    private static final Path DEFAULT_OUTPUT_DIRECTORY = Path.of("target", "evals");

    private final JsonMapper jsonMapper;

    public RunbookRetrievalEvalReportWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public OutputFiles write(RunbookRetrievalEvalReport report) {
        return write(report, DEFAULT_OUTPUT_DIRECTORY);
    }

    OutputFiles write(RunbookRetrievalEvalReport report, Path outputDirectory) {
        requireSafeVersion(report.suiteVersion());
        Path jsonPath = outputDirectory.resolve(report.suiteVersion() + ".json");
        Path markdownPath = outputDirectory.resolve(report.suiteVersion() + ".md");
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(
                    jsonPath,
                    jsonMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(report) + "\n",
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    markdownPath,
                    markdown(report),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write retrieval eval report", exception);
        }
        return new OutputFiles(jsonPath, markdownPath);
    }

    private String markdown(RunbookRetrievalEvalReport report) {
        StringBuilder output = new StringBuilder();
        output.append("# Runbook retrieval eval\n\n")
                .append("> Measured retrieval result. This is not a full diagnosis or prompt-injection safety eval.\n\n")
                .append("## Reproducibility\n\n")
                .append("- Suite: `").append(report.suiteVersion()).append("`\n")
                .append("- Suite SHA-256: `").append(report.suiteSha256()).append("`\n")
                .append("- Corpus: `").append(report.corpusVersion()).append("`\n")
                .append("- Corpus content SHA-256: `")
                .append(report.corpusContentSha256()).append("`\n")
                .append("- Git SHA: `").append(report.gitSha()).append("`\n")
                .append("- Backend: `").append(report.backend()).append("`\n")
                .append("- Embedding: `")
                .append(report.embeddingProfile().model()).append("`, ")
                .append(report.embeddingProfile().dimensions()).append(" dimensions\n")
                .append("- Executed at: `").append(report.executedAt()).append("`\n\n")
                .append("## Results\n\n")
                .append("| Split | Positive Hit@")
                .append(report.topK())
                .append(" | MRR | No-match accuracy | Unsafe top-1 on benign cases |\n")
                .append("|---|---:|---:|---:|---:|\n");
        appendMetrics(output, report.development());
        appendMetrics(output, report.heldOut());

        output.append("\n## Threshold\n\n")
                .append("The threshold was selected from the development split only and frozen before held-out scoring.\n\n")
                .append("- Frozen threshold: `")
                .append(number(report.calibration().frozenThreshold())).append("`\n")
                .append("- Configured runtime threshold: `")
                .append(number(report.configuredMinimumSimilarity())).append("`\n")
                .append("- Runtime matches calibration: `")
                .append(report.configuredThresholdMatchesCalibration()).append("`\n")
                .append("- Objective: ").append(report.calibration().objective()).append("\n")
                .append("- Tie-break: ").append(report.calibration().tieBreak()).append("\n\n")
                .append("## Usage and cost boundary\n\n")
                .append("- Embedding calls: ").append(report.embeddingUsage().calls()).append("\n")
                .append("- Local input characters: ")
                .append(report.embeddingUsage().localInputCharacters()).append("\n")
                .append("- Provider billable characters: ")
                .append(nullable(report.embeddingUsage().providerBillableCharacters()))
                .append("\n")
                .append("- Provider input tokens: ")
                .append(nullable(report.embeddingUsage().providerInputTokens())).append("\n")
                .append("- Provider call latency: ")
                .append(report.embeddingUsage().providerCallLatencyMs()).append(" ms\n")
                .append("- Estimated provider cost: ")
                .append(nullable(report.costEstimate().usd())).append("\n")
                .append("- Cost status: ").append(report.costEstimate().status()).append("\n\n")
                .append("## Cases\n\n")
                .append("| Case | Split | Type | Pass | Relevant rank | Accepted | Top hit |\n")
                .append("|---|---|---|---:|---:|---:|---|\n");
        for (RunbookRetrievalEvalReport.CaseResult evalCase : report.cases()) {
            String topHit = evalCase.hits().isEmpty()
                    ? "none"
                    : evalCase.hits().getFirst().evidenceId()
                    + " (" + number(evalCase.hits().getFirst().cosineSimilarity()) + ")";
            output.append("| `").append(evalCase.caseId()).append("` | ")
                    .append(evalCase.split()).append(" | ")
                    .append(evalCase.caseType()).append(" | ")
                    .append(evalCase.retrievalExpectationMet()).append(" | ")
                    .append(nullable(evalCase.firstRelevantRank())).append(" | ")
                    .append(evalCase.acceptedCount()).append(" | ")
                    .append(topHit).append(" |\n");
        }

        output.append("\n## Safety boundary\n\n")
                .append(report.adversarialSafetyStatus()).append("\n\n")
                .append("The adversarial runbook remains untrusted data. Its retrieval is an observation, not proof that synthesis resists indirect prompt injection.\n");
        return output.toString();
    }

    private void appendMetrics(
            StringBuilder output,
            RunbookRetrievalEvalReport.SplitMetrics metrics
    ) {
        output.append("| ").append(metrics.split()).append(" | ")
                .append(percent(metrics.hitAtK())).append(" | ")
                .append(number(metrics.meanReciprocalRank())).append(" | ")
                .append(percent(metrics.noMatchAccuracy())).append(" | ")
                .append(percent(metrics.benignUnsafeTop1Rate())).append(" |\n");
    }

    private String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private String nullable(Object value) {
        return value == null ? "not reported" : value.toString();
    }

    private void requireSafeVersion(String suiteVersion) {
        if (suiteVersion == null || !suiteVersion.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("suite version is unsafe for a file name");
        }
    }

    public record OutputFiles(Path json, Path markdown) {
    }
}
