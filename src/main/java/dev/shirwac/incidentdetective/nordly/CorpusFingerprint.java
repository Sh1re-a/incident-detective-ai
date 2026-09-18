package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.rag.RunbookCorpusEntry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Produces an order-independent fingerprint of the exact eligible corpus.
 */
final class CorpusFingerprint {

    private static final String FORMAT_VERSION =
            "nordly-corpus-fingerprint-v1";
    private static final Comparator<RunbookCorpusEntry> ENTRY_ORDER =
            Comparator.comparing(RunbookCorpusEntry::documentId)
                    .thenComparing(RunbookCorpusEntry::documentVersion)
                    .thenComparing(RunbookCorpusEntry::chunkId)
                    .thenComparing(RunbookCorpusEntry::evidenceId)
                    .thenComparing(RunbookCorpusEntry::sourceRef)
                    .thenComparing(RunbookCorpusEntry::title)
                    .thenComparing(RunbookCorpusEntry::displaySummary)
                    .thenComparing(RunbookCorpusEntry::text);

    private CorpusFingerprint() {
    }

    static String sha256(List<RunbookCorpusEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        MessageDigest digest = sha256Digest();
        update(digest, FORMAT_VERSION);
        update(digest, Integer.toString(entries.size()));

        entries.stream()
                .map(entry -> Objects.requireNonNull(entry, "corpus entry"))
                .sorted(ENTRY_ORDER)
                .forEach(entry -> {
                    update(digest, entry.documentId());
                    update(digest, entry.documentVersion());
                    update(digest, entry.chunkId());
                    update(digest, entry.evidenceId());
                    update(digest, entry.sourceRef());
                    update(digest, entry.title());
                    update(digest, entry.displaySummary());
                    update(digest, entry.text());
                });

        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "fingerprint value")
                .getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length)
                .array());
        digest.update(bytes);
    }
}
