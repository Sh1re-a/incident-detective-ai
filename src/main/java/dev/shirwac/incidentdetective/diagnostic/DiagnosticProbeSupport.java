package dev.shirwac.incidentdetective.diagnostic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

final class DiagnosticProbeSupport {

    private DiagnosticProbeSupport() {
    }

    static DiagnosticProbeFinding finding(
            String code,
            String subject,
            String status,
            String detail,
            List<String> evidenceIds
    ) {
        return new DiagnosticProbeFinding(
                code,
                subject,
                status,
                detail,
                evidenceIds
        );
    }

    static DiagnosticProbeReceipt observed(
            String scenarioId,
            DiagnosticProbeId probeId,
            String summary,
            List<DiagnosticProbeFinding> findings,
            boolean truncated
    ) {
        return new DiagnosticProbeReceipt(
                scenarioId,
                probeId,
                DiagnosticProbeOutcome.OBSERVED,
                summary,
                findings,
                truncated
        );
    }

    static DiagnosticProbeReceipt unavailable(
            String scenarioId,
            DiagnosticProbeId probeId,
            String summary,
            List<DiagnosticProbeFinding> findings,
            boolean truncated
    ) {
        return new DiagnosticProbeReceipt(
                scenarioId,
                probeId,
                DiagnosticProbeOutcome.NOT_AVAILABLE,
                summary,
                findings,
                truncated
        );
    }

    static String normalizedStatus(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    static boolean isSuccessfulTraceStatus(String status) {
        String normalized = normalizedStatus(status);
        return normalized.equals("ok")
                || normalized.equals("success")
                || normalized.equals("unset");
    }

    static String shortSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
