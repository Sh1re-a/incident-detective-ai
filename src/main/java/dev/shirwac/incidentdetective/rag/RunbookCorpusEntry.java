package dev.shirwac.incidentdetective.rag;

import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record RunbookCorpusEntry(
        @NotBlank String evidenceId,
        @NotBlank String documentId,
        @NotBlank String documentVersion,
        @NotBlank String chunkId,
        @NotBlank String title,
        @NotBlank String displaySummary,
        @NotBlank String sourceRef,
        @NotBlank @Size(max = 2_000) String text
) {
    public String contentSha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalContent().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String embeddingInput() {
        return "title: " + title + " | text: " + text;
    }

    public RunbookEvidence asEvidence(String scenarioId) {
        return new RunbookEvidence(
                evidenceId,
                scenarioId,
                displaySummary,
                sourceRef,
                new RunbookEvidence.RunbookContent(
                        documentId,
                        chunkId,
                        documentVersion,
                        text
                )
        );
    }

    private String canonicalContent() {
        return title + "\n" + text;
    }
}
