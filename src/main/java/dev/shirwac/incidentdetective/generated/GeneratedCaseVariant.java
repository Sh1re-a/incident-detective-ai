package dev.shirwac.incidentdetective.generated;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Stable identity for one deterministic generator recipe and scenario. */
public record GeneratedCaseVariant(
        String variantId,
        String fingerprint
) {
    private static final String FINGERPRINT_FORMAT_VERSION =
            "generated-case-variant-fingerprint-v1";
    private static final int ID_FINGERPRINT_LENGTH = 16;

    public GeneratedCaseVariant {
        if (variantId == null || variantId.isBlank()) {
            throw new IllegalArgumentException("variantId must not be blank");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "fingerprint must be a lowercase SHA-256 value"
            );
        }
    }

    public static GeneratedCaseVariant from(
            String generatorVersion,
            GeneratedCaseRequest request,
            String scenarioId
    ) {
        Objects.requireNonNull(request, "request must not be null");
        MessageDigest digest = sha256Digest();
        update(digest, FINGERPRINT_FORMAT_VERSION);
        update(digest, requireText(generatorVersion, "generatorVersion"));
        update(digest, Long.toString(request.seed()));
        update(digest, request.incidentFamily().wireValue());
        update(digest, request.evidenceMode().wireValue());
        update(digest, request.noiseLevel().wireValue());
        update(digest, requireText(scenarioId, "scenarioId"));

        String fingerprint = HexFormat.of().formatHex(digest.digest());
        String family = request.incidentFamily().wireValue().replace('_', '-');
        return new GeneratedCaseVariant(
                family + "-" + fingerprint.substring(0, ID_FINGERPRINT_LENGTH),
                fingerprint
        );
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length)
                .array());
        digest.update(bytes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
