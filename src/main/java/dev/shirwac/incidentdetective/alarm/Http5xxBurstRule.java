package dev.shirwac.incidentdetective.alarm;

import dev.shirwac.incidentdetective.domain.evidence.LogEvidence;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure-Java rule that detects three HTTP 5xx responses in thirty seconds. */
public final class Http5xxBurstRule {

    public static final String RULE_ID = "HTTP_5XX_BURST_V1";
    public static final String HTTP_STATUS_ATTRIBUTE = "http_status";
    public static final int THRESHOLD = 3;
    public static final Duration WINDOW = Duration.ofSeconds(30);

    private static final Comparator<LogEvidence> EVENT_ORDER = Comparator
            .comparing(LogEvidence::observedAt)
            .thenComparing(LogEvidence::scenarioId)
            .thenComparing(log -> log.content().service())
            .thenComparing(LogEvidence::evidenceId);

    public Optional<AlarmReceipt> evaluate(List<LogEvidence> logs) {
        Objects.requireNonNull(logs, "logs must not be null");
        List<LogEvidence> ordered = new ArrayList<>(logs.size());
        for (LogEvidence log : logs) {
            ordered.add(Objects.requireNonNull(log, "logs must not contain null values"));
        }
        ordered.sort(EVENT_ORDER);

        Map<SignalKey, ArrayDeque<LogEvidence>> activeWindows = new HashMap<>();
        for (LogEvidence log : ordered) {
            if (!isHttp5xx(log)) {
                continue;
            }

            SignalKey key = new SignalKey(
                    log.scenarioId(),
                    log.content().service()
            );
            ArrayDeque<LogEvidence> failures = activeWindows.computeIfAbsent(
                    key,
                    ignored -> new ArrayDeque<>()
            );
            var cutoff = log.observedAt().minus(WINDOW);
            while (!failures.isEmpty()
                    && failures.getFirst().observedAt().isBefore(cutoff)) {
                failures.removeFirst();
            }
            failures.addLast(log);

            if (failures.size() == THRESHOLD) {
                List<LogEvidence> triggerEvidence = List.copyOf(failures);
                return Optional.of(receipt(key, log, triggerEvidence));
            }
        }
        return Optional.empty();
    }

    private boolean isHttp5xx(LogEvidence log) {
        String rawStatus = log.content().attributes().get(HTTP_STATUS_ATTRIBUTE);
        if (rawStatus == null) {
            return false;
        }
        try {
            int status = Integer.parseInt(rawStatus);
            return status >= 500 && status <= 599;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private AlarmReceipt receipt(
            SignalKey key,
            LogEvidence trigger,
            List<LogEvidence> triggerEvidence
    ) {
        return new AlarmReceipt(
                "%s-http-5xx-burst-%d".formatted(
                        key.scenarioId(),
                        trigger.observedAt().toEpochMilli()
                ),
                RULE_ID,
                key.scenarioId(),
                key.service(),
                trigger.observedAt(),
                triggerEvidence.getFirst().observedAt(),
                THRESHOLD,
                triggerEvidence.size(),
                WINDOW.toSeconds(),
                triggerEvidence.stream().map(LogEvidence::evidenceId).toList()
        );
    }

    private record SignalKey(String scenarioId, String service) {
    }
}
