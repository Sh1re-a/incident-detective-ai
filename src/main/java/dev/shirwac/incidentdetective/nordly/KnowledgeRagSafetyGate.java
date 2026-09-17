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
    private static final String CANCEL_ACTION_TERM =
            "(?:avbestall(?:a|er|t)?|avbryt(?:a|er|it)?|"
                    + "avboka(?:r|t)?|annullera(?:r|t)?|cancel(?:led)?)\\b";
    private static final String RETURN_CREATE_TERM =
            "(?:(?:skapa|starta|oppna|create|start|open).{0,15}"
                    + "(?:en retur|a return)"
                    + "(?: (?:at mig|for mig|for me|"
                    + "for (?:min (?:bestallning|order|vara)|"
                    + "bestallningen|ordern|my (?:order|item))))?)";
    private static final String REFUND_POLICY_TERM =
            "(?:aterbetal\\w*|pengar(?:na)? tillbaka|refund\\w*)";
    private static final String RETURN_POLICY_TERM =
            "(?:retur\\w*|return\\w*)";

    private static final List<Pattern> PII_REQUESTS = patterns(
            "(?:\\b(?:visa\\w*|hamta|hitta|lista|las upp|skriv ut|beratta|"
                    + "show|give me|fetch|find|list|read out|print|tell me|provide)"
                    + "\\b|\\bge(?: mig)?\\b)"
                    + ".{0,45}(?:personlig information|privata uppgifter|"
                    + "personuppgifter|kunddata|private information|"
                    + "namn|e-post|epost|mejl|mail|adress|telefon|personnummer|"
                    + "personal information|customer data|name|email|address|"
                    + "phone|ssn)",
            "(?:vem ar kunden|vad heter kunden|who is the customer)",
            "(?:beratta|sag|tell me).{0,35}(?:vad )?"
                    + "(?:kunden heter|the customer(?:'?s)? name)",
            "(?:vad ar|vilken ar|what is|what'?s).{0,35}"
                    + "(?:kundens|customer(?:'?s)?).{0,25}"
                    + "(?:namn|e-post|epost|mejl|mail|adress|telefon|personnummer|"
                    + "name|email|address|phone|ssn)",
            "(?:vad ar min|vilken ar min|what is my|what'?s my).{0,25}"
                    + "(?:e-post|epost|mejl|mail|adress|telefon|personnummer|"
                    + "email|address|phone|ssn)",
            "(?:vad ar|vilken ar)\\s+"
                    + "(?!(?:min|din|nordlys|foretagets)\\b)"
                    + "[a-z][a-z'-]{1,40}s\\s+"
                    + "(?:e-post|epost|mejl|mail|adress|telefon|personnummer)",
            "(?:what is|what'?s)\\s+"
                    + "(?!(?:my|your|nordly'?s|the company'?s)\\b)"
                    + "[a-z][a-z' -]{1,40}'s\\s+"
                    + "(?:email|address|phone|ssn)",
            "(?:avsloja|visa|show|disclose).{0,35}"
                    + "(?:koparens identitet|buyer identity|hela kundposten|"
                    + "full customer record|customer record)"
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
                    + "(?:vd|ceo|chef|i manaden|per manad|per month)",
            "(?:vad|hur mycket|what|how much).{0,20}"
                    + "(?:far|gets?|receives?).{0,45}"
                    + "(?:lon|lonen|ersattning|salary|compensation|pay)",
            "(?:beratta|visa|show|tell me).{0,25}"
                    + "(?:lon|lonen|ersattning|salary|compensation|pay)"
                    + ".{0,25}(?:for|of)\\s+[a-z][a-z' -]{1,40}[?!.]*$",
            "(?:vad|hur mycket) tjanar\\s+[a-z][a-z' -]{1,40}[?!.]*$",
            "(?:what is|how much is)\\s+[a-z][a-z' -]{1,40}"
                    + "\\s+(?:paid|salary|compensation)[?!.]*$",
            "(?:what does|how much does)\\s+[a-z][a-z' -]{1,40}"
                    + "\\s+(?:earn|make|get paid)[?!.]*$"
    );
    private static final List<Pattern> PROMPT_INJECTIONS = patterns(
            "(?:ignorera|bortse fran|strunta i|glom|ignore|disregard|forget|override)"
                    + ".{0,45}"
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
                    + "unsafe documents|osakra dokument)",
            "(?:glom|forget).{0,45}(?:allt|everything|vad du fatt veta|"
                    + "what you (?:know|were told))",
            "(?:latsas|pretend).{0,45}(?:regler|rules).{0,30}"
                    + "(?:inte galler|do not apply|dont apply|not apply)",
            "(?:oversatt|translate).{0,35}"
                    + "(?:developer instructions|developer message|"
                    + "system prompt|systeminstruktioner|utvecklarinstruktioner)"
    );
    private static final List<Pattern> FINANCIAL_ACTIONS = patterns(
            "(?:aterbetala|genomfor en aterbetalning|utfor en aterbetalning|"
                    + "gor en aterbetalning|issue a refund|process a refund|"
                    + "execute a refund|refund (?:order|customer))",
            "(?:kan (?:du|ni)|skulle (?:du|ni) kunna|jag vill att (?:du|ni)|snalla|"
                    + "can you|could you|would you|please|i want you to|"
                    + "i need you to|i would like you to).{0,35}"
                    + "(?:aterbetala|betala tillbaka|issue (?:a )?refund|"
                    + "process (?:a )?refund|refund (?:it|me|my order)|"
                    + "ge mig pengar(?:na)? tillbaka|returnera\\w*|"
                    + "return (?:it|my order|my item)|" + RETURN_CREATE_TERM + ")",
            "^(?:jag (?:vill|behover|onskar) (?!veta)|"
                    + "i (?:want|need|would like) to (?!know)).{0,35}"
                    + "(?:aterbetala|betala tillbaka|issue (?:a )?refund|"
                    + "process (?:a )?refund|refund (?:it|me|my order)|"
                    + "ge mig pengar(?:na)? tillbaka|returnera\\w*|"
                    + "return (?:it|my order|my item)|" + RETURN_CREATE_TERM + ")",
            "(?:jag vill (?:ha|fa)|i want (?:to (?:get|request) )?)"
                    + ".{0,5}(?:en aterbetalning|a refund)",
            "i would like a refund",
            "(?:jag vill ha|i want).{0,15}pengar(?:na)? tillbaka",
            "^(?:jag (?:vill|behover|onskar) (?:fa|ha) tillbaka pengarna|"
                    + "i (?:want|need) (?:my )?money back)[.!?]*$",
            "^(?:refund (?:me|it|my order)|skapa|starta|oppna|create|start|open)"
                    + ".{0,20}(?:retur|return|refund)?[.!?]*$",
            "^" + RETURN_CREATE_TERM
                    + "(?: (?:tack|please|nu|now))*[.!?]*$",
            "(?:[.!?;]\\s*|,\\s*)(?:aterbetala\\w*|betala tillbaka|"
                    + "issue (?:a )?refund|process (?:a )?refund|"
                    + "refund (?:it|me|my order)|give me (?:my )?money back|"
                    + "returnera\\w*|return (?:it|my order|my item)|"
                    + RETURN_CREATE_TERM + ")"
                    + "(?: (?:den|det|varan|ordern|min order|it|my order|my item))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:,? (?:och|and) )(?:aterbetala\\w*|betala tillbaka|"
                    + "issue (?:a )?refund|process (?:a )?refund|"
                    + "refund (?:it|me|my order)|returnera\\w*|"
                    + "return (?:it|my order|my item)|"
                    + RETURN_CREATE_TERM + ")"
                    + "(?: (?:den|det|varan|ordern|min order|it|my order|my item))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            REFUND_POLICY_TERM + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|do it|go ahead|"
                    + "give me (?:my )?money back)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            RETURN_POLICY_TERM + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|starta den|skapa den|"
                    + "do it|go ahead|start it|create it)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "^(?:jag behover|jag onskar|i need|i would like)"
                    + " (?:en aterbetalning|a refund)[.!?]*$",
            "(?:debitera|dra pengar|charge (?:the )?(?:card|customer)|"
                    + "capture (?:the )?payment)"
    );
    private static final List<Pattern> WRITE_ACTIONS = patterns(
            "(?:andra|uppdatera|byta|change|update|switch).{0,35}"
                    + "(?:leveransadress\\w*|leverans adress\\w*|"
                    + "delivery address|shipping address)",
            "(?:radera|andra|uppdatera|publicera|skicka|kontakta|"
                    + "deploya|rulla (?:tillbaka|tillbaks)).{0,30}"
                    + "(?:order|konto|kund|data|"
                    + "meddelande|release|system)",
            "(?:delete|change|update|publish|send|contact|deploy|"
                    + "rollback|roll back)"
                    + ".{0,30}(?:order|account|customer|data|message|release|system)",
            "(?:kan (?:du|ni)|skulle (?:du|ni) kunna|jag vill att (?:du|ni)|snalla|"
                    + "can you|could you|would you|please|i want you to|"
                    + "i need you to|i would like you to)"
                    + ".{0,35}"
                    + CANCEL_ACTION_TERM,
            "^(?:jag (?:vill|behover|onskar) (?!veta)|"
                    + "i (?:want|need|would like) to (?!know)).{0,35}"
                    + CANCEL_ACTION_TERM,
            "^" + CANCEL_ACTION_TERM
                    + ".{0,55}$",
            "(?:[.!?;]\\s*|,\\s*)" + CANCEL_ACTION_TERM
                    + "(?: (?:den|det|ordern|bestallningen|min order|min bestallning|"
                    + "it|me|my order))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:,? (?:och|and) )" + CANCEL_ACTION_TERM
                    + "(?: (?:den|det|ordern|bestallningen|min order|min bestallning|"
                    + "it|me|my order))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:avbestall\\w*|avbryt\\w*|avboka\\w*|annullera\\w*|cancel\\w*)"
                    + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|do it|go ahead)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:rollback|roll back|rulla (?:tillbaka|tillbaks))"
                    + "(?: nu| now| omedelbart)?$",
            "(?:mark|set|andra|uppdatera).{0,35}"
                    + "(?:refund|aterbetalning|orderstatus|order status)"
                    + ".{0,35}(?:complete|completed|klar|fardig|delivered|levererad)"
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
                .replace('’', '\'')
                .replace('‘', '\'')
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
                    "Frågan passerade säkerhetskontrollen och får gå vidare inom den begränsade assistenten.",
                    "The question passed the safety check and may continue within the bounded assistant."
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
