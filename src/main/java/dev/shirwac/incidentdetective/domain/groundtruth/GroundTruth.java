package dev.shirwac.incidentdetective.domain.groundtruth;

import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

@ValidGroundTruth
public record GroundTruth(
        @NotBlank String scenarioId,
        @NotNull DiagnosisStatus expectedStatus,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String rootCauseCode,
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
        String affectedService,
        @NotNull List<@NotNull @Valid ExpectedClaim> expectedClaims,
        @NotNull List<@NotNull @Valid ClaimSupport> claimSupport,
        @NotNull List<@NotNull @Valid RunbookReference> relevantRunbooks
) {
    public GroundTruth {
        expectedClaims = expectedClaims == null ? null : List.copyOf(expectedClaims);
        claimSupport = claimSupport == null ? null : List.copyOf(claimSupport);
        relevantRunbooks = relevantRunbooks == null ? null : List.copyOf(relevantRunbooks);
    }
}
