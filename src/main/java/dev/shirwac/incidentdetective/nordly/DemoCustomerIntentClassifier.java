package dev.shirwac.incidentdetective.nordly;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public final class DemoCustomerIntentClassifier {

    public static final String CLASSIFIER = "deterministic_rules_v4";

    private static final String CANCEL_TERM =
            "(?:avbestall\\w*|avbryt\\w*|avboka\\w*|annullera\\w*|cancel\\w*)";
    private static final String CANCEL_ACTION_TERM =
            "(?:avbestall(?:a|er|t)?|avbryt(?:a|er|it)?|"
                    + "avboka(?:r|t)?|annullera(?:r|t)?|cancel(?:led)?)\\b";
    private static final String REFUND_ACTION_TERM =
            "(?:aterbetala(?:r|t)?\\b|betala tillbak(?:a|s)|issue (?:a )?refund|"
                    + "process (?:a )?refund|refund (?:it|den|ordern|my order)|"
                    + "give me (?:my )?money back|"
                    + "betala ut (?:pengar(?:na)?|aterbetalningen)|"
                    + "pay (?:it|the money|my refund) out)";
    private static final String REFUND_POLICY_TERM =
            "(?:aterbetal\\w*|pengar(?:na)? tillbak(?:a|s)|refund\\w*|"
                    + "utbetal\\w*|betalas ut|paid out|payout)";
    private static final String RETURN_ACTION_TERM =
            "(?:returnera(?:r|t)?\\b|return (?:it|my order|my item))";
    private static final String RETURN_POLICY_TERM =
            "(?:retur\\w*|return\\w*)";
    private static final String RETURN_CREATE_TERM =
            "(?:(?:skapa|starta|oppna|create|start|open).{0,15}"
                    + "(?:en retur|a return)"
                    + "(?: (?:at mig|for mig|for me|"
                    + "for (?:min (?:bestallning|order|vara)|"
                    + "bestallningen|ordern|my (?:order|item))))?)";
    private static final String CANCEL_OBJECT =
            "(?:den|det|ordern|bestallningen|min order|min bestallning|"
                    + "my order|order|it|me)";

    private static final List<Pattern> CHANGE_DELIVERY_ADDRESS = patterns(
            "(?:andra|uppdatera|byta|change|update|switch).{0,35}"
                    + "(?:leveransadress\\w*|leverans adress\\w*|"
                    + "delivery address|shipping address)"
    );
    private static final List<Pattern> CLARIFICATION_REQUIRED = patterns(
            "^(?:(?:okej|ok|okay)[,!]?\\s*)?"
                    + "(?:gor det|utfor det|genomfor det|kor(?: pa)?|"
                    + "do it|go ahead)"
                    + "(?: nu| now)?[.!?]*$"
    );

    private static final List<Pattern> CANCEL_ACTION = patterns(
            "(?:jag|vi) (?:vill|onskar|behover) inte "
                    + "(?:ha|behalla|ta emot) (?:paketet|ordern|bestallningen|varan)"
                    + "(?: langre)?",
            "i (?:do not|don'?t) want "
                    + "(?:the |my )?(?:package|parcel|order|item)(?: anymore)?",
            "(?:kan|skulle) (?:du|ni).{0,35}" + CANCEL_ACTION_TERM,
            "(?:can|could|would) you.{0,35}" + CANCEL_ACTION_TERM,
            "(?:jag vill att (?:du|ni)|snalla|please|i want you to)"
                    + ".{0,35}"
                    + CANCEL_ACTION_TERM,
            "^(?:jag (?:vill|behover|onskar) (?!veta)|"
                    + "i (?:want|need|would like) to (?!know)).{0,35}"
                    + CANCEL_ACTION_TERM,
            "^" + CANCEL_ACTION_TERM
                    + "(?: " + CANCEL_OBJECT + ")?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:[.!?;]\\s*|,\\s*)" + CANCEL_ACTION_TERM
                    + "(?: " + CANCEL_OBJECT + ")?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:,? (?:och|and) )" + CANCEL_ACTION_TERM
                    + "(?: " + CANCEL_OBJECT + ")?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            CANCEL_TERM + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|do it|go ahead)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$"
    );
    private static final List<Pattern> CANCEL_POLICY = patterns(
            "(?:kan jag|far jag|nar far|nar kan|gar det att|hur fungerar|vad galler|"
                    + "can i|may i|when can|is it possible to|how does|what is)"
                    + ".{0,50}"
                    + CANCEL_TERM,
            "(?:vilka|vad|which|what).{0,35}"
                    + "(?:regler?|policy|rules?).{0,35}" + CANCEL_TERM,
            "(?:jag vill veta|i want to know).{0,50}" + CANCEL_TERM,
            CANCEL_TERM + ".{0,35}(?:policy|regeln|regel|tillatet|allowed)"
    );
    private static final List<Pattern> REFUND_ACTION = patterns(
            "(?:kan|skulle) (?:du|ni).{0,35}" + REFUND_ACTION_TERM,
            "(?:can|could|would) you.{0,35}" + REFUND_ACTION_TERM,
            "(?:kan|skulle) (?:du|ni).{0,35}(?:ge mig )?pengar(?:na)? tillbak(?:a|s)",
            "(?:jag vill att (?:du|ni)|snalla|please|i want you to)"
                    + ".{0,35}"
                    + REFUND_ACTION_TERM,
            "^(?:jag (?:vill|behover|onskar) (?!veta)|"
                    + "i (?:want|need|would like) to (?!know)).{0,35}"
                    + REFUND_ACTION_TERM,
            "(?:jag vill (?:ha|fa)|i want (?:to (?:get|request) )?)"
                    + ".{0,5}(?:en aterbetalning|a refund)",
            "i would like a refund",
            "(?:jag vill ha|i want).{0,15}pengar(?:na)? tillbak(?:a|s)",
            "^(?:jag (?:vill|behover|onskar) (?:fa|ha) tillbak(?:a|s) pengarna|"
                    + "i (?:want|need) (?:my )?money back)[.!?]*$",
            "^(?:refund (?:me|it|my order)|"
                    + REFUND_ACTION_TERM + ")"
                    + "(?: (?:den|det|ordern|min order|it|my order))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:[.!?;]\\s*|,\\s*)" + REFUND_ACTION_TERM
                    + "(?: (?:den|det|ordern|min order|it|my order))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:,? (?:och|and) )" + REFUND_ACTION_TERM
                    + "(?: (?:den|det|ordern|min order|it|my order))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            REFUND_POLICY_TERM + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|do it|go ahead|"
                    + "give me (?:my )?money back)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "^(?:jag behover|jag onskar|i need|i would like)"
                    + " (?:en aterbetalning|a refund)[.!?]*$"
    );
    private static final List<Pattern> PURCHASE_ACTION = patterns(
            "^(?:(?:okej|ok|okay)[,!]?\\s*)?"
                    + "(?:jag|vi) (?:vill|behover|onskar) "
                    + "(?:bestalla|kopa|lagga till).{0,35}"
                    + "(?:vara|produkt|artikel|item|product)",
            "(?:kan|skulle) (?:du|ni).{0,35}"
                    + "(?:bestalla|kopa|lagga till).{0,35}"
                    + "(?:vara|produkt|artikel|item|product)",
            "^(?:bestall|kop|lagg till).{0,35}"
                    + "(?:vara|produkt|artikel)",
            "^i (?:want|need|would like) (?:to )?"
                    + "(?:order|buy|add).{0,35}(?:item|product)",
            "(?:can|could|would) you.{0,35}"
                    + "(?:order|buy|add).{0,35}(?:item|product)"
    );
    private static final List<Pattern> RETURN_ACTION = patterns(
            "(?:kan|skulle) (?:du|ni).{0,35}" + RETURN_ACTION_TERM,
            "(?:can|could|would) you.{0,35}" + RETURN_ACTION_TERM,
            "(?:kan|skulle) (?:du|ni).{0,35}" + RETURN_CREATE_TERM,
            "(?:can|could|would) you.{0,35}" + RETURN_CREATE_TERM,
            "(?:jag vill att (?:du|ni)|snalla|please|i want you to)"
                    + ".{0,35}" + RETURN_ACTION_TERM,
            "^(?:jag (?:vill|behover|onskar) (?!veta)|"
                    + "i (?:want|need|would like) to (?!know)).{0,35}"
                    + RETURN_ACTION_TERM,
            "^(?:" + RETURN_ACTION_TERM + ")"
                    + "(?: (?:den|det|varan|ordern|min order|it|my order|my item))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "^" + RETURN_CREATE_TERM
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:[.!?;]\\s*|,\\s*)(?:" + RETURN_ACTION_TERM + "|"
                    + RETURN_CREATE_TERM + ")"
                    + "(?: (?:den|det|varan|ordern|min order|it|my order|my item))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            "(?:,? (?:och|and) )(?:" + RETURN_ACTION_TERM + "|"
                    + RETURN_CREATE_TERM + ")"
                    + "(?: (?:den|det|varan|ordern|min order|it|my order|my item))?"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$",
            RETURN_POLICY_TERM + ".{0,100}"
                    + "(?:gor det|utfor det|genomfor det|starta den|skapa den|"
                    + "do it|go ahead|start it|create it)"
                    + "(?: (?:at mig|for me|tack|please|nu|now))*[.!?]*$"
    );
    private static final List<Pattern> REFUND_POLICY = patterns(
            REFUND_POLICY_TERM
    );
    private static final List<Pattern> RETURN_POLICY = patterns(
            RETURN_POLICY_TERM
    );
    private static final List<Pattern> ORDER_STATUS = patterns(
            "(?:vad|vilka).{0,20}(?:bestallde jag|har jag bestallt)",
            "(?:what|which).{0,20}(?:did i order|have i ordered)",
            "(?:var|vart) ar (?:mina|mitt) (?:varor|paket|bestallning)",
            "^(?:var ar|vart ar|nar kommer) den[.!?]*$",
            "^(?:(?:okej|ok|okay)[,!]?\\s*)?"
                    + "(?:har den skickats|ar den skickad|has it shipped|"
                    + "is it shipped)[.!?]*$",
            "^(?:(?:okej|ok|okay)[,!]?\\s*)?"
                    + "(?:nar kommer den(?: da)?|"
                    + "when will it arrive(?: then)?)[.!?]*$",
            "(?:where is|where are).{0,25}(?:my order|my package|my items)",
            "^(?:where is|when will) it.{0,20}[.!?]*$",
            "(?:orderstatus|order status|leverans|delivery|forsandelse|shipment)",
            "(?:min|mitt|mina|my).{0,20}(?:order|bestallning|paket|varor|items)"
    );
    private static final List<Pattern> COMPANY_KNOWLEDGE = patterns(
            "(?:vad|vem|vilka).{0,30}(?:ar )?(?:nordly|ni)",
            "(?:what|who).{0,30}(?:is|are) (?:nordly|you)",
            "(?:oppettider|oppet(?:tider)?|opening hours|support hours)",
            "(?:nar|vilka tider|what time|when).{0,35}"
                    + "(?:kundservice|support|customer service).{0,20}"
                    + "(?:oppet|open|stanger|closes)?",
            "(?:kundservice|customer service).{0,35}"
                    + "(?:kontakt|contact|telefon|phone|ring|open|oppet)",
            "(?:hur hanterar|hur hjalper|how do you handle|how can you help)"
                    + ".{0,45}(?:kunder|kundarenden|customer|support)?"
    );

    public Decision classify(String message) {
        String normalized = normalize(message);
        if (matches(CHANGE_DELIVERY_ADDRESS, normalized)) {
            return decision(Intent.CHANGE_DELIVERY_ADDRESS, true);
        }
        if (matches(CANCEL_ACTION, normalized)) {
            return decision(Intent.CANCEL_ORDER, true);
        }
        if (matches(REFUND_ACTION, normalized)) {
            return decision(Intent.REFUND_ORDER, true);
        }
        if (matches(RETURN_ACTION, normalized)) {
            return decision(Intent.RETURN_ORDER, true);
        }
        if (matches(PURCHASE_ACTION, normalized)) {
            return decision(Intent.PURCHASE_ITEM, true);
        }
        if (matches(CLARIFICATION_REQUIRED, normalized)) {
            return decision(Intent.CLARIFICATION_REQUIRED, false);
        }
        if (matches(CANCEL_POLICY, normalized)) {
            return decision(Intent.CANCELLATION_POLICY, false);
        }
        if (matches(REFUND_POLICY, normalized)) {
            return decision(Intent.REFUND_POLICY, false);
        }
        if (matches(RETURN_POLICY, normalized)) {
            return decision(Intent.RETURN_POLICY, false);
        }
        if (matches(ORDER_STATUS, normalized)) {
            return decision(Intent.ORDER_STATUS, false);
        }
        if (matches(COMPANY_KNOWLEDGE, normalized)) {
            return decision(Intent.COMPANY_KNOWLEDGE, false);
        }
        return decision(Intent.UNSUPPORTED, false);
    }

    private static Decision decision(Intent intent, boolean actionRequested) {
        return new Decision(intent, CLASSIFIER, actionRequested);
    }

    private static boolean matches(List<Pattern> patterns, String value) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(value).find());
    }

    private static List<Pattern> patterns(String... expressions) {
        return Arrays.stream(expressions)
                .map(expression -> Pattern.compile(
                        expression,
                        Pattern.CASE_INSENSITIVE
                ))
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

    public enum Intent {
        ORDER_STATUS("order_status"),
        CANCELLATION_POLICY("cancellation_policy"),
        CANCEL_ORDER("cancel_order"),
        RETURN_POLICY("return_policy"),
        RETURN_ORDER("return_order"),
        REFUND_POLICY("refund_policy"),
        REFUND_ORDER("refund_order"),
        PURCHASE_ITEM("purchase_item"),
        CHANGE_DELIVERY_ADDRESS("change_delivery_address"),
        COMPANY_KNOWLEDGE("company_knowledge"),
        CONVERSATION("conversation"),
        CLARIFICATION_REQUIRED("clarification_required"),
        UNSUPPORTED("unsupported");

        private final String value;

        Intent(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public record Decision(
            Intent intent,
            String classifier,
            boolean actionRequested
    ) {
    }
}
