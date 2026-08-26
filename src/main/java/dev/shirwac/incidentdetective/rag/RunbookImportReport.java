package dev.shirwac.incidentdetective.rag;

import java.time.Instant;
import java.util.List;

public record RunbookImportReport(
        String corpusVersion,
        String embeddingModel,
        int embeddingDimensions,
        String embeddingFormatVersion,
        int totalChunks,
        int importedChunks,
        int skippedChunks,
        int inputCharacters,
        Integer providerBillableCharacters,
        Double providerInputTokens,
        boolean providerUsageMetadataComplete,
        long embeddingLatencyMs,
        Instant completedAt,
        List<RunbookImportItem> items
) {
    public RunbookImportReport {
        items = List.copyOf(items);
    }
}
