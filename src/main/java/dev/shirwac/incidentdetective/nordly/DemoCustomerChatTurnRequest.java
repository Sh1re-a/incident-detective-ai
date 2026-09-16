package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
                description = "Explicit opt-in when this turn needs the bounded "
                        + "live RAG path. Exact order reads and authority stops never "
                        + "require or spend this opt-in."
        )
        boolean confirmLiveAi
) {
}
