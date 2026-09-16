package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabPlanResponse;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;

import java.time.Instant;

record IncidentLabReplayFixture(
        String fixtureVersion,
        String replayId,
        String recordingSource,
        Instant recordedAt,
        String sourceContentGitSha,
        String runtimeBuildGitSha,
        boolean runtimeBuildIdentityVerified,
        String recordedInstruction,
        String recordedInstructionLocale,
        IncidentLabPlanResponse recordedPlan,
        IncidentLabRunResponse recordedRun
) {
    static final String FIXTURE_VERSION = "incident-lab-replay-fixture-v1";
    static final String CAPTURED_PUBLIC_API = "captured_public_api";
    static final String TEST_CONTRACT_FIXTURE = "test_contract_fixture";
}
