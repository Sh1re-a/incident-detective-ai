package dev.shirwac.incidentdetective.replay;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.verification.DeterministicVerifier;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public final class RecordedReplayService {

    public static final String TRUTH_LABEL =
            "Simulated incident — recorded deterministic replay.";

    private final RecordedScenarioRepository repository;
    private final DeterministicVerifier verifier;
    private final Clock clock;

    RecordedReplayService(
            RecordedScenarioRepository repository,
            Validator validator,
            Clock clock
    ) {
        this.repository = repository;
        this.verifier = new DeterministicVerifier(validator);
        this.clock = clock;
    }

    public RecordedReplayResult play(String scenarioId) {
        Instant startedAt = clock.instant();
        RecordedScenarioPackage scenarioPackage = repository.findById(scenarioId)
                .orElseThrow(() -> new ScenarioNotFoundException(scenarioId));

        Set<String> seenEvidenceIds = new HashSet<>();
        List<RecordedToolResult> toolResults = new ArrayList<>();
        for (RecordedToolEvent event : scenarioPackage.toolEvents()) {
            List<Evidence> returnedEvidence = event.evidenceIds().stream()
                    .map(scenarioPackage.evidenceById()::get)
                    .toList();
            returnedEvidence.stream()
                    .map(Evidence::evidenceId)
                    .forEach(seenEvidenceIds::add);
            toolResults.add(new RecordedToolResult(
                    event.eventId(),
                    event.toolName(),
                    event.safeSummary(),
                    returnedEvidence
            ));
        }

        VerificationReport verification = verifier.verify(
                scenarioPackage.recordedDiagnosis(),
                Set.copyOf(seenEvidenceIds),
                scenarioPackage.groundTruth()
        );
        DiagnosisCorrectness correctness = verification.diagnosisCorrectness();
        ReplayComparison comparison = new ReplayComparison(
                scenarioPackage.groundTruth().expectedStatus(),
                scenarioPackage.groundTruth().rootCauseCode(),
                scenarioPackage.groundTruth().affectedService(),
                correctness.rootCauseCorrect(),
                correctness.affectedServiceCorrect(),
                correctness.abstentionCorrect()
        );

        Instant completedAt = clock.instant();
        ReplayRunStatus status = verification.hardErrors().isEmpty()
                ? ReplayRunStatus.COMPLETED
                : ReplayRunStatus.VERIFICATION_FAILED;

        return new RecordedReplayResult(
                UUID.randomUUID().toString(),
                scenarioId,
                RunMode.RECORDED_REPLAY,
                TRUTH_LABEL,
                status,
                startedAt,
                completedAt,
                Math.max(0, Duration.between(startedAt, completedAt).toMillis()),
                scenarioPackage.scenario(),
                toolResults,
                scenarioPackage.recordedDiagnosis(),
                verification,
                comparison,
                null,
                null,
                null,
                null
        );
    }
}
