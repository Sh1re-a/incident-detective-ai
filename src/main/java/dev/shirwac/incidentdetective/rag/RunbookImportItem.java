package dev.shirwac.incidentdetective.rag;

public record RunbookImportItem(
        String evidenceId,
        String contentSha256,
        RunbookImportStatus status,
        int inputCharacters,
        Integer providerBillableCharacters,
        Double providerInputTokens,
        long embeddingLatencyMs
) {
}
