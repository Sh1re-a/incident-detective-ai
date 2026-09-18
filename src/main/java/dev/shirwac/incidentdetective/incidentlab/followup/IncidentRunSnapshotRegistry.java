package dev.shirwac.incidentdetective.incidentlab.followup;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Cloud-safe store for sanitized, synthetic post-run snapshots and idempotency. */
@Component
@Profile("rag")
public final class IncidentRunSnapshotRegistry {

    static final Duration TTL = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public IncidentRunSnapshotRegistry(
            JdbcClient jdbc,
            JsonMapper jsonMapper,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.jsonMapper = Objects.requireNonNull(jsonMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    public String register(IncidentFollowUpSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        String reference = "ilr_" + UUID.randomUUID()
                .toString()
                .replace("-", "");
        String json = serialize(snapshot);
        String hash = sha256(json);
        OffsetDateTime expiresAt = pgTimestamp(clock.instant().plus(TTL));
        jdbc.sql("""
                        INSERT INTO incident_followup_runs (
                            run_reference, mode, scenario_id, answer_state,
                            snapshot_sha256, snapshot_json, expires_at
                        ) VALUES (
                            :runReference, :mode, :scenarioId, :answerState,
                            :snapshotSha256, CAST(:snapshotJson AS jsonb), :expiresAt
                        )
                        """)
                .param("runReference", reference)
                .param("mode", snapshot.mode().name())
                .param("scenarioId", snapshot.scenarioId())
                .param("answerState", snapshot.originalAnswerState().name())
                .param("snapshotSha256", hash)
                .param("snapshotJson", json)
                .param("expiresAt", expiresAt)
                .update();
        return reference;
    }

    public Optional<StoredSnapshot> find(String reference) {
        return jdbc.sql("""
                        SELECT snapshot_json::text, snapshot_sha256
                        FROM incident_followup_runs
                        WHERE run_reference = :runReference
                          AND expires_at > :now
                        """)
                .param("runReference", reference)
                .param("now", pgTimestamp(clock.instant()))
                .query((resultSet, rowNumber) -> {
                    String json = resultSet.getString("snapshot_json");
                    String storedHash = resultSet.getString("snapshot_sha256");
                    IncidentFollowUpSnapshot snapshot = deserialize(
                            json,
                            IncidentFollowUpSnapshot.class
                    );
                    String canonicalJson = serialize(snapshot);
                    if (!MessageDigest.isEqual(
                            storedHash.getBytes(StandardCharsets.US_ASCII),
                            sha256(canonicalJson).getBytes(StandardCharsets.US_ASCII)
                    )) {
                        throw new IncidentFollowUpException(
                                IncidentFollowUpException.Code.RESPONSE_NOT_VERIFIABLE,
                                "The stored incident snapshot failed its integrity check"
                        );
                    }
                    return new StoredSnapshot(
                            reference,
                            snapshot,
                            storedHash
                    );
                })
                .optional();
    }

    public Optional<IncidentFollowUpResponse> cachedTurn(
            String runReference,
            String clientTurnId,
            String requestFingerprint
    ) {
        return jdbc.sql("""
                        SELECT request_sha256, status, response_json::text
                        FROM incident_followup_turns
                        WHERE run_reference = :runReference
                          AND client_turn_id = :clientTurnId
                        """)
                .param("runReference", runReference)
                .param("clientTurnId", clientTurnId)
                .query((resultSet, rowNumber) -> {
                    String stored = resultSet.getString("request_sha256");
                    if (!stored.equals(requestFingerprint)) {
                        throw new IncidentFollowUpException(
                                IncidentFollowUpException.Code.IDEMPOTENCY_CONFLICT,
                                "clientTurnId was already used for another follow-up"
                        );
                    }
                    if (!"completed".equals(resultSet.getString("status"))) {
                        throw new IncidentFollowUpException(
                                IncidentFollowUpException.Code.TURN_ALREADY_ATTEMPTED,
                                "The follow-up was already attempted; automatic retry is disabled"
                        );
                    }
                    return deserialize(
                            resultSet.getString("response_json"),
                            IncidentFollowUpResponse.class
                    );
                })
                .optional();
    }

    public boolean reserveTurn(
            String runReference,
            String clientTurnId,
            String requestFingerprint
    ) {
        return jdbc.sql("""
                        INSERT INTO incident_followup_turns (
                            run_reference, client_turn_id, request_sha256, status
                        ) VALUES (
                            :runReference, :clientTurnId, :requestSha256, 'started'
                        )
                        ON CONFLICT (run_reference, client_turn_id) DO NOTHING
                        """)
                .param("runReference", runReference)
                .param("clientTurnId", clientTurnId)
                .param("requestSha256", requestFingerprint)
                .update() == 1;
    }

    public void completeTurn(
            String runReference,
            String clientTurnId,
            IncidentFollowUpResponse response
    ) {
        jdbc.sql("""
                        UPDATE incident_followup_turns
                        SET status = 'completed',
                            response_json = CAST(:responseJson AS jsonb),
                            completed_at = CURRENT_TIMESTAMP
                        WHERE run_reference = :runReference
                          AND client_turn_id = :clientTurnId
                          AND status = 'started'
                        """)
                .param("responseJson", serialize(response))
                .param("runReference", runReference)
                .param("clientTurnId", clientTurnId)
                .update();
    }

    public void failTurn(String runReference, String clientTurnId) {
        jdbc.sql("""
                        UPDATE incident_followup_turns
                        SET status = 'failed', completed_at = CURRENT_TIMESTAMP
                        WHERE run_reference = :runReference
                          AND client_turn_id = :clientTurnId
                          AND status = 'started'
                        """)
                .param("runReference", runReference)
                .param("clientTurnId", clientTurnId)
                .update();
    }

    public String fingerprint(Object value) {
        return sha256(serialize(value));
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize incident snapshot", exception);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read incident snapshot", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash incident snapshot", exception);
        }
    }

    private OffsetDateTime pgTimestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record StoredSnapshot(
            String runReference,
            IncidentFollowUpSnapshot snapshot,
            String snapshotSha256
    ) {
    }
}
