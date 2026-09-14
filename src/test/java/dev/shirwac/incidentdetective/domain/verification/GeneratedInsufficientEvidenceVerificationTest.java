package dev.shirwac.incidentdetective.domain.verification;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.groundtruth.ClaimSupport;
import dev.shirwac.incidentdetective.domain.groundtruth.ExpectedClaim;
import dev.shirwac.incidentdetective.generated.GeneratedCase;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRequest;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.generated.NordlyIncidentGeneratedCaseGenerator;
import dev.shirwac.incidentdetective.generated.PaymentTimeoutGeneratedCaseGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedInsufficientEvidenceVerificationTest {

    private static final Map<GeneratedIncidentFamily, ExpectedDiagnosis> EXPECTED = Map.of(
            GeneratedIncidentFamily.PAYMENT_TIMEOUT,
            new ExpectedDiagnosis("PAYMENT_TIMEOUT_CONFIG", "PAYMENT_ADAPTER"),
            GeneratedIncidentFamily.CATALOG_CACHE_INVALIDATION,
            new ExpectedDiagnosis(
                    "CATALOG_CACHE_INVALIDATION_FAILURE",
                    "CATALOG_SERVICE"
            ),
            GeneratedIncidentFamily.ORDER_EVENT_BACKLOG,
            new ExpectedDiagnosis(
                    "ORDER_EVENT_CONSUMER_BACKLOG",
                    "ORDER_EVENT_CONSUMER"
            ),
            GeneratedIncidentFamily.ORDER_IDEMPOTENCY_FAILURE,
            new ExpectedDiagnosis(
                    "ORDER_IDEMPOTENCY_FAILURE",
                    "ORDER_SERVICE"
            )
    );

    private final DeterministicVerifier verifier = new DeterministicVerifier();

    @ParameterizedTest
    @EnumSource(GeneratedIncidentFamily.class)
    void acceptsDirectlySupportedAbstentionForEveryGeneratedFamily(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = insufficientCase(family);
        Diagnosis abstention = honestAbstention(generated);

        VerificationReport report = verifier.verify(
                abstention,
                seenEvidenceIds(generated),
                generated.hiddenGroundTruth()
        );

        assertTrue(report.hardErrors().isEmpty());
        assertTrue(report.citationValidity().valid());
        assertEquals(1.0, report.evidencePrecision().score());
        assertEquals(1.0, report.claimCoverage().score());
        assertTrue(report.diagnosisCorrectness().abstentionCorrect());
    }

    @ParameterizedTest
    @EnumSource(GeneratedIncidentFamily.class)
    void rejectsDiagnosisInferredFromChangeAndSymptomForEveryGeneratedFamily(
            GeneratedIncidentFamily family
    ) {
        GeneratedCase generated = insufficientCase(family);
        ExpectedDiagnosis expected = EXPECTED.get(family);
        String changeEvidence = changeEvidenceId(generated, family);
        String symptomEvidence = support(generated, ClaimCode.OBSERVED_SYMPTOM)
                .allowedEvidenceIds()
                .getFirst();
        Diagnosis inferred = new Diagnosis(
                DiagnosisStatus.DIAGNOSED,
                expected.rootCause(),
                expected.affectedService(),
                "A change happened before the visible symptom.",
                "Chronology alone must not be released as a diagnosis.",
                List.of(
                        claim(
                                ClaimCode.ROOT_CAUSE,
                                expected.rootCause(),
                                changeEvidence
                        ),
                        claim(
                                ClaimCode.AFFECTED_SERVICE,
                                expected.affectedService(),
                                symptomEvidence
                        )
                ),
                safeNextStep()
        );

        VerificationReport report = verifier.verify(
                inferred,
                seenEvidenceIds(generated),
                generated.hiddenGroundTruth()
        );

        assertTrue(report.hardErrors().isEmpty());
        assertTrue(report.citationValidity().valid());
        assertEquals(0.0, report.evidencePrecision().score());
        assertFalse(report.diagnosisCorrectness().abstentionCorrect());
    }

    private GeneratedCase insufficientCase(GeneratedIncidentFamily family) {
        GeneratedCaseRequest request = new GeneratedCaseRequest(
                42L,
                family,
                GeneratedEvidenceMode.INSUFFICIENT_EVIDENCE,
                GeneratedNoiseLevel.LOW
        );
        return family == GeneratedIncidentFamily.PAYMENT_TIMEOUT
                ? new PaymentTimeoutGeneratedCaseGenerator().generate(request)
                : new NordlyIncidentGeneratedCaseGenerator().generate(request);
    }

    private Diagnosis honestAbstention(GeneratedCase generated) {
        List<Claim> claims = generated.hiddenGroundTruth()
                .expectedClaims()
                .stream()
                .map(expected -> claim(
                        expected.claimCode(),
                        expected.claimValueCode(),
                        support(generated, expected).allowedEvidenceIds().getFirst()
                ))
                .toList();
        return new Diagnosis(
                DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                null,
                null,
                "The symptom is visible, but the cause is not proven.",
                "The causal record is missing from the bounded evidence window.",
                claims,
                safeNextStep()
        );
    }

    private ClaimSupport support(
            GeneratedCase generated,
            ExpectedClaim expected
    ) {
        return generated.hiddenGroundTruth().claimSupport().stream()
                .filter(candidate -> candidate.claimCode() == expected.claimCode())
                .filter(candidate -> candidate.claimValueCode()
                        .equals(expected.claimValueCode()))
                .findFirst()
                .orElseThrow();
    }

    private ClaimSupport support(
            GeneratedCase generated,
            ClaimCode claimCode
    ) {
        return generated.hiddenGroundTruth().claimSupport().stream()
                .filter(candidate -> candidate.claimCode() == claimCode)
                .findFirst()
                .orElseThrow();
    }

    private String changeEvidenceId(
            GeneratedCase generated,
            GeneratedIncidentFamily family
    ) {
        String suffix = family == GeneratedIncidentFamily.PAYMENT_TIMEOUT
                ? "-log-release"
                : "-log-change-event";
        return generated.investigationData().evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .filter(id -> id.endsWith(suffix))
                .findFirst()
                .orElseThrow();
    }

    private Set<String> seenEvidenceIds(GeneratedCase generated) {
        return generated.investigationData().evidenceInventory().stream()
                .map(Evidence::evidenceId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Claim claim(
            ClaimCode claimCode,
            String claimValueCode,
            String evidenceId
    ) {
        return new Claim(
                claimCode,
                claimValueCode,
                claimValueCode,
                List.of(evidenceId)
        );
    }

    private SafeNextStep safeNextStep() {
        return new SafeNextStep(
                "Ask a human to collect the missing causal evidence.",
                true
        );
    }

    private record ExpectedDiagnosis(
            String rootCause,
            String affectedService
    ) {
    }
}
