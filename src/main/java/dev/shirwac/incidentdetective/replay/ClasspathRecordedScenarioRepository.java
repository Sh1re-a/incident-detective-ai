package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.GroundTruth;
import dev.shirwac.incidentdetective.domain.groundtruth.RunbookReference;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
final class ClasspathRecordedScenarioRepository implements RecordedScenarioRepository {

    private static final String INDEX_RESOURCE = "fixtures/index.json";

    private final Map<String, RecordedScenarioPackage> scenariosById;
    private final List<RecordedScenarioPackage> scenarios;

    ClasspathRecordedScenarioRepository(JsonMapper jsonMapper, Validator validator) {
        Map<String, RecordedScenarioPackage> loaded = loadAll(jsonMapper, validator);
        scenariosById = Map.copyOf(loaded);
        scenarios = List.copyOf(loaded.values());
    }

    @Override
    public Optional<RecordedScenarioPackage> findById(String scenarioId) {
        return Optional.ofNullable(scenariosById.get(scenarioId));
    }

    @Override
    public List<RecordedScenarioPackage> findAll() {
        return scenarios;
    }

    private Map<String, RecordedScenarioPackage> loadAll(
            JsonMapper jsonMapper,
            Validator validator
    ) {
        ScenarioFixtureIndex index = readResource(
                jsonMapper,
                INDEX_RESOURCE,
                ScenarioFixtureIndex.class
        );
        requireValid(validator, index, INDEX_RESOURCE);

        Map<String, RecordedScenarioPackage> loaded = new LinkedHashMap<>();
        Set<String> globalEvidenceIds = new HashSet<>();
        for (ScenarioFixtureIndexEntry entry : index.scenarios()) {
            if (loaded.containsKey(entry.scenarioId())) {
                throw invalid(entry.scenarioId(), "duplicate scenario ID in fixture index");
            }

            RecordedScenarioFixture fixture = readResource(
                    jsonMapper,
                    entry.recordedResource(),
                    RecordedScenarioFixture.class
            );
            GroundTruth groundTruth = readResource(
                    jsonMapper,
                    entry.groundTruthResource(),
                    GroundTruth.class
            );
            requireValid(validator, fixture, entry.recordedResource());
            requireValid(validator, groundTruth, entry.groundTruthResource());

            RecordedScenarioPackage scenarioPackage = assemble(entry, fixture, groundTruth);
            for (String evidenceId : scenarioPackage.evidenceById().keySet()) {
                if (!globalEvidenceIds.add(evidenceId)) {
                    throw invalid(entry.scenarioId(), "duplicate global evidence ID " + evidenceId);
                }
            }
            loaded.put(entry.scenarioId(), scenarioPackage);
        }
        return loaded;
    }

    RecordedScenarioPackage assemble(
            ScenarioFixtureIndexEntry entry,
            RecordedScenarioFixture fixture,
            GroundTruth groundTruth
    ) {
        String scenarioId = entry.scenarioId();
        if (!scenarioId.equals(fixture.scenario().scenarioId())
                || !scenarioId.equals(groundTruth.scenarioId())) {
            throw invalid(scenarioId, "scenario IDs do not match the fixture index");
        }

        Map<String, Evidence> evidenceById = new LinkedHashMap<>();
        for (Evidence evidence : fixture.evidenceInventory()) {
            if (!scenarioId.equals(evidence.scenarioId())) {
                throw invalid(scenarioId, "evidence belongs to another scenario");
            }
            if (evidenceById.putIfAbsent(evidence.evidenceId(), evidence) != null) {
                throw invalid(scenarioId, "duplicate evidence ID " + evidence.evidenceId());
            }
        }

        Set<String> seenEvidenceIds = validateToolEvents(
                scenarioId,
                fixture.toolEvents(),
                evidenceById
        );
        Set<String> citedEvidenceIds = fixture.recordedDiagnosis().claims().stream()
                .map(Claim::evidenceIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        if (!seenEvidenceIds.containsAll(citedEvidenceIds)) {
            Set<String> unseenCitations = new HashSet<>(citedEvidenceIds);
            unseenCitations.removeAll(seenEvidenceIds);
            throw invalid(scenarioId, "diagnosis cites unseen evidence " + unseenCitations);
        }

        Set<String> supportedEvidenceIds = groundTruth.claimSupport().stream()
                .map(ClaimSupport::allowedEvidenceIds)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        if (!evidenceById.keySet().containsAll(supportedEvidenceIds)) {
            throw invalid(scenarioId, "ground truth references missing evidence");
        }
        validateRunbooks(scenarioId, groundTruth, evidenceById.values().stream().toList());

        return new RecordedScenarioPackage(
                fixture.scenario(),
                evidenceById,
                fixture.toolEvents(),
                fixture.recordedDiagnosis(),
                groundTruth
        );
    }

    private Set<String> validateToolEvents(
            String scenarioId,
            List<RecordedToolEvent> toolEvents,
            Map<String, Evidence> evidenceById
    ) {
        Set<String> eventIds = new HashSet<>();
        Set<String> seenEvidenceIds = new HashSet<>();
        for (RecordedToolEvent event : toolEvents) {
            if (!eventIds.add(event.eventId())) {
                throw invalid(scenarioId, "duplicate tool event ID " + event.eventId());
            }
            for (String evidenceId : event.evidenceIds()) {
                if (!evidenceById.containsKey(evidenceId)) {
                    throw invalid(scenarioId, "tool event references missing evidence " + evidenceId);
                }
                seenEvidenceIds.add(evidenceId);
            }
        }
        return Set.copyOf(seenEvidenceIds);
    }

    private void validateRunbooks(
            String scenarioId,
            GroundTruth groundTruth,
            List<Evidence> evidenceInventory
    ) {
        List<RunbookEvidence.RunbookContent> runbooks = evidenceInventory.stream()
                .filter(RunbookEvidence.class::isInstance)
                .map(RunbookEvidence.class::cast)
                .map(RunbookEvidence::content)
                .toList();

        for (RunbookReference reference : groundTruth.relevantRunbooks()) {
            boolean found = runbooks.stream().anyMatch(runbook ->
                    runbook.documentId().equals(reference.documentId())
                            && runbook.chunkId().equals(reference.chunkId())
                            && runbook.documentVersion().equals(reference.documentVersion())
            );
            if (!found) {
                throw invalid(scenarioId, "ground truth references a missing runbook chunk");
            }
        }
    }

    private <T> T readResource(JsonMapper jsonMapper, String path, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.isReadable()) {
            throw new IllegalStateException("Fixture resource is not readable: " + path);
        }

        try (InputStream input = resource.getInputStream()) {
            return jsonMapper.readValue(input, type);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read fixture resource: " + path, exception);
        }
    }

    private void requireValid(Validator validator, Object value, String resource) {
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid fixture " + resource + ": " + details);
        }
    }

    private IllegalStateException invalid(String scenarioId, String message) {
        return new IllegalStateException("Invalid fixture " + scenarioId + ": " + message);
    }
}
