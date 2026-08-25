package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class GeminiDiagnosisDecoder {

    private final ObjectReader strictReader;
    private final Validator validator;

    public GeminiDiagnosisDecoder(JsonMapper jsonMapper, Validator validator) {
        strictReader = jsonMapper.readerFor(Diagnosis.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
    }

    public Diagnosis decode(String json) {
        if (json == null || json.isBlank()) {
            throw malformed(null);
        }
        try {
            Diagnosis diagnosis = strictReader.readValue(json);
            if (!validator.validate(diagnosis).isEmpty()) {
                throw malformed(null);
            }
            return diagnosis;
        } catch (ModelProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw malformed(exception);
        }
    }

    private ModelProviderException malformed(Throwable cause) {
        return new ModelProviderException(
                ModelProviderFailure.MALFORMED_RESPONSE,
                "Gemini returned a response that did not match Diagnosis",
                cause
        );
    }
}
