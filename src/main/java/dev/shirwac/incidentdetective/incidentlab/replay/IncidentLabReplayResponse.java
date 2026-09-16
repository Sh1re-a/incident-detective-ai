package dev.shirwac.incidentdetective.incidentlab.replay;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabPlanResponse;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.replay.RunMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record IncidentLabReplayResponse(
        String contractVersion,
        String replayId,
        String playbackId,
        RunMode mode,
        String delivery,
        String truthLabel,
        String truthLabelEn,
        Instant servedAt,
        String recordedInstruction,
        String recordedInstructionLocale,
        IncidentLabPlanResponse recordedPlan,
        IncidentLabRunResponse recordedRun,
        Provenance provenance,
        PlaybackReceipt playbackReceipt,
        List<String> limitations
) {
    public static final String CONTRACT_VERSION = "incident-lab-replay-v1";
    public static final String DELIVERY = "synchronous_recorded_playback";
    public static final String TRUTH_LABEL =
            "Förinspelad syntetisk körning · ingen modell, ADK-agent, embedding eller vectorsökning körs nu.";
    public static final String TRUTH_LABEL_EN =
            "Recorded synthetic run · no model, ADK agent, embedding, or vector search is running now.";

    public IncidentLabReplayResponse {
        limitations = limitations == null ? List.of() : List.copyOf(limitations);
    }

    public record Provenance(
            String fixtureVersion,
            String recordingSource,
            Instant recordedAt,
            String sourceBuildGitSha,
            String resourceSha256,
            boolean resourceSha256VerifiedAtStartup,
            String originalPlanContractVersion,
            String originalRunContractVersion
    ) {
    }

    public record PlaybackReceipt(
            int providerCalls,
            int modelCalls,
            int adkToolCalls,
            int readOperations,
            int embeddingCalls,
            boolean vectorSearchExecuted,
            boolean liveQuotaConsumed,
            boolean writeToolsAvailable,
            boolean actionExecuted,
            @Schema(nullable = true)
            BigDecimal estimatedCostUsd,
            String costStatus
    ) {
        static PlaybackReceipt noCurrentExecution() {
            return new PlaybackReceipt(
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    false,
                    false,
                    null,
                    "not_incurred"
            );
        }
    }
}
