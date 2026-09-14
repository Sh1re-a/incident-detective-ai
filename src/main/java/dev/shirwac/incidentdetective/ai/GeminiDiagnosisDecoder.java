package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

@Component
public final class GeminiDiagnosisDecoder {

    private static final Set<String> DIAGNOSIS_JSON_PROPERTIES = Set.of(
            "status",
            "root_cause_code",
            "affected_service",
            "business_summary",
            "technical_summary",
            "claims",
            "claim_code",
            "claim_value_code",
            "display_text",
            "evidence_ids",
            "safe_next_step",
            "summary",
            "requires_human_approval"
    );

    private final ObjectReader strictReader;
    private final Validator validator;

    public GeminiDiagnosisDecoder(JsonMapper jsonMapper, Validator validator) {
        strictReader = jsonMapper.readerFor(Diagnosis.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public Diagnosis decode(String json) {
        if (json == null || json.isBlank()) {
            throw malformed(new ModelResponseFailureMetadata(
                    ModelResponseFailureMetadata.Category.JSON_DESERIALIZATION,
                    ModelResponseFailureMetadata.Reason.EMPTY_RESPONSE,
                    List.of()
            ));
        }

        Diagnosis diagnosis;
        try {
            diagnosis = strictReader.readValue(json);
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw malformed(deserializationMetadata(exception));
        }

        Set<ConstraintViolation<Diagnosis>> violations =
                validator.validate(diagnosis);
        if (!violations.isEmpty()) {
            throw malformed(new ModelResponseFailureMetadata(
                    ModelResponseFailureMetadata.Category.BEAN_VALIDATION,
                    ModelResponseFailureMetadata.Reason.CONSTRAINT_VIOLATION,
                    validationPaths(violations)
            ));
        }
        return diagnosis;
    }

    private ModelResponseFailureMetadata deserializationMetadata(
            Exception exception
    ) {
        ModelResponseFailureMetadata.Reason reason =
                exception instanceof UnrecognizedPropertyException
                        ? ModelResponseFailureMetadata.Reason.UNKNOWN_PROPERTY
                        : exception instanceof StreamReadException
                                ? ModelResponseFailureMetadata.Reason.INVALID_JSON
                                : ModelResponseFailureMetadata.Reason.VALUE_DESERIALIZATION;
        return new ModelResponseFailureMetadata(
                ModelResponseFailureMetadata.Category.JSON_DESERIALIZATION,
                reason,
                jacksonPaths(exception)
        );
    }

    private List<String> jacksonPaths(Exception exception) {
        if (!(exception instanceof JacksonException jacksonException)) {
            return List.of();
        }
        List<String> segments = jacksonException.getPath().stream()
                .map(reference -> reference.getPropertyName() != null
                        ? reference.getPropertyName()
                        : reference.getIndex() >= 0
                                ? "[" + reference.getIndex() + "]"
                                : "")
                .filter(segment -> !segment.isBlank())
                .toList();
        if (segments.stream()
                .filter(segment -> !segment.startsWith("["))
                .anyMatch(segment -> !DIAGNOSIS_JSON_PROPERTIES.contains(segment))) {
            return List.of();
        }
        String path = segments.stream().reduce(
                "",
                (current, segment) -> segment.startsWith("[")
                        ? current + segment
                        : current.isEmpty() ? segment : current + "." + segment
        );
        return path.isBlank() ? List.of() : List.of(path);
    }

    private List<String> validationPaths(
            Set<ConstraintViolation<Diagnosis>> violations
    ) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .map(path -> path.isBlank() ? "$" : path)
                .toList();
    }

    private ModelProviderException malformed(
            ModelResponseFailureMetadata safeMetadata
    ) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                "Gemini returned a response that did not match Diagnosis",
                safeMetadata
        );
    }
}
