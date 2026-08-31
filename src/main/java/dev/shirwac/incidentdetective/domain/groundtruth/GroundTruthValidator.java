package dev.shirwac.incidentdetective.domain.groundtruth;

import dev.shirwac.incidentdetective.domain.diagnosis.ClaimCode;
import dev.shirwac.incidentdetective.domain.diagnosis.ClaimValueTaxonomy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GroundTruthValidator
        implements ConstraintValidator<ValidGroundTruth, GroundTruth> {

    @Override
    public boolean isValid(GroundTruth groundTruth, ConstraintValidatorContext context) {
        if (groundTruth == null || groundTruth.expectedStatus() == null) {
            return true;
        }

        List<ExpectedClaim> expectedClaims = groundTruth.expectedClaims();
        List<ClaimSupport> claimSupport = groundTruth.claimSupport();
        List<RunbookReference> relevantRunbooks = groundTruth.relevantRunbooks();

        if (containsNull(expectedClaims)
                || containsNull(claimSupport)
                || containsNull(relevantRunbooks)) {
            return false;
        }

        if (!hasUniqueExpectedClaimKeys(expectedClaims)
                || !hasUniqueSupportKeys(claimSupport)
                || !hasCanonicalClaimValues(expectedClaims, claimSupport)
                || !hasMatchingClaimAndSupportKeys(expectedClaims, claimSupport)
                || !hasUniqueEvidenceIdsPerSupport(claimSupport)
                || !hasUniqueRunbookReferences(relevantRunbooks)) {
            return false;
        }

        return switch (groundTruth.expectedStatus()) {
            case DIAGNOSED -> isValidDiagnosed(groundTruth, expectedClaims);
            case INSUFFICIENT_EVIDENCE -> isValidInsufficientEvidence(
                    groundTruth,
                    expectedClaims
            );
        };
    }

    private boolean isValidDiagnosed(
            GroundTruth groundTruth,
            List<ExpectedClaim> expectedClaims
    ) {
        if (isBlank(groundTruth.rootCauseCode()) || isBlank(groundTruth.affectedService())) {
            return false;
        }

        return hasExactlyOneMatchingClaim(
                expectedClaims,
                ClaimCode.ROOT_CAUSE,
                groundTruth.rootCauseCode()
        ) && hasExactlyOneMatchingClaim(
                expectedClaims,
                ClaimCode.AFFECTED_SERVICE,
                groundTruth.affectedService()
        );
    }

    private boolean isValidInsufficientEvidence(
            GroundTruth groundTruth,
            List<ExpectedClaim> expectedClaims
    ) {
        if (groundTruth.rootCauseCode() != null || groundTruth.affectedService() != null) {
            return false;
        }

        return expectedClaims.stream()
                .allMatch(claim -> claim.claimCode() != null
                        && claim.claimCode().allowedForInsufficientEvidence());
    }

    private boolean hasExactlyOneMatchingClaim(
            List<ExpectedClaim> expectedClaims,
            ClaimCode claimCode,
            String expectedValue
    ) {
        List<ExpectedClaim> claimsWithCode = expectedClaims.stream()
                .filter(claim -> claim.claimCode() == claimCode)
                .toList();

        return claimsWithCode.size() == 1
                && Objects.equals(claimsWithCode.getFirst().claimValueCode(), expectedValue);
    }

    private boolean hasUniqueExpectedClaimKeys(List<ExpectedClaim> expectedClaims) {
        Set<ClaimKey> keys = new HashSet<>();
        return expectedClaims.stream()
                .map(this::claimKey)
                .allMatch(keys::add);
    }

    private boolean hasUniqueSupportKeys(List<ClaimSupport> claimSupport) {
        Set<ClaimKey> keys = new HashSet<>();
        return claimSupport.stream()
                .map(this::claimKey)
                .allMatch(keys::add);
    }

    private boolean hasMatchingClaimAndSupportKeys(
            List<ExpectedClaim> expectedClaims,
            List<ClaimSupport> claimSupport
    ) {
        Set<ClaimKey> expectedKeys = new HashSet<>(
                expectedClaims.stream().map(this::claimKey).toList()
        );
        Set<ClaimKey> supportKeys = new HashSet<>(
                claimSupport.stream().map(this::claimKey).toList()
        );
        return expectedKeys.equals(supportKeys);
    }

    private boolean hasCanonicalClaimValues(
            List<ExpectedClaim> expectedClaims,
            List<ClaimSupport> claimSupport
    ) {
        return expectedClaims.stream().allMatch(claim -> ClaimValueTaxonomy.contains(
                claim.claimCode(),
                claim.claimValueCode()
        )) && claimSupport.stream().allMatch(support -> ClaimValueTaxonomy.contains(
                support.claimCode(),
                support.claimValueCode()
        ));
    }

    private boolean hasUniqueEvidenceIdsPerSupport(List<ClaimSupport> claimSupport) {
        return claimSupport.stream().allMatch(support -> {
            List<String> evidenceIds = support.allowedEvidenceIds();
            return evidenceIds != null
                    && new HashSet<>(evidenceIds).size() == evidenceIds.size();
        });
    }

    private boolean hasUniqueRunbookReferences(List<RunbookReference> relevantRunbooks) {
        Set<String> keys = new HashSet<>();
        return relevantRunbooks.stream()
                .map(reference -> reference.documentId() + "\u0000" + reference.chunkId())
                .allMatch(keys::add);
    }

    private ClaimKey claimKey(ExpectedClaim claim) {
        return new ClaimKey(claim.claimCode(), claim.claimValueCode());
    }

    private ClaimKey claimKey(ClaimSupport support) {
        return new ClaimKey(support.claimCode(), support.claimValueCode());
    }

    private boolean containsNull(List<?> values) {
        return values == null || values.stream().anyMatch(Objects::isNull);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ClaimKey(ClaimCode claimCode, String claimValueCode) {
    }
}
