package dev.shirwac.incidentdetective.nordly;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoCustomerIntentClassifierTest {

    private final DemoCustomerIntentClassifier classifier =
            new DemoCustomerIntentClassifier();

    @ParameterizedTest
    @MethodSource("examples")
    void separatesInformationRequestsFromRequestedActions(
            String message,
            DemoCustomerIntentClassifier.Intent expected,
            boolean actionRequested
    ) {
        DemoCustomerIntentClassifier.Decision decision =
                classifier.classify(message);

        assertEquals(expected, decision.intent(), message);
        assertEquals(actionRequested, decision.actionRequested(), message);
        assertEquals(
                DemoCustomerIntentClassifier.CLASSIFIER,
                decision.classifier()
        );
    }

    private static Stream<Arguments> examples() {
        return Stream.of(
                Arguments.of(
                        "Var är min beställning?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Where is my order?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "När kommer den?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Var är den?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Vilka varor finns i min beställning?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Vad beställde jag?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "What did I order?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Har den skickats?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Är den skickad?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Okej, när kommer den då?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Has it shipped?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Is it shipped?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "Okay, when will it arrive then?",
                        DemoCustomerIntentClassifier.Intent.ORDER_STATUS,
                        false
                ),
                Arguments.of(
                        "När har kundservice öppet?",
                        DemoCustomerIntentClassifier.Intent.COMPANY_KNOWLEDGE,
                        false
                ),
                Arguments.of(
                        "Vad är Nordly för företag?",
                        DemoCustomerIntentClassifier.Intent.COMPANY_KNOWLEDGE,
                        false
                ),
                Arguments.of(
                        "When is customer service open?",
                        DemoCustomerIntentClassifier.Intent.COMPANY_KNOWLEDGE,
                        false
                ),
                Arguments.of(
                        "Kan jag få pengarna utbetalda?",
                        DemoCustomerIntentClassifier.Intent.REFUND_POLICY,
                        false
                ),
                Arguments.of(
                        "Betala ut pengarna nu",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Can you pay the money out?",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill ändra leveransadressen",
                        DemoCustomerIntentClassifier.Intent.CHANGE_DELIVERY_ADDRESS,
                        true
                ),
                Arguments.of(
                        "Kan du uppdatera leveransadressen?",
                        DemoCustomerIntentClassifier.Intent.CHANGE_DELIVERY_ADDRESS,
                        true
                ),
                Arguments.of(
                        "I want to change the delivery address",
                        DemoCustomerIntentClassifier.Intent.CHANGE_DELIVERY_ADDRESS,
                        true
                ),
                Arguments.of(
                        "Could you update the shipping address?",
                        DemoCustomerIntentClassifier.Intent.CHANGE_DELIVERY_ADDRESS,
                        true
                ),
                Arguments.of(
                        "Gör det",
                        DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED,
                        false
                ),
                Arguments.of(
                        "Okej, gör det nu.",
                        DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED,
                        false
                ),
                Arguments.of(
                        "Kör på",
                        DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED,
                        false
                ),
                Arguments.of(
                        "Do it",
                        DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED,
                        false
                ),
                Arguments.of(
                        "Go ahead",
                        DemoCustomerIntentClassifier.Intent.CLARIFICATION_REQUIRED,
                        false
                ),
                Arguments.of(
                        "När får den avbeställas enligt policyn?",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Kan jag avbryta den?",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Jag vill veta när man får avbeställa enligt policyn",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Vilka regler gäller för avbeställning?",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Vad gäller om jag vill avbeställa min order?",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "I want to know when I can cancel",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Avbeställ den nu",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Kan du avbeställa den åt mig?",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Avbeställ min order tack",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag behöver avbeställa min order",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Can you cancel it for me?",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Avbryt min beställning",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill inte ha paketet längre",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "I don't want the package anymore",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Kan ni avbeställa min beställning?",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Går det att avbeställa ordern?",
                        DemoCustomerIntentClassifier.Intent.CANCELLATION_POLICY,
                        false
                ),
                Arguments.of(
                        "Vilka regler gäller för avbeställning, och avbeställ den nu",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "Vilka regler gäller för avbeställning? Gör det nu.",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "What are the cancellation rules? Cancel it now.",
                        DemoCustomerIntentClassifier.Intent.CANCEL_ORDER,
                        true
                ),
                Arguments.of(
                        "När syns en godkänd återbetalning på kortet?",
                        DemoCustomerIntentClassifier.Intent.REFUND_POLICY,
                        false
                ),
                Arguments.of(
                        "Vilka regler gäller för retur?",
                        DemoCustomerIntentClassifier.Intent.RETURN_POLICY,
                        false
                ),
                Arguments.of(
                        "Kan du återbetala den åt mig?",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Återbetala den nu",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill ha pengarna tillbaka",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill få tillbaka pengarna",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "I want my money back",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Kan du ge mig pengarna tillbaka?",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Kan du betala tillbaks till mig?",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill ha pengarna tillbaks",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "I want a refund",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Can you issue a refund?",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Skapa en retur",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Starta en retur åt mig",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Starta en retur för min beställning.",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Create a return for my order.",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Skapa en retur för ordern.",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Hur startar jag en retur för min beställning?",
                        DemoCustomerIntentClassifier.Intent.RETURN_POLICY,
                        false
                ),
                Arguments.of(
                        "Kan du skapa en retur åt mig?",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill returnera varan",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "I want to return my item",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Can you return my order?",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Please return my item",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "I would like a refund",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Refund my order now",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Vilka regler gäller för återbetalning, och återbetala den nu",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "How do refunds work? Give me my money back.",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag behöver en återbetalning",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "I need a refund",
                        DemoCustomerIntentClassifier.Intent.REFUND_ORDER,
                        true
                ),
                Arguments.of(
                        "Jag vill beställa en till vara",
                        DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM,
                        true
                ),
                Arguments.of(
                        "Okej jag vill beställa en till vara",
                        DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM,
                        true
                ),
                Arguments.of(
                        "Kan du lägga till en produkt åt mig?",
                        DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM,
                        true
                ),
                Arguments.of(
                        "I want to order another item",
                        DemoCustomerIntentClassifier.Intent.PURCHASE_ITEM,
                        true
                ),
                Arguments.of(
                        "Vilka regler gäller för retur, och skapa en retur nu",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Vad gäller för retur? Starta den åt mig.",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "What is the return policy? Create a return for me.",
                        DemoCustomerIntentClassifier.Intent.RETURN_ORDER,
                        true
                ),
                Arguments.of(
                        "Kan jag returnera min vara?",
                        DemoCustomerIntentClassifier.Intent.RETURN_POLICY,
                        false
                ),
                Arguments.of(
                        "Vilken färg har ert kontor?",
                        DemoCustomerIntentClassifier.Intent.UNSUPPORTED,
                        false
                )
        );
    }
}
