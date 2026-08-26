package dev.shirwac.incidentdetective.rag.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunbookRetrievalEvalReportWriterTest {

    @TempDir
    private Path outputDirectory;

    @Test
    void writesMachineReadableJsonAndTruthfulMarkdown() throws Exception {
        JsonMapper mapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        RunbookRetrievalEvalReportWriter writer =
                new RunbookRetrievalEvalReportWriter(mapper);

        RunbookRetrievalEvalReportWriter.OutputFiles files = writer.write(
                report(),
                outputDirectory
        );

        var json = mapper.readTree(Files.readString(files.json()));
        assertEquals("runbook-retrieval-eval-v1", json.get("suite_version").asText());
        assertTrue(json.get("embedding_usage")
                .get("provider_input_tokens")
                .isNull());
        assertTrue(json.get("cost_estimate").get("usd").isNull());

        String markdown = Files.readString(files.markdown());
        assertTrue(markdown.contains("development"));
        assertTrue(markdown.contains("held_out"));
        assertTrue(markdown.contains("Frozen threshold"));
        assertTrue(markdown.contains("not reported"));
        assertTrue(markdown.contains("not proof"));
        assertFalse(markdown.contains("$0.00"));
    }

    private RunbookRetrievalEvalReport report() {
        RunbookRetrievalEvalReport.SplitMetrics development =
                new RunbookRetrievalEvalReport.SplitMetrics(
                        "development", 1, 1, 1.0, 1.0, 1, 1, 1.0, 0.0
                );
        RunbookRetrievalEvalReport.SplitMetrics heldOut =
                new RunbookRetrievalEvalReport.SplitMetrics(
                        "held_out", 1, 1, 1.0, 1.0, 1, 1, 1.0, 0.0
                );
        return new RunbookRetrievalEvalReport(
                "runbook-retrieval-eval-v1",
                "a".repeat(64),
                "runbook-corpus-v1",
                "b".repeat(64),
                "abcdef123456",
                Instant.parse("2026-08-26T12:00:00Z"),
                "pgvector_exact_cosine",
                new RunbookRetrievalEvalReport.EmbeddingProfile(
                        "google",
                        "gemini-embedding-2",
                        768,
                        "search-result-v1",
                        "task: search result | query: {query}",
                        "title: {title} | text: {text}"
                ),
                4,
                0.0,
                new RunbookRetrievalEvalReport.Calibration(
                        "development",
                        "0.5 * Hit@4 + 0.5 * no-match accuracy",
                        "higher no-match accuracy, then higher MRR, then lower threshold",
                        0.55,
                        1.0,
                        1.0,
                        1.0,
                        1.0,
                        5
                ),
                false,
                development,
                heldOut,
                new RunbookRetrievalEvalReport.EmbeddingUsage(
                        2, 250, null, null, false, 30
                ),
                new RunbookRetrievalEvalReport.CostEstimate(
                        null,
                        "not_calculated: provider usage metadata unavailable"
                ),
                false,
                "not_evaluated: retrieval is not a synthesis safety test",
                45,
                List.of(new RunbookRetrievalEvalReport.CaseResult(
                        "dev-case",
                        "development",
                        "positive",
                        null,
                        List.of("runbook-payment-timeout-precedence"),
                        false,
                        true,
                        1,
                        1.0,
                        1,
                        125,
                        null,
                        null,
                        10,
                        15,
                        null,
                        List.of(new RunbookRetrievalEvalReport.Hit(
                                1,
                                "runbook-payment-timeout-precedence",
                                0.81,
                                true,
                                true
                        ))
                ))
        );
    }
}
