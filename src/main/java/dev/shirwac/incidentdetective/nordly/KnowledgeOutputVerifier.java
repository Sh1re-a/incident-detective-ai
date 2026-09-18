package dev.shirwac.incidentdetective.nordly;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
final class KnowledgeOutputVerifier {

    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}\\b"
    );
    private static final Pattern SWEDISH_PERSONAL_NUMBER = Pattern.compile(
            "\\b(?:19|20)?\\d{6}[-+ ]?\\d{4}\\b"
    );
    private static final Pattern CARD_LIKE_NUMBER = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );
    private static final Pattern PHONE_LIKE_NUMBER = Pattern.compile(
            "(?<![A-Z0-9])(?:\\+46|0)7[0236][ -]?(?:\\d[ -]?){6,8}(?!\\d)"
    );
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(?:api[-_ ]?key|password|secret|token)\\s*[:=]\\s*\\S+"
    );
    private static final Pattern ACTION_CLAIM = Pattern.compile(
            "(?i)(?:jag har (?:aterbetalat|avbokat|publicerat|deployat)|"
                    + "aterbetalningen har (?:genomforts|skickats)|"
                    + "ordern (?:ar|har blivit) avbokad|"
                    + "i (?:have )?(?:refunded|cancelled|published|deployed)|"
                    + "refund has been (?:issued|processed)|"
                    + "order has been cancelled|"
                    + "(?:message|release) has been (?:published|deployed))"
    );

    Result verify(
            KnowledgeGeneratedAnswer answer,
            List<KnowledgeRagResponse.RankedMatch> retrieved
    ) {
        boolean schemaPass = structurallyValid(answer);
        Set<String> allowedCitations = retrieved.stream()
                .map(KnowledgeRagResponse.RankedMatch::evidenceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean citationsWithinContext = schemaPass
                && answer.claims().stream()
                .flatMap(claim -> claim.citationIds().stream())
                .allMatch(allowedCitations::contains);
        boolean approvedOnly = retrieved.stream()
                .allMatch(match -> NordlyKnowledgeCorpus.REQUIRED_LIFECYCLE
                        .equals(match.status()));
        boolean outputPiiScanPass = schemaPass && piiSafe(answer);
        boolean outputPolicyScanPass = schemaPass && policySafe(answer);
        return new Result(
                schemaPass,
                citationsWithinContext,
                approvedOnly,
                outputPiiScanPass,
                outputPolicyScanPass,
                schemaPass
                        && citationsWithinContext
                        && approvedOnly
                        && outputPiiScanPass
                        && outputPolicyScanPass
        );
    }

    private boolean structurallyValid(KnowledgeGeneratedAnswer answer) {
        if (answer == null
                || !bounded(answer.summarySv(), 700)
                || !bounded(answer.summaryEn(), 700)
                || answer.claims() == null
                || answer.claims().isEmpty()
                || answer.claims().size() > 3) {
            return false;
        }
        for (KnowledgeGeneratedAnswer.GeneratedClaim claim : answer.claims()) {
            if (claim == null
                    || !bounded(claim.textSv(), 500)
                    || !bounded(claim.textEn(), 500)
                    || claim.citationIds() == null
                    || claim.citationIds().isEmpty()
                    || claim.citationIds().size() > 3
                    || claim.citationIds().stream()
                    .anyMatch(value -> value == null || value.isBlank())
                    || new HashSet<>(claim.citationIds()).size()
                    != claim.citationIds().size()) {
                return false;
            }
        }
        return true;
    }

    private boolean piiSafe(KnowledgeGeneratedAnswer answer) {
        String value = combinedText(answer);
        return !EMAIL.matcher(value).find()
                && !SWEDISH_PERSONAL_NUMBER.matcher(value).find()
                && !CARD_LIKE_NUMBER.matcher(value).find()
                && !PHONE_LIKE_NUMBER.matcher(value).find()
                && !SECRET_ASSIGNMENT.matcher(value).find();
    }

    private boolean policySafe(KnowledgeGeneratedAnswer answer) {
        return !ACTION_CLAIM.matcher(normalize(combinedText(answer))).find();
    }

    private String combinedText(KnowledgeGeneratedAnswer answer) {
        StringBuilder output = new StringBuilder()
                .append(answer.summarySv())
                .append('\n')
                .append(answer.summaryEn());
        for (KnowledgeGeneratedAnswer.GeneratedClaim claim : answer.claims()) {
            output.append('\n').append(claim.textSv())
                    .append('\n').append(claim.textEn());
        }
        return output.toString();
    }

    private String normalize(String value) {
        String decomposed = java.text.Normalizer.normalize(
                value,
                java.text.Normalizer.Form.NFD
        );
        return decomposed.replaceAll("\\p{M}", "");
    }

    private boolean bounded(String value, int maxLength) {
        return value != null
                && !value.isBlank()
                && value.length() <= maxLength;
    }

    record Result(
            boolean schemaPass,
            boolean citationsWithinRetrievedContext,
            boolean approvedDocumentsOnly,
            boolean outputPiiScanPass,
            boolean outputPolicyScanPass,
            boolean passed
    ) {
    }
}
