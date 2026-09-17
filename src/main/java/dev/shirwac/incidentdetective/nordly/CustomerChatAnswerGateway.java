package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.ai.ModelCostEstimate;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates one natural customer-service answer from a bounded evidence list.
 *
 * <p>The caller owns routing, retrieval, tool execution and business authority.
 * This gateway receives only the evidence that the model may use.</p>
 */
public interface CustomerChatAnswerGateway {

    int MAX_EVIDENCE_ITEMS = 8;

    Result generate(boolean confirmLiveAi, Input input);

    record Input(
            String customerMessage,
            String locale,
            String routedIntent,
            String routedOutcome,
            List<DemoCustomerChatTurnRequest.ConversationTurn> recentConversation,
            List<Evidence> evidence
    ) {
        public Input {
            customerMessage = required(
                    customerMessage,
                    "customerMessage",
                    500
            );
            locale = required(locale, "locale", 2);
            if (!Set.of("sv", "en").contains(locale)) {
                throw new IllegalArgumentException("locale must be sv or en");
            }
            routedIntent = token(routedIntent, "routedIntent");
            routedOutcome = token(routedOutcome, "routedOutcome");
            recentConversation = recentConversation == null
                    ? List.of()
                    : List.copyOf(recentConversation);
            if (recentConversation.size() > 6
                    || recentConversation.stream().anyMatch(item -> item == null)) {
                throw new IllegalArgumentException(
                        "recentConversation must contain at most six non-null turns"
                );
            }
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            if (evidence.size() > MAX_EVIDENCE_ITEMS) {
                throw new IllegalArgumentException(
                        "evidence must contain at most " + MAX_EVIDENCE_ITEMS
                                + " items"
                );
            }
            Set<String> evidenceIds = new HashSet<>();
            if (evidence.stream().anyMatch(item -> item == null
                    || !evidenceIds.add(item.id()))) {
                throw new IllegalArgumentException(
                        "evidence items must be non-null and have unique IDs"
                );
            }
        }

        public Input(
                String customerMessage,
                String locale,
                String routedIntent,
                String routedOutcome,
                List<Evidence> evidence
        ) {
            this(
                    customerMessage,
                    locale,
                    routedIntent,
                    routedOutcome,
                    List.of(),
                    evidence
            );
        }
    }

    record Evidence(
            String id,
            String title,
            String text
    ) {
        public Evidence {
            id = required(id, "evidence.id", 160);
            title = required(title, "evidence.title", 240);
            text = required(text, "evidence.text", 4_000);
        }
    }

    record Answer(
            String textSv,
            String textEn,
            List<Claim> claims
    ) {
        public Answer {
            textSv = required(textSv, "textSv", 700);
            textEn = required(textEn, "textEn", 700);
            claims = claims == null ? null : List.copyOf(claims);
            if (claims == null || claims.size() > 3
                    || claims.stream().anyMatch(claim -> claim == null)) {
                throw new IllegalArgumentException(
                        "claims must contain between zero and three items"
                );
            }
        }
    }

    record Claim(
            String textSv,
            String textEn,
            List<String> citationIds
    ) {
        public Claim {
            textSv = required(textSv, "claim.textSv", 500);
            textEn = required(textEn, "claim.textEn", 500);
            citationIds = citationIds == null
                    ? null
                    : List.copyOf(citationIds);
            if (citationIds == null
                    || citationIds.isEmpty()
                    || citationIds.size() > 3
                    || citationIds.stream().anyMatch(value -> value == null
                    || value.isBlank()
                    || value.length() > 160)
                    || new HashSet<>(citationIds).size()
                    != citationIds.size()) {
                throw new IllegalArgumentException(
                        "claim citations must contain one to three unique IDs"
                );
            }
        }
    }

    record Result(
            Answer answer,
            ProviderMetadata provider,
            ModelCostEstimate costEstimate
    ) {
        public Result {
            if (answer == null || provider == null || costEstimate == null) {
                throw new IllegalArgumentException(
                        "answer, provider metadata and cost estimate are required"
                );
            }
        }
    }

    record ProviderMetadata(
            GoogleGenAiProviderRoute route,
            String responseId,
            String modelVersion,
            ModelTokenUsage tokenUsage,
            long latencyMs
    ) {
        public ProviderMetadata {
            if (route == null) {
                throw new IllegalArgumentException("provider route is required");
            }
            if (modelVersion == null || modelVersion.isBlank()) {
                throw new IllegalArgumentException("modelVersion is required");
            }
            if (latencyMs < 0) {
                throw new IllegalArgumentException(
                        "latencyMs must not be negative"
                );
            }
        }
    }

    private static String required(
            String value,
            String name,
            int maxLength
    ) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must contain between 1 and " + maxLength
                            + " characters"
            );
        }
        return value.strip();
    }

    private static String token(String value, String name) {
        String normalized = required(value, name, 80);
        if (!normalized.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                    name + " must be a lowercase wire token"
            );
        }
        return normalized;
    }
}
