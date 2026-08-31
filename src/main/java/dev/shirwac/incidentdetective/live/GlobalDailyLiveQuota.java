package dev.shirwac.incidentdetective.live;

import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Instant;

/**
 * Global UTC-day budget for public live-AI starts.
 *
 * <p>A successful decision consumes one start. A rejected decision leaves the
 * current count unchanged.</p>
 */
public interface GlobalDailyLiveQuota {

    Decision tryConsume(int dailyLimit);

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
            Instant resetsAt
    ) {
        public Decision {
            if (consumed < 0) {
                throw new IllegalArgumentException("consumed must not be negative");
            }
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive");
            }
            if (resetsAt == null) {
                throw new IllegalArgumentException("resetsAt is required");
            }
        }

        public int remaining() {
            return Math.max(0, limit - consumed);
        }
    }
}
