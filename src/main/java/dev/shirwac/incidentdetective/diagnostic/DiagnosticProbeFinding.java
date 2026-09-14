package dev.shirwac.incidentdetective.diagnostic;

import java.util.List;

/** One bounded, evidence-linked observation returned by a diagnostic probe. */
public record DiagnosticProbeFinding(
        String code,
        String subject,
        String status,
        String detail,
        List<String> evidenceIds
) {
    public static final int MAX_CODE_LENGTH = 64;
    public static final int MAX_SUBJECT_LENGTH = 128;
    public static final int MAX_STATUS_LENGTH = 64;
    public static final int MAX_DETAIL_LENGTH = 256;
    public static final int MAX_EVIDENCE_IDS = 4;
    public static final int MAX_EVIDENCE_ID_LENGTH = 160;

    public DiagnosticProbeFinding {
        code = boundedText(code, "code", MAX_CODE_LENGTH);
        subject = boundedText(subject, "subject", MAX_SUBJECT_LENGTH);
        status = boundedText(status, "status", MAX_STATUS_LENGTH);
        detail = boundedText(detail, "detail", MAX_DETAIL_LENGTH);
        if (evidenceIds == null) {
            throw new IllegalArgumentException("evidenceIds must not be null");
        }
        evidenceIds = evidenceIds.stream()
                .map(id -> boundedText(
                        id,
                        "evidenceId",
                        MAX_EVIDENCE_ID_LENGTH
                ))
                .distinct()
                .limit(MAX_EVIDENCE_IDS)
                .toList();
    }

    private static String boundedText(String value, String name, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= maximum
                ? normalized
                : normalized.substring(0, maximum);
    }
}
