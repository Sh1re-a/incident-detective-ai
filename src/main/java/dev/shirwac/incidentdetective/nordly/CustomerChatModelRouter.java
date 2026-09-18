package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import java.util.Locale;
import java.util.List;
import java.util.Objects;

/**
 * Selects one bounded customer-support capability from an untrusted message.
 *
 * <p>The router does not execute the selected capability. In particular, it
 * exposes no tool that can mutate customer, order, payment, or refund state.</p>
 */
public interface CustomerChatModelRouter {

    String CLASSIFIER = "gemini_function_router_v1";
    String PROMPT_VERSION = "nordly-customer-chat-router-v1";

    RoutingResult route(RoutingRequest request);

    enum RoutingScope {
        STANDARD,
        DENY_ONLY
    }

    enum Tool {
        GET_CURRENT_ORDER("get_current_order"),
        SEARCH_APPROVED_COMPANY_KNOWLEDGE(
                "search_approved_company_knowledge"
        ),
        DENY_BUSINESS_ACTION("deny_business_action"),
        CONTINUE_CUSTOMER_CONVERSATION("continue_customer_conversation");

        private final String wireValue;

        Tool(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static Tool fromWireValue(String value) {
            for (Tool tool : values()) {
                if (tool.wireValue.equals(value)) {
                    return tool;
                }
            }
            throw new IllegalArgumentException("Unknown customer-chat tool");
        }
    }

    enum DeniedAction {
        CANCEL_ORDER("cancel_order"),
        RETURN_ORDER("return_order"),
        REFUND_ORDER("refund_order"),
        PURCHASE_ITEM("purchase_item"),
        CHANGE_DELIVERY_ADDRESS("change_delivery_address");

        private final String wireValue;

        DeniedAction(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static DeniedAction fromWireValue(String value) {
            for (DeniedAction action : values()) {
                if (action.wireValue.equals(value)) {
                    return action;
                }
            }
            throw new IllegalArgumentException("Unknown denied action");
        }
    }

    enum ConversationKind {
        GREETING("greeting"),
        THANKS("thanks"),
        CLARIFICATION("clarification"),
        OUT_OF_SCOPE("out_of_scope");

        private final String wireValue;

        ConversationKind(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static ConversationKind fromWireValue(String value) {
            for (ConversationKind kind : values()) {
                if (kind.wireValue.equals(value)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown conversation kind");
        }
    }

    record RoutingRequest(
            String message,
            String locale,
            boolean confirmLiveAi,
            RoutingScope scope,
            List<DemoCustomerChatTurnRequest.ConversationTurn> recentConversation
    ) {
        public RoutingRequest {
            message = requireText(message, "message");
            if (message.length() < 3 || message.length() > 500) {
                throw new IllegalArgumentException(
                        "message must contain between 3 and 500 characters"
                );
            }
            locale = requireText(locale, "locale")
                    .toLowerCase(Locale.ROOT);
            if (!"sv".equals(locale) && !"en".equals(locale)) {
                throw new IllegalArgumentException("locale must be sv or en");
            }
            Objects.requireNonNull(scope, "scope must not be null");
            recentConversation = recentConversation == null
                    ? List.of()
                    : List.copyOf(recentConversation);
            if (recentConversation.size() > 6
                    || recentConversation.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "recentConversation must contain at most six non-null turns"
                );
            }
        }

        public RoutingRequest(
                String message,
                String locale,
                boolean confirmLiveAi,
                RoutingScope scope
        ) {
            this(message, locale, confirmLiveAi, scope, List.of());
        }
    }

    record RoutingResult(
            String classifier,
            String promptVersion,
            String callId,
            Tool tool,
            DeniedAction deniedAction,
            ConversationKind conversationKind,
            String configuredModelId,
            String providerModelVersion,
            String providerResponseId,
            ModelTokenUsage tokenUsage,
            ModelCostEstimate costEstimate,
            long latencyMs
    ) {
        public RoutingResult {
            classifier = requireText(classifier, "classifier");
            promptVersion = requireText(promptVersion, "promptVersion");
            callId = requireText(callId, "callId");
            Objects.requireNonNull(tool, "tool must not be null");
            configuredModelId = requireText(
                    configuredModelId,
                    "configuredModelId"
            );
            Objects.requireNonNull(costEstimate, "costEstimate must not be null");
            if (providerModelVersion != null
                    && providerModelVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "providerModelVersion must be null or non-blank"
                );
            }
            if (providerResponseId != null && providerResponseId.isBlank()) {
                throw new IllegalArgumentException(
                        "providerResponseId must be null or non-blank"
                );
            }
            if (latencyMs < 0) {
                throw new IllegalArgumentException(
                        "latencyMs must not be negative"
                );
            }
            boolean validSelection = switch (tool) {
                case GET_CURRENT_ORDER,
                        SEARCH_APPROVED_COMPANY_KNOWLEDGE -> deniedAction == null
                        && conversationKind == null;
                case DENY_BUSINESS_ACTION -> deniedAction != null
                        && conversationKind == null;
                case CONTINUE_CUSTOMER_CONVERSATION -> deniedAction == null
                        && conversationKind != null;
            };
            if (!validSelection) {
                throw new IllegalArgumentException(
                        "router arguments must match the selected tool"
                );
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
