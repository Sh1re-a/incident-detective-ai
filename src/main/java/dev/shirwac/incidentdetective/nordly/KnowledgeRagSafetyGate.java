package dev.shirwac.incidentdetective.nordly;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public final class KnowledgeRagSafetyGate {

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
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(?:\\bAIza[0-9a-z_-]{20,}\\b|\\bsk-[0-9a-z_-]{16,}\\b|"
                    + "\\b(?:api[-_ ]?key|password|secret|token)\\s*[:=]\\s*\\S+)"
    );

    private static final List<Pattern> PII_REQUESTS = patterns(
            "(?:visa|ge(?: mig)?|hamta|lista|skriv ut|beratta|show|give me|"
                    + "fetch|list|print|tell me|provide)"
                    + ".{0,45}(?:personlig information|personuppgifter|kunddata|"
                    + "namn|e-post|epost|mejl|mail|adress|telefon|personnummer|"
                    + "personal information|customer data|name|email|address|"
                    + "phone|ssn)",
            "(?:vem ar kunden|who is the customer)"
    );
    private static final List<Pattern> SECRET_REQUESTS = patterns(
            "(?:visa|ge(?: mig)?|hamta|lista|skriv ut|avsloja|beratta|show|"
                    + "give me|fetch|list|print|reveal|tell me|provide).{0,45}"
                    + "(?:api[- ]?nyckel|api[- ]?key|"
                    + "losenord|password|databaslosenord|database password|"
                    + "secret|credential|access token|auth token)",
            "(?:vad ar|what is).{0,30}(?:api[- ]?nyckeln|api[- ]?key|losenordet|"
                    + "password|secret|token)"
    );
    private static final List<Pattern> EMPLOYEE_COMPENSATION_REQUESTS = patterns(
            "(?:nagon(?:s)?|anstalld(?:s)?|medarbetare(?:s)?|employee(?:'?s)?|"
                    + "staff member(?:'?s)?|person(?:'?s)?|someone(?:'?s)?|"
                    + "somebody(?:'?s)?)"
                    + ".{0,40}(?:lon|lonen|tjanar|salary|compensation|pay|paid|earns?)",
            "(?:lon|lonen|salary|compensation|pay|paid)"
                    + ".{0,40}(?:nagon|anstalld|medarbetare|employee|"
                    + "staff member|person|someone|somebody)",
            "(?:vad|hur mycket|what|how much).{0,25}"
                    + "(?:tjanar|far i lon|earns?|is paid).{0,45}"
                    + "(?:pa nordly|at nordly|anstalld|medarbetare|employee|staff)",
            "(?:vad|hur mycket|what|how much).{0,45}"
                    + "(?:tjanar|far|earns?|is paid).{0,45}"
                    + "(?:vd|ceo|chef|i manaden|per manad|per month)"
    );
    private static final List<Pattern> PROMPT_INJECTIONS = patterns(
            "(?:ignorera|bortse fran|ignore|disregard|forget).{0,45}"
                    + "(?:regler|instruktioner|tidigare|previous|prior|rules|"
                    + "instructions|directions)",
            "(?:folj inte|do not follow|dont follow).{0,45}"
                    + "(?:tidigare|foregaende|previous|prior).{0,20}"
                    + "(?:regler|instruktioner|rules|instructions|directions)",
            "(?:bortse fran|ignore|disregard).{0,25}"
                    + "(?:allt ovan|everything above|all above)",
            "(?:system prompt|systemprompt|developer message|jailbreak|bypass|"
                    + "kringga|override instructions)",
            "(?:anvand|use).{0,35}(?:alla interna dokument|every internal document|"
                    + "unsafe documents|osakra dokument)"
    );
    private static final List<Pattern> FINANCIAL_ACTIONS = patterns(
            "(?:aterbetala|genomfor en aterbetalning|utfor en aterbetalning|"
                    + "gor en aterbetalning|issue a refund|process a refund|"
                    + "execute a refund|refund (?:order|customer))",
            "(?:debitera|dra pengar|charge (?:the )?(?:card|customer)|"
                    + "capture (?:the )?payment)"
    );
    private static final List<Pattern> WRITE_ACTIONS = patterns(
            "(?:avboka|radera|andra|uppdatera|publicera|skicka|kontakta|"
                    + "deploya|rulla (?:tillbaka|tillbaks)).{0,30}"
                    + "(?:order|konto|kund|data|"
                    + "meddelande|release|system)",
            "(?:cancel|delete|change|update|publish|send|contact|deploy|"
                    + "rollback|roll back)"
                    + ".{0,30}(?:order|account|customer|data|message|release|system)",
            "(?:rollback|roll back|rulla (?:tillbaka|tillbaks))"
                    + "(?: nu| now| omedelbart)?$"
    );

    public Decision evaluate(String question) {
        String normalized = normalize(question);
        if (contains(EMAIL, question)
                || contains(SWEDISH_PERSONAL_NUMBER, question)
                || contains(CARD_LIKE_NUMBER, question)
                || contains(PHONE_LIKE_NUMBER, question)
                || matches(PII_REQUESTS, normalized)) {
            return Decision.block(
                    ReasonCode.PII_REQUEST,
                    "Frågan stoppades före AI eftersom den ber om eller innehåller persondata.",
                    "The question was stopped before AI because it requests or contains personal data."
            );
        }
        if (matches(EMPLOYEE_COMPENSATION_REQUESTS, normalized)) {
            return Decision.block(
                    ReasonCode.EMPLOYEE_COMPENSATION_REQUEST,
                    "Frågan stoppades före AI eftersom en persons lön eller "
                            + "ersättning är privat personalinformation.",
                    "The question was stopped before AI because an individual's "
                            + "salary or compensation is private employee information."
            );
        }
        if (contains(SECRET_VALUE, question)
                || matches(SECRET_REQUESTS, normalized)) {
            return Decision.block(
                    ReasonCode.SECRET_REQUEST,
                    "Frågan stoppades före AI eftersom den ber om hemligheter eller inloggningsuppgifter.",
                    "The question was stopped before AI because it requests secrets or credentials."
            );
        }
        String promptNormalized = normalized.replace('0', 'o');
        if (matches(PROMPT_INJECTIONS, promptNormalized)) {
            return Decision.block(
                    ReasonCode.PROMPT_INJECTION,
                    "Frågan försöker ändra säkerhetsreglerna och stoppades före AI.",
                    "The question attempts to override safety rules and was stopped before AI."
            );
        }
        if (matches(FINANCIAL_ACTIONS, normalized)) {
            return Decision.block(
                    ReasonCode.FINANCIAL_ACTION,
                    "AI:n får förklara policyn men inte genomföra en ekonomisk åtgärd.",
                    "The AI may explain policy but cannot execute a financial action."
            );
        }
        if (matches(WRITE_ACTIONS, normalized)) {
            return Decision.block(
                    ReasonCode.WRITE_ACTION,
                    "AI:n är read-only och får inte ändra företagets system.",
                    "The AI is read-only and cannot change company systems."
            );
        }
        return Decision.allow();
    }

    private static boolean contains(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).find();
    }

    private static boolean matches(List<Pattern> patterns, String value) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private static List<Pattern> patterns(String... expressions) {
        return java.util.Arrays.stream(expressions)
                .map(expression -> Pattern.compile(expression, Pattern.CASE_INSENSITIVE))
                .toList();
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        );
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    public enum ReasonCode {
        NONE,
        PII_REQUEST,
        EMPLOYEE_COMPENSATION_REQUEST,
        SECRET_REQUEST,
        PROMPT_INJECTION,
        FINANCIAL_ACTION,
        WRITE_ACTION
    }

    public record Decision(
            boolean allowed,
            ReasonCode reasonCode,
            String summarySv,
            String summaryEn
    ) {
        static Decision allow() {
            return new Decision(
                    true,
                    ReasonCode.NONE,
                    "Frågan får gå vidare till den begränsade kunskapssökningen.",
                    "The question may continue to the bounded knowledge search."
            );
        }

        static Decision block(
                ReasonCode reasonCode,
                String summarySv,
                String summaryEn
        ) {
            return new Decision(false, reasonCode, summarySv, summaryEn);
        }
    }
}
