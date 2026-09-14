package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;

import java.util.ArrayList;
import java.util.List;

/** Removes unverified model-authored prose from the public ADK receipt. */
final class AdkAgentTurnPublicProjector {

    private static final String PROJECTION_LIMITATION =
            "Model-authored diagnosis prose is not returned. Java projects "
            + "released diagnoses from verified codes and evidence IDs.";

    AdkAgentTurnResponse project(AdkAgentTurnResponse response) {
        if (response == null) {
            return null;
        }
        Diagnosis diagnosis = answerWasReleased(response)
                ? safeDiagnosis(response.diagnosis())
                : null;
        return new AdkAgentTurnResponse(
                response.contractVersion(),
                response.runId(),
                response.sessionId(),
                response.turnId(),
                response.mode(),
                response.truthLabel(),
                response.outcome(),
                response.providerRoute(),
                response.scenario(),
                response.safety(),
                response.runtime(),
                response.workflow(),
                safeEvents(response.events()),
                response.toolEvents(),
                response.diagnosticProbe(),
                diagnosis,
                response.verification(),
                null,
                response.verificationEvent(),
                response.receipt(),
                limitations(response.limitations())
        );
    }

    private boolean answerWasReleased(AdkAgentTurnResponse response) {
        return response.diagnosis() != null
                && response.verificationEvent() != null
                && response.verificationEvent().answerReleased();
    }

    private Diagnosis safeDiagnosis(Diagnosis source) {
        boolean diagnosed = source.status() == DiagnosisStatus.DIAGNOSED;
        return new Diagnosis(
                source.status(),
                source.rootCauseCode(),
                source.affectedService(),
                diagnosed
                        ? "Java released a diagnosis after deterministic "
                        + "verification of this generated synthetic case."
                        : "Java released an insufficient-evidence result for "
                        + "this generated synthetic case.",
                diagnosed
                        ? "Verified root-cause code "
                        + source.rootCauseCode()
                        + " and affected-service code "
                        + source.affectedService() + "."
                        : "Available evidence did not support a verified root "
                        + "cause or affected service.",
                source.claims().stream().map(this::safeClaim).toList(),
                new SafeNextStep(
                        diagnosed
                                ? "Have a human review the cited read-only "
                                + "evidence before approving any change."
                                : "Collect the missing read-only evidence and "
                                + "require human approval before any change.",
                        true
                )
        );
    }

    private Claim safeClaim(Claim source) {
        return new Claim(
                source.claimCode(),
                source.claimValueCode(),
                "Verified " + source.claimCode().wireValue().replace('_', ' ')
                        + " code: " + source.claimValueCode() + ".",
                source.evidenceIds()
        );
    }

    private List<AdkAgentTurnResponse.RuntimeEvent> safeEvents(
            List<AdkAgentTurnResponse.RuntimeEvent> events
    ) {
        return events.stream()
                .map(event -> new AdkAgentTurnResponse.RuntimeEvent(
                        event.sequence(),
                        event.eventId(),
                        event.invocationId(),
                        event.author(),
                        event.type(),
                        event.observedAt(),
                        event.finalResponse(),
                        event.contentWithheld() || event.text() != null,
                        null,
                        event.functionCalls(),
                        event.functionResponses(),
                        event.tokenUsage(),
                        event.providerModelVersion()
                ))
                .toList();
    }

    private List<String> limitations(List<String> source) {
        List<String> safe = new ArrayList<>(source);
        if (!safe.contains(PROJECTION_LIMITATION)) {
            safe.add(PROJECTION_LIMITATION);
        }
        return List.copyOf(safe);
    }
}
