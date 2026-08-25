package dev.shirwac.incidentdetective.domain.diagnosis;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DiagnosisValidator implements ConstraintValidator<ValidDiagnosis, Diagnosis> {

    @Override
    public boolean isValid(Diagnosis diagnosis, ConstraintValidatorContext context) {
        if (diagnosis == null || diagnosis.status() == null) {
            return true;
        }

        return switch (diagnosis.status()) {
            case DIAGNOSED -> isValidDiagnosed(diagnosis);
            case INSUFFICIENT_EVIDENCE -> isValidInsufficientEvidence(diagnosis);
        };
    }

    private boolean isValidDiagnosed(Diagnosis diagnosis) {
        if (isBlank(diagnosis.rootCauseCode()) || isBlank(diagnosis.affectedService())) {
            return false;
        }

        List<Claim> claims = diagnosis.claims();
        if (claims == null || claims.stream().anyMatch(Objects::isNull)) {
            return false;
        }

        return hasExactlyOneMatchingClaim(
                claims,
                ClaimCode.ROOT_CAUSE,
                diagnosis.rootCauseCode()
        ) && hasExactlyOneMatchingClaim(
                claims,
                ClaimCode.AFFECTED_SERVICE,
                diagnosis.affectedService()
        ) && claims.stream().allMatch(this::hasEvidence)
                && hasUniqueClaimKeys(claims);
    }

    private boolean isValidInsufficientEvidence(Diagnosis diagnosis) {
        if (diagnosis.rootCauseCode() != null || diagnosis.affectedService() != null) {
            return false;
        }

        if (diagnosis.claims() == null || diagnosis.claims().stream().anyMatch(Objects::isNull)) {
            return false;
        }

        Set<ClaimCode> allowedClaims = Set.of(
                ClaimCode.OBSERVED_SYMPTOM,
                ClaimCode.MISSING_EVIDENCE
        );

        return diagnosis.claims().stream()
                .allMatch(claim -> allowedClaims.contains(claim.claimCode()));
    }

    private boolean hasExactlyOneMatchingClaim(
            List<Claim> claims,
            ClaimCode claimCode,
            String expectedValue
    ) {
        List<Claim> claimsWithCode = claims.stream()
                .filter(claim -> claim.claimCode() == claimCode)
                .toList();

        return claimsWithCode.size() == 1
                && Objects.equals(claimsWithCode.getFirst().claimValueCode(), expectedValue);
    }

    private boolean hasEvidence(Claim claim) {
        return claim.evidenceIds() != null && !claim.evidenceIds().isEmpty();
    }

    private boolean hasUniqueClaimKeys(List<Claim> claims) {
        Set<String> claimKeys = new HashSet<>();
        return claims.stream()
                .allMatch(claim -> claimKeys.add(claim.claimCode() + "\u0000" + claim.claimValueCode()));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
