package dev.shirwac.incidentdetective.ai;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiDiagnosisDecoderTest {

    private final GeminiDiagnosisDecoder decoder = new GeminiDiagnosisDecoder(
            JsonMapper.builder()
                    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .build(),
            Validation.buildDefaultValidatorFactory().getValidator()
    );

    @Test
    void decodesAValidDiagnosis() {
        String json = """
                {
                  "status": "insufficient_evidence",
                  "root_cause_code": null,
                  "affected_service": null,
                  "business_summary": "Checkout failures need more evidence.",
                  "technical_summary": "Metrics alone do not prove a root cause.",
                  "claims": [],
                  "safe_next_step": {
                    "summary": "Collect logs and traces after human review.",
                    "requires_human_approval": true
                  }
                }
                """;

        assertEquals("Checkout failures need more evidence.",
                decoder.decode(json).businessSummary());
    }

    @Test
    void rejectsBrokenJsonAndUnknownGroundTruthFields() {
        ModelProviderException broken = assertThrows(
                ModelProviderException.class,
                () -> decoder.decode("{not-json")
        );
        ModelProviderException leakedField = assertThrows(
                ModelProviderException.class,
                () -> decoder.decode("""
                        {
                          "status": "insufficient_evidence",
                          "root_cause_code": null,
                          "affected_service": null,
                          "business_summary": "Needs evidence.",
                          "technical_summary": "Needs evidence.",
                          "claims": [],
                          "safe_next_step": {
                            "summary": "Collect more evidence.",
                            "requires_human_approval": true
                          },
                          "ground_truth": {"root_cause_code": "SECRET"}
                        }
                        """)
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, broken.failure());
        assertEquals(
                ModelResponseFailureMetadata.Category.JSON_DESERIALIZATION,
                broken.safeMetadata().orElseThrow().category()
        );
        assertEquals(
                ModelResponseFailureMetadata.Reason.INVALID_JSON,
                broken.safeMetadata().orElseThrow().reason()
        );
        assertEquals(List.of(),
                broken.safeMetadata().orElseThrow().propertyPaths());
        assertNull(broken.getCause());
        assertEquals(
                ModelProviderFailure.MALFORMED_RESPONSE,
                leakedField.failure()
        );
        assertEquals(
                ModelResponseFailureMetadata.Reason.UNKNOWN_PROPERTY,
                leakedField.safeMetadata().orElseThrow().reason()
        );
    }

    @Test
    void rejectsAResponseThatDeserializesButViolatesTheDiagnosisContract() {
        ModelProviderException missingClaims = assertThrows(
                ModelProviderException.class,
                () -> decoder.decode("""
                        {
                          "status": "insufficient_evidence",
                          "root_cause_code": null,
                          "affected_service": null,
                          "business_summary": "Needs evidence.",
                          "technical_summary": "Needs evidence.",
                          "claims": null,
                          "safe_next_step": {
                            "summary": "Collect more evidence.",
                            "requires_human_approval": true
                          }
                        }
                        """)
        );
        ModelProviderException unsafeNextStep = assertThrows(
                ModelProviderException.class,
                () -> decoder.decode("""
                        {
                          "status": "insufficient_evidence",
                          "root_cause_code": null,
                          "affected_service": null,
                          "business_summary": "Needs evidence.",
                          "technical_summary": "Needs evidence.",
                          "claims": [],
                          "safe_next_step": {
                            "summary": "Change the system now.",
                            "requires_human_approval": false
                          }
                        }
                        """)
        );

        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, missingClaims.failure());
        assertEquals(ModelProviderFailure.MALFORMED_RESPONSE, unsafeNextStep.failure());
        assertEquals(
                ModelResponseFailureMetadata.Category.BEAN_VALIDATION,
                missingClaims.safeMetadata().orElseThrow().category()
        );
        assertEquals(
                ModelResponseFailureMetadata.Reason.CONSTRAINT_VIOLATION,
                missingClaims.safeMetadata().orElseThrow().reason()
        );
        assertEquals(
                List.of("$", "claims"),
                missingClaims.safeMetadata().orElseThrow().propertyPaths()
        );
        assertEquals(
                List.of("safeNextStep.requiresHumanApproval"),
                unsafeNextStep.safeMetadata().orElseThrow().propertyPaths()
        );
    }

    @Test
    void failureMetadataNeverRetainsRawModelValuesOrUnsafePaths() {
        String secret = "DO-NOT-LOG-secret-value";
        ModelProviderException exception = assertThrows(
                ModelProviderException.class,
                () -> decoder.decode("""
                        {
                          "status": "%s",
                          "root_cause_code": null,
                          "affected_service": null,
                          "business_summary": "Needs evidence.",
                          "technical_summary": "Needs evidence.",
                          "claims": [],
                          "safe_next_step": {
                            "summary": "Collect more evidence.",
                            "requires_human_approval": true
                          }
                        }
                        """.formatted(secret))
        );

        String retained = exception.getMessage()
                + exception.safeMetadata().orElseThrow();
        assertFalse(retained.contains(secret));
        assertNull(exception.getCause());
    }
}
