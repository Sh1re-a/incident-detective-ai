package dev.shirwac.incidentdetective.live;

/**
 * Conservative list-price allowance consumed before a public provider call.
 *
 * <p>The allowance is deliberately larger than observed successful runs and is
 * never refunded during the UTC day. This bounds provider-list-price exposure
 * even when usage metadata is missing or the provider outcome is uncertain.</p>
 */
public enum LiveAiOperation {
    CUSTOMER_CHAT_ROUTE(5_000, false),
    CUSTOMER_CHAT_ANSWER(5_000, false),
    INCIDENT_FOLLOW_UP(5_000, false),
    INCIDENT_PLAN(5_000, false),
    KNOWLEDGE_RAG(10_000, true),
    ADK_TURN(20_000, true),
    LEGACY_INVESTIGATION(25_000, true);

    private final long allowanceMicroUsd;
    private final boolean embeddingPossible;

    LiveAiOperation(long allowanceMicroUsd, boolean embeddingPossible) {
        this.allowanceMicroUsd = allowanceMicroUsd;
        this.embeddingPossible = embeddingPossible;
    }

    public long allowanceMicroUsd() {
        return allowanceMicroUsd;
    }

    public boolean embeddingPossible() {
        return embeddingPossible;
    }
}
