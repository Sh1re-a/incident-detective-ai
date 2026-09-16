package dev.shirwac.incidentdetective.nordly;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(
        description = "One controlled turn for a fixed synthetic Nordly customer. "
                + "The transcript may be retained by the browser, but the backend "
                + "uses no persistent conversation memory and exposes no write tool."
)
public record DemoCustomerChatTurnResponse(
        String contractVersion,
        String turnId,
        String mode,
        String truthLabel,
        String truthLabelEn,
        String outcome,
        SubmittedMessage submittedMessage,
        ContextReceipt context,
        IntentDecision intent,
        SafetyDecision safety,
        AssistantMessage assistantMessage,
        @Schema(nullable = true)
        DemoOrderSnapshot order,
        List<ToolEvent> toolEvents,
        List<Source> sources,
        List<VerifiedClaim> verifiedClaims,
        RagExecution rag,
        Verification verification,
        Receipt receipt,
        @Schema(nullable = true)
        ErrorDetail error,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION =
            "nordly-demo-customer-chat-turn-v1";
    public static final String MODE = "controlled_synthetic_customer_chat";

    public DemoCustomerChatTurnResponse {
        toolEvents = List.copyOf(toolEvents);
        sources = List.copyOf(sources);
        verifiedClaims = List.copyOf(verifiedClaims);
        limitations = List.copyOf(limitations);
    }

    @Schema(name = "DemoCustomerSubmittedMessage")
    public record SubmittedMessage(
            String text,
            String locale,
            boolean redacted
    ) {
    }

    @Schema(name = "DemoCustomerContextReceipt")
    public record ContextReceipt(
            String contextVersion,
            String contextId,
            String currentOrderId,
            String contextSourceRef,
            String orderSourceRef,
            boolean syntheticOnly,
            boolean persistentMemory,
            String memoryScope
    ) {
        public ContextReceipt {
            if (!syntheticOnly || persistentMemory) {
                throw new IllegalArgumentException(
                        "Demo customer context must be synthetic and stateless"
                );
            }
        }
    }

    @Schema(name = "DemoCustomerIntentDecision")
    public record IntentDecision(
            String name,
            String classifier,
            boolean actionRequested
    ) {
    }

    @Schema(name = "DemoCustomerSafetyDecision")
    public record SafetyDecision(
            String decision,
            String reasonCode,
            String summarySv,
            String summaryEn
    ) {
    }

    @Schema(name = "DemoCustomerAssistantMessage")
    public record AssistantMessage(
            String textSv,
            String textEn
    ) {
    }

    @Schema(name = "DemoCustomerToolEvent")
    public record ToolEvent(
            int sequence,
            String type,
            String initiatedBy,
            boolean modelSelected,
            String name,
            String status,
            boolean executed,
            String summarySv,
            String summaryEn,
            @Schema(nullable = true)
            String sourceRef,
            List<String> evidenceIds,
            @Schema(nullable = true)
            Long latencyMs
    ) {
        public ToolEvent {
            evidenceIds = List.copyOf(evidenceIds);
        }
    }

    @Schema(name = "DemoCustomerSource")
    public record Source(
            String kind,
            @Schema(nullable = true)
            String documentId,
            @Schema(nullable = true)
            String chunkId,
            @Schema(nullable = true)
            String documentVersion,
            String title,
            @Schema(nullable = true)
            String sectionHeading,
            String sourceRef,
            String evidenceId,
            @Schema(nullable = true)
            String lifecycle,
            @Schema(nullable = true)
            Double similarity,
            String displaySummarySv,
            String displaySummaryEn
    ) {
    }

    @Schema(
            name = "DemoCustomerVerifiedClaim",
            description = "A released claim and the exact returned evidence IDs "
                    + "that survived Java verification. Empty when no grounded "
                    + "claim was released."
    )
    public record VerifiedClaim(
            String textSv,
            String textEn,
            List<String> citationIds
    ) {
        public VerifiedClaim {
            citationIds = List.copyOf(citationIds);
        }
    }

    @Schema(name = "DemoCustomerRagExecution")
    public record RagExecution(
            boolean requested,
            String outcome,
            @Schema(nullable = true)
            String backend,
            @Schema(nullable = true)
            String corpusVersion,
            @Schema(nullable = true)
            String corpusContentSha256,
            boolean currentVectorSearch,
            boolean embeddingExecuted,
            @Schema(nullable = true)
            String embeddingProvider,
            @Schema(nullable = true)
            String embeddingModelId,
            @Schema(nullable = true)
            Integer embeddingDimensions,
            int vectorMatchCount,
            String verificationOutcome,
            @Schema(nullable = true)
            GoogleGenAiProviderRoute providerRoute,
            @Schema(nullable = true)
            String generationModelId,
            @Schema(nullable = true)
            String providerResponseId,
            @Schema(nullable = true)
            ModelTokenUsage tokenUsage,
            @Schema(nullable = true)
            String errorCode
    ) {
    }

    @Schema(name = "DemoCustomerVerification")
    public record Verification(
            String evaluationStatus,
            boolean fixedCustomerScope,
            boolean orderSourceVerified,
            boolean approvedPoliciesOnly,
            boolean citationsWithinReturnedSources,
            boolean semanticClaimSupportEvaluated,
            @Schema(
                    description = "No tool can change customer, order, refund "
                            + "or other business state. Quota accounting and "
                            + "observability are outside this scope."
            )
            boolean noBusinessWriteCapability,
            boolean businessActionExecuted,
            String overallOutcome
    ) {
        public Verification {
            if (!noBusinessWriteCapability || businessActionExecuted) {
                throw new IllegalArgumentException(
                        "Demo customer verification must retain the business read-only boundary"
                );
            }
        }
    }

    @Schema(name = "DemoCustomerReceipt")
    public record Receipt(
            int readOperations,
            int providerCalls,
            int embeddingCalls,
            int vectorSearches,
            int generationCalls,
            int businessWriteOperations,
            String businessWriteScope,
            boolean businessWriteToolsAvailable,
            boolean businessActionExecuted,
            boolean persistentMemoryUsed,
            long totalLatencyMs,
            @Schema(nullable = true)
            String modelId,
            @Schema(nullable = true)
            BigDecimal estimatedCostUsd,
            String costStatus,
            String costBasis
    ) {
        public Receipt {
            if (readOperations < 0
                    || providerCalls < 0
                    || embeddingCalls < 0
                    || vectorSearches < 0
                    || generationCalls < 0
                    || businessWriteOperations != 0
                    || !"customer_order_and_refund_state".equals(
                    businessWriteScope
            )
                    || businessWriteToolsAvailable
                    || businessActionExecuted
                    || persistentMemoryUsed
                    || totalLatencyMs < 0) {
                throw new IllegalArgumentException(
                        "Demo customer receipts must prove bounded reads and no business writes"
                );
            }
        }
    }

    @Schema(name = "DemoCustomerErrorDetail")
    public record ErrorDetail(
            String code,
            String summarySv,
            String summaryEn
    ) {
    }
}
