package dev.shirwac.incidentdetective.live;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;

/**
 * Global UTC-day budget for public live-AI starts and conservative list-price
 * allowances.
 *
 * <p>A successful decision consumes one start and the operation's full
 * conservative allowance. A rejected decision leaves both counters unchanged.</p>
 */
public interface GlobalDailyLiveQuota {

    Decision tryConsume(
            int dailyLimit,
            long dailyBudgetMicroUsd,
            long operationAllowanceMicroUsd
    );

    Snapshot snapshot(int dailyLimit, long dailyBudgetMicroUsd);

    Scope scope();

    enum Scope {
        PROCESS_LOCAL("process_local"),
        DATABASE_GLOBAL("database_global");

        private final String wireValue;

        Scope(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }

    record Decision(
            boolean allowed,
            int consumed,
            int limit,
            long consumedMicroUsd,
            long budgetMicroUsd,
            Instant resetsAt
    ) {
        public Decision {
            if (consumed < 0) {
                throw new IllegalArgumentException("consumed must not be negative");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive");
            }
            if (consumedMicroUsd < 0) {
                throw new IllegalArgumentException(
                        "consumedMicroUsd must not be negative"
                );
            }
            if (budgetMicroUsd < 1) {
                throw new IllegalArgumentException(
                        "budgetMicroUsd must be positive"
                );
            }
            if (resetsAt == null) {
                throw new IllegalArgumentException("resetsAt is required");
            }
        }

        public int remaining() {
            return Math.max(0, limit - consumed);
        }

        public long remainingMicroUsd() {
            return Math.max(0, budgetMicroUsd - consumedMicroUsd);
        }
    }

    record Snapshot(
            int consumed,
            int limit,
            long consumedMicroUsd,
            long budgetMicroUsd,
            Instant resetsAt
    ) {
        public Snapshot {
            if (consumed < 0 || limit < 1) {
                throw new IllegalArgumentException("invalid start counters");
            }
            if (consumedMicroUsd < 0 || budgetMicroUsd < 1) {
                throw new IllegalArgumentException("invalid budget counters");
            }
            if (resetsAt == null) {
                throw new IllegalArgumentException("resetsAt is required");
            }
        }

        public int remaining() {
            return Math.max(0, limit - consumed);
        }

        public long remainingMicroUsd() {
            return Math.max(0, budgetMicroUsd - consumedMicroUsd);
        }

        public boolean canConsume(long operationAllowanceMicroUsd) {
            return remaining() > 0
                    && operationAllowanceMicroUsd > 0
                    && operationAllowanceMicroUsd <= remainingMicroUsd();
        }
    }
}
