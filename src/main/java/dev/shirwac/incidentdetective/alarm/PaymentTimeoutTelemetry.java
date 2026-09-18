package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A fixed, request-local payment timeout signal series with no external I/O. */
public record PaymentTimeoutTelemetry(
        String scenarioId,
        List<LogEvidence> logs
) {

    public static final String INCIDENT_FAMILY = "PAYMENT_TIMEOUT";
    public static final String SERVICE = "PAYMENT_ADAPTER";
    public static final String METHOD = "POST";
    public static final String PATH = "/payments/authorize";
    public static final String EVENT_KIND = "HTTP_RESPONSE";

    public PaymentTimeoutTelemetry {
        requireText(scenarioId, "scenarioId");
        Objects.requireNonNull(logs, "logs must not be null");
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("logs must not be empty");
        }
        if (logs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("logs must not contain null values");
        }
        if (logs.stream().anyMatch(log -> !scenarioId.equals(log.scenarioId()))) {
            throw new IllegalArgumentException("all logs must belong to scenarioId");
        }
        logs = List.copyOf(logs);
    }

    public static PaymentTimeoutTelemetry startingAt(
            String scenarioId,
            Instant startedAt
    ) {
        requireText(scenarioId, "scenarioId");
        Objects.requireNonNull(startedAt, "startedAt must not be null");

        return new PaymentTimeoutTelemetry(
                scenarioId,
                List.of(
                        log(scenarioId, startedAt, 1, 200),
                        log(scenarioId, startedAt.plusSeconds(5), 2, 200),
                        log(scenarioId, startedAt.plusSeconds(10), 3, 200),
                        log(scenarioId, startedAt.plusSeconds(20), 4, 504),
                        log(scenarioId, startedAt.plusSeconds(32), 5, 504),
                        log(scenarioId, startedAt.plusSeconds(45), 6, 504)
                )
        );
    }

    private static LogEvidence log(
            String scenarioId,
            Instant observedAt,
            int sequence,
            int httpStatus
    ) {
        boolean failed = httpStatus >= 500;
        String evidenceId = "%s-payment-http-%03d".formatted(scenarioId, sequence);
        String requestId = "synthetic-payment-%03d".formatted(sequence);
        String summary = failed
                ? "Synthetic payment authorization timed out with HTTP 504."
                : "Synthetic payment authorization completed with HTTP 200.";
        String message = failed
                ? "Synthetic payment provider exceeded the configured timeout."
                : "Synthetic payment authorization completed.";

        return new LogEvidence(
                evidenceId,
                scenarioId,
                observedAt,
                summary,
                "synthetic/%s/logs/payment/%03d".formatted(scenarioId, sequence),
                new LogEvidence.LogContent(
                        SERVICE,
                        failed ? "ERROR" : "INFO",
                        message,
                        Map.of(
                                Http5xxBurstRule.HTTP_STATUS_ATTRIBUTE,
                                Integer.toString(httpStatus),
                                "incident_family",
                                INCIDENT_FAMILY,
                                "event_kind",
                                EVENT_KIND,
                                "method",
                                METHOD,
                                "path",
                                PATH,
                                "request_id",
                                requestId,
                                "synthetic",
                                "true"
                        )
                )
        );
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
