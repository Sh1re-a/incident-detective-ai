package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorpusFingerprintTest {

    @Test
    void isOrderIndependentAndChangesWithEligibleContent() {
        RunbookCorpusEntry first = entry(
                "evidence-a",
                "document-a",
                "chunk-a",
                "A customer sees a card authorization."
        );
        RunbookCorpusEntry second = entry(
                "evidence-b",
                "document-b",
                "chunk-b",
                "A confirmed order is persisted."
        );
        RunbookCorpusEntry changed = entry(
                "evidence-a",
                "document-a",
                "chunk-a",
                "A customer sees a captured card charge."
        );

        String fingerprint = CorpusFingerprint.sha256(List.of(first, second));

        assertEquals(
                fingerprint,
                CorpusFingerprint.sha256(List.of(second, first))
        );
        assertNotEquals(
                fingerprint,
                CorpusFingerprint.sha256(List.of(changed, second))
        );
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    private static RunbookCorpusEntry entry(
            String evidenceId,
            String documentId,
            String chunkId,
            String text
    ) {
        return new RunbookCorpusEntry(
                evidenceId,
                documentId,
                "1.0",
                chunkId,
                "Synthetic title",
                "Synthetic summary",
                "knowledge/" + documentId + "#" + chunkId,
                text
        );
    }
}
