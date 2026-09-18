package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DemoCustomerChatTurnRequest(
        @NotBlank
        @Size(min = 3, max = 500)
        @Schema(
                description = "One customer message. The server binds it to the "
                        + "fixed synthetic customer; no customer or order ID is accepted."
        )
        String message,
        @NotBlank
        @Pattern(regexp = "sv|en")
        String locale,
        @Schema(
                description = "Explicit opt-in for Gemini to interpret the free-text "
                        + "message, select one allowlisted read or boundary tool, and compose "
                        + "the final reply only from the backend's bounded evidence. Hard "
                        + "safety refusals still stop before any provider call."
        )
        boolean confirmLiveAi,
        @Size(max = 6)
        @Schema(
                description = "Optional bounded transcript supplied by the browser for "
                        + "conversational continuity. It is untrusted context, never a "
                        + "factual source; order and policy facts are read again from the backend."
        )
        List<@Valid ConversationTurn> recentConversation
) {
    public DemoCustomerChatTurnRequest {
        recentConversation = recentConversation == null
                ? List.of()
                : List.copyOf(recentConversation);
        if (recentConversation.size() > 6) {
            throw new IllegalArgumentException(
                    "recentConversation must contain at most six turns"
            );
        }
    }

    public DemoCustomerChatTurnRequest(
            String message,
            String locale,
            boolean confirmLiveAi
    ) {
        this(message, locale, confirmLiveAi, List.of());
    }

    public record ConversationTurn(
            @NotBlank
            @Size(max = 500)
            String customerMessage,
            @NotBlank
            @Size(max = 700)
            String assistantMessage
    ) {
        public ConversationTurn {
            customerMessage = requireText(
                    customerMessage,
                    "customerMessage",
                    500
            );
            assistantMessage = requireText(
                    assistantMessage,
                    "assistantMessage",
                    700
            );
        }

        private static String requireText(
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
    }
}
