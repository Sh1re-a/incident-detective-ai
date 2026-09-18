package dev.shirwac.incidentdetective.incidentlab.replay;

public record IncidentLabReplayAvailabilityResponse(
        String contractVersion,
        boolean available,
        String replayContractVersion,
        String truthLabel,
        String truthLabelEn,
        String reasonCode
) {
    public static final String CONTRACT_VERSION =
            "incident-lab-replay-availability-v1";

    static IncidentLabReplayAvailabilityResponse ready() {
        return new IncidentLabReplayAvailabilityResponse(
                CONTRACT_VERSION,
                true,
                IncidentLabReplayResponse.CONTRACT_VERSION,
                "En verifierad förinspelad syntetisk körning finns tillgänglig.",
                "A verified recorded synthetic run is available.",
                "ready"
        );
    }

    static IncidentLabReplayAvailabilityResponse unavailable() {
        return new IncidentLabReplayAvailabilityResponse(
                CONTRACT_VERSION,
                false,
                IncidentLabReplayResponse.CONTRACT_VERSION,
                "Ingen verifierad förinspelad Incident Lab-körning är publicerad ännu.",
                "No verified recorded Incident Lab run has been published yet.",
                "golden_recording_not_captured"
        );
    }
}
