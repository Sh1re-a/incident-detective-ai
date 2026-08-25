package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class GeminiDiagnosisDecoder {

    private final ObjectReader strictReader;

    public GeminiDiagnosisDecoder(JsonMapper jsonMapper) {
        strictReader = jsonMapper.readerFor(Diagnosis.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public Diagnosis decode(String json) {
        if (json == null || json.isBlank()) {
            throw malformed(null);
        }
        try {
            return strictReader.readValue(json);
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
