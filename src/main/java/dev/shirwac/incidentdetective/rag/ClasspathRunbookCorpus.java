package dev.shirwac.incidentdetective.rag;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public final class ClasspathRunbookCorpus {

    static final String RESOURCE = "runbooks/runbook-corpus-v1.json";
    static final int MIN_DOCUMENTS = 10;
    static final int MAX_DOCUMENTS = 15;

    private final RunbookCorpus corpus;

    public ClasspathRunbookCorpus(JsonMapper jsonMapper, Validator validator) {
        corpus = load(jsonMapper, validator);
    }

    public String version() {
        return corpus.corpusVersion();
    }

    public List<RunbookCorpusEntry> entries() {
        return corpus.entries();
    }

    public List<String> documentIds() {
        return corpus.entries().stream()
                .map(RunbookCorpusEntry::documentId)
                .distinct()
                .sorted()
                .toList();
    }

    private RunbookCorpus load(JsonMapper jsonMapper, Validator validator) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.isReadable()) {
            throw new IllegalStateException("Runbook corpus is not readable: " + RESOURCE);
        }

        RunbookCorpus loaded;
        try (InputStream input = resource.getInputStream()) {
            loaded = jsonMapper.readValue(input, RunbookCorpus.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read runbook corpus: " + RESOURCE, exception);
        }

        Set<ConstraintViolation<RunbookCorpus>> violations = validator.validate(loaded);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw invalid(details);
        }

        validateIdentities(loaded.entries());
        return loaded;
    }

    private void validateIdentities(List<RunbookCorpusEntry> entries) {
        Set<String> evidenceIds = new HashSet<>();
        Set<String> sourceRefs = new HashSet<>();
        Set<ChunkIdentity> chunks = new HashSet<>();
        Map<String, DocumentIdentity> documents = new HashMap<>();

        for (RunbookCorpusEntry entry : entries) {
            if (!evidenceIds.add(entry.evidenceId())) {
                throw invalid("duplicate evidence ID " + entry.evidenceId());
            }
            if (!sourceRefs.add(entry.sourceRef())) {
                throw invalid("duplicate source ref " + entry.sourceRef());
            }
            ChunkIdentity chunk = new ChunkIdentity(
                    entry.documentId(),
                    entry.documentVersion(),
                    entry.chunkId()
            );
            if (!chunks.add(chunk)) {
                throw invalid("duplicate document version chunk " + chunk);
            }
            String expectedSourceRef = "runbooks/" + entry.documentId()
                    + "#" + entry.chunkId();
            if (!expectedSourceRef.equals(entry.sourceRef())) {
                throw invalid("source ref does not match document and chunk");
            }

            DocumentIdentity identity = new DocumentIdentity(
                    entry.documentVersion(),
                    entry.title()
            );
            DocumentIdentity previous = documents.putIfAbsent(
                    entry.documentId(),
                    identity
            );
            if (previous != null && !previous.equals(identity)) {
                throw invalid("document chunks disagree on title or version");
            }
        }

        if (documents.size() < MIN_DOCUMENTS || documents.size() > MAX_DOCUMENTS) {
            throw invalid("expected 10 to 15 distinct runbook documents");
        }
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("Invalid runbook corpus " + RESOURCE + ": " + message);
    }

    private record ChunkIdentity(
            String documentId,
            String documentVersion,
            String chunkId
    ) {
    }

    private record DocumentIdentity(String documentVersion, String title) {
    }
}
