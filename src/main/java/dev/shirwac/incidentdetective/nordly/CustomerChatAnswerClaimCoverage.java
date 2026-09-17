package dev.shirwac.incidentdetective.nordly;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic release check for model prose against backend-verified claims. */
final class CustomerChatAnswerClaimCoverage {

    private static final Pattern SENTENCE_BREAK = Pattern.compile(
            "(?<=[.!?])\\s+|\\R+"
    );
    private static final Pattern SWEDISH_SOCIAL_SENTENCE = Pattern.compile(
            "^(?:hej(?: igen)?|tack(?: sa mycket)?|sjalvklart|absolut|"
                    + "jag hjalper (?:dig )?garna(?: vidare)?|"
                    + "jag hjalper (?:dig )?garna med en annan fraga|"
                    + "hur kan jag hjalpa (?:dig )?(?:vidare|idag)?|"
                    + "vad vill du (?:ha hjalp med|att jag hjalper dig med)"
                    + "(?: idag)?|"
                    + "absolut vad vill du att jag hjalper dig med|"
                    + "jag forstar att vantan kanns frustrerande|"
                    + "jag forstar att det ar frustrerande att vanta)$"
    );
    private static final Pattern ENGLISH_SOCIAL_SENTENCE = Pattern.compile(
            "^(?:hi|hello|thanks?(?: very much)?|of course|"
                    + "i(?: am|'m) happy to help(?: you)?(?: further)?|"
                    + "i(?: am|'m) happy to help with another question|"
                    + "how can i help(?: you)?(?: further| today)?|"
                    + "what would you like (?:help with|me to help you with)"
                    + "(?: today)?|"
                    + "of course what would you like help with|"
                    + "i understand that waiting is frustrating)$"
    );
    private static final Pattern SWEDISH_PROTECTED_BOUNDARY = Pattern.compile(
            "^jag (?:kan tyvarr inte lamna ut|har inte tillgang till|"
                    + "har inte mojlighet att lamna ut) "
                    + "(?:privat information|privata loneuppgifter|"
                    + "individuella loneuppgifter|den privata informationen|"
                    + "den informationen)(?: men jag (?:hjalper dig garna|"
                    + "kan hjalpa)(?: med)? (?:din order|en orderfraga|"
                    + "en annan fraga))?$"
    );
    private static final Pattern ENGLISH_PROTECTED_BOUNDARY = Pattern.compile(
            "^i (?:cannot disclose|don't have access to|"
                    + "do not have access to) (?:private information|"
                    + "private salary data|individual compensation data|"
                    + "that private information|that information)"
                    + "(?: but i (?:am happy to help|can help)(?: you)? with "
                    + "(?:your order|an order question|another question))?$"
    );

    private CustomerChatAnswerClaimCoverage() {
    }

    static boolean covers(CustomerChatAnswerGateway.Answer answer) {
        return languageCovered(
                answer.textSv(),
                answer.claims().stream()
                        .map(CustomerChatAnswerGateway.Claim::textSv)
                        .toList(),
                SWEDISH_SOCIAL_SENTENCE
        ) && languageCovered(
                answer.textEn(),
                answer.claims().stream()
                        .map(CustomerChatAnswerGateway.Claim::textEn)
                        .toList(),
                ENGLISH_SOCIAL_SENTENCE
        );
    }

    static boolean claimsWithinVerifiedSet(
            CustomerChatAnswerGateway.Answer answer,
            List<CustomerChatAnswerGateway.Claim> verifiedClaims
    ) {
        return answer.claims().stream().allMatch(generated ->
                verifiedClaims.stream().anyMatch(verified ->
                        equivalent(generated, verified)));
    }

    static boolean protectedBoundarySafe(CustomerChatAnswerGateway.Answer answer) {
        return answer.claims().isEmpty()
                && SWEDISH_PROTECTED_BOUNDARY.matcher(
                normalizePolicySentence(answer.textSv())
        ).matches()
                && ENGLISH_PROTECTED_BOUNDARY.matcher(
                normalizePolicySentence(answer.textEn())
        ).matches();
    }

    private static boolean languageCovered(
            String answerText,
            List<String> claimTexts,
            Pattern socialSentence
    ) {
        Set<String> verifiedSentences = new HashSet<>();
        claimTexts.forEach(claimText -> SENTENCE_BREAK.splitAsStream(claimText)
                .map(CustomerChatAnswerClaimCoverage::normalizeSentence)
                .filter(value -> !value.isBlank())
                .forEach(verifiedSentences::add));
        Set<String> answerSentences = SENTENCE_BREAK.splitAsStream(answerText)
                .map(CustomerChatAnswerClaimCoverage::normalizeSentence)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return verifiedSentences.stream().allMatch(answerSentences::contains)
                && answerSentences.stream().allMatch(sentence ->
                verifiedSentences.contains(sentence)
                        || matchesSocial(sentence, socialSentence));
    }

    private static boolean matchesSocial(
            String sentence,
            Pattern socialSentence
    ) {
        String withoutTerminalPunctuation = normalizePolicySentence(sentence);
        return socialSentence.matcher(withoutTerminalPunctuation).matches();
    }

    private static String normalizePolicySentence(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .replace('‘', '\'')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static boolean equivalent(
            CustomerChatAnswerGateway.Claim generated,
            CustomerChatAnswerGateway.Claim verified
    ) {
        return normalizeSentence(generated.textSv()).equals(
                normalizeSentence(verified.textSv())
        ) && normalizeSentence(generated.textEn()).equals(
                normalizeSentence(verified.textEn())
        ) && generated.citationIds().equals(verified.citationIds());
    }

    private static String normalizeSentence(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('’', '\'')
                .replace('‘', '\'')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
