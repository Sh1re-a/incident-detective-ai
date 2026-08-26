package dev.shirwac.incidentdetective.rag.eval;

import dev.shirwac.incidentdetective.rag.ClasspathRunbookCorpus;
import dev.shirwac.incidentdetective.rag.RagProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public final class ClasspathRunbookRetrievalEvalSuite {

    static final String RESOURCE = "evals/runbook-retrieval-eval-v1.json";
    static final String DEVELOPMENT = "development";
    static final String HELD_OUT = "held_out";
    static final String POSITIVE = "positive";
    static final String NO_MATCH = "no_match";
    static final String ADVERSARIAL = "adversarial";

    private final RunbookRetrievalEvalSuite suite;
    private final String resourceSha256;

    public ClasspathRunbookRetrievalEvalSuite(
            JsonMapper jsonMapper,
            Validator validator,
            ClasspathRunbookCorpus corpus,
            RagProperties properties
    ) {
        byte[] bytes = readResource();
        suite = parse(bytes, jsonMapper);
        resourceSha256 = sha256(bytes);
        validate(suite, validator, corpus, properties);
    }

    public RunbookRetrievalEvalSuite suite() {
        return suite;
    }

    public String resourceSha256() {
        return resourceSha256;
    }

    private byte[] readResource() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.isReadable()) {
            throw invalid("resource is not readable");
        }
        try {
            return resource.getContentAsByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + RESOURCE, exception);
        }
    }

    private RunbookRetrievalEvalSuite parse(byte[] bytes, JsonMapper jsonMapper) {
        return jsonMapper.readValue(bytes, RunbookRetrievalEvalSuite.class);
    }

    private void validate(
            RunbookRetrievalEvalSuite loaded,
            Validator validator,
            ClasspathRunbookCorpus corpus,
            RagProperties properties
    ) {
        Set<ConstraintViolation<RunbookRetrievalEvalSuite>> violations =
                validator.validate(loaded);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw invalid(details);
        }

        if (!"runbook-retrieval-eval-v1".equals(loaded.suiteVersion())) {
            throw invalid("unexpected suite version");
        }
        if (!loaded.corpusVersion().equals(corpus.version())) {
            throw invalid("corpus version does not match the loaded corpus");
        }
        var contract = loaded.retrievalContract();
        if (!contract.model().equals(properties.embeddingModel())
                || contract.dimensions() != properties.embeddingDimensions()
                || !contract.embeddingFormatVersion().equals(
                        properties.embeddingFormatVersion()
                )) {
            throw invalid("embedding contract does not match the active profile");
        }
        if (!"cosine".equals(contract.distance())
                || contract.topK() < 1
                || contract.topK() > 20) {
            throw invalid("retrieval distance or top_k is unsupported");
        }

        Set<String> corpusEvidenceIds = corpus.entries().stream()
                .map(entry -> entry.evidenceId())
                .collect(Collectors.toSet());
        Set<String> caseIds = new HashSet<>();
        Set<String> queries = new HashSet<>();
        for (RunbookRetrievalEvalSuite.EvalCase evalCase : loaded.cases()) {
            if (!caseIds.add(evalCase.caseId())) {
                throw invalid("duplicate case ID " + evalCase.caseId());
            }
            if (!queries.add(evalCase.query())) {
                throw invalid("duplicate query in case " + evalCase.caseId());
            }
            if (!Set.of(DEVELOPMENT, HELD_OUT).contains(evalCase.split())) {
                throw invalid("unknown split " + evalCase.split());
            }
            if (!Set.of(POSITIVE, NO_MATCH, ADVERSARIAL).contains(
                    evalCase.caseType()
            )) {
                throw invalid("unknown case type " + evalCase.caseType());
            }
            if (!corpusEvidenceIds.containsAll(evalCase.relevantEvidenceIds())) {
                throw invalid("case references evidence outside the corpus");
            }
            if (NO_MATCH.equals(evalCase.caseType())) {
                if (!evalCase.expectedEmpty()
                        || !evalCase.relevantEvidenceIds().isEmpty()) {
                    throw invalid("no-match case must expect an empty result");
                }
            } else if (evalCase.expectedEmpty()
                    || evalCase.relevantEvidenceIds().isEmpty()) {
                throw invalid("positive and adversarial cases need relevant evidence");
            }
            if (ADVERSARIAL.equals(evalCase.caseType())
                    && (evalCase.safetyFollowUp() == null
                    || evalCase.safetyFollowUp().isBlank())) {
                throw invalid("adversarial case needs an explicit safety follow-up");
            }
        }

        requireCoverage(loaded.cases(), DEVELOPMENT, POSITIVE);
        requireCoverage(loaded.cases(), DEVELOPMENT, NO_MATCH);
        requireCoverage(loaded.cases(), HELD_OUT, POSITIVE);
        requireCoverage(loaded.cases(), HELD_OUT, NO_MATCH);
        requireCoverage(loaded.cases(), HELD_OUT, ADVERSARIAL);
    }

    private void requireCoverage(
            List<RunbookRetrievalEvalSuite.EvalCase> cases,
            String split,
            String type
    ) {
        boolean present = cases.stream().anyMatch(evalCase ->
                split.equals(evalCase.split()) && type.equals(evalCase.caseType())
        );
        if (!present) {
            throw invalid("missing " + split + " " + type + " coverage");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException(
                "Invalid runbook retrieval eval " + RESOURCE + ": " + message
        );
    }
}
