package dev.shirwac.incidentdetective.proof;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
@Service
public final class ProofEvalSummaryService {

    static final String RETRIEVAL_RESOURCE =
            "proof/evals/retrieval-summary-v1.json";
    private static final String RETRIEVAL_VERSION =
            "retrieval-proof-summary-v1";

    private final RetrievalEvalProofResponse retrieval;

    public ProofEvalSummaryService(JsonMapper jsonMapper) {
        retrieval = read(
                jsonMapper,
                RETRIEVAL_RESOURCE,
                RetrievalEvalProofResponse.class
        );
        validateRetrieval(retrieval);
    }

    public RetrievalEvalProofResponse retrieval() {
        return retrieval;
    }

    private static <T> T read(
            JsonMapper jsonMapper,
            String resourcePath,
            Class<T> type
    ) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(input);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load published proof summary " + resourcePath,
                    exception
            );
        }
    }

    private static void validateRetrieval(RetrievalEvalProofResponse summary) {
        requireEqual(
                RETRIEVAL_VERSION,
                summary.summaryVersion(),
                "retrieval summary version"
        );
        requireEqual("measured", summary.status(), "retrieval status");
        if (summary.provenance() == null
                || summary.retrieval() == null
                || summary.development() == null
                || summary.heldOut() == null
                || summary.providerUsage() == null
                || summary.safetyBoundary() == null) {
            throw invalid("retrieval summary sections must be present");
        }
        if (!summary.provenance().historicalFrozenRun()) {
            throw invalid("retrieval proof must be marked as a historical frozen run");
        }
        requireEqual(
                "development",
                summary.development().split(),
                "retrieval development split"
        );
        requireEqual(
                "held_out",
                summary.heldOut().split(),
                "retrieval held-out split"
        );
        if (summary.retrieval().embeddingDimensions() < 1
                || summary.retrieval().corpusDocumentCount() < 1
                || summary.retrieval().corpusChunkCount() < 1
                || summary.retrieval().topK() < 1) {
            throw invalid("retrieval configuration counts must be positive");
        }
        if (summary.providerUsage().providerUsageMetadataComplete()
                && (summary.providerUsage().providerBillableCharacters() == null
                || summary.providerUsage().providerInputTokens() == null)) {
            throw invalid(
                    "complete retrieval usage requires provider usage values"
            );
        }
    }

    private static void requireEqual(
            String expected,
            String actual,
            String field
    ) {
        if (!expected.equals(actual)) {
            throw invalid(field + " is unsupported");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid published proof summary: " + message);
    }
}
