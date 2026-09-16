package dev.shirwac.incidentdetective.adk;

import dev.shirwac.incidentdetective.domain.diagnosis.Claim;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
                safeEvents(
                        response.events(),
                        response.scenario() == null
                                ? null
                                : response.scenario().scenarioId()
                ),
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
            List<AdkAgentTurnResponse.RuntimeEvent> events,
            String scenarioId
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
                        safeFunctionResponses(
                                event.functionResponses(),
                                scenarioId
                        ),
                        event.tokenUsage(),
                        event.providerModelVersion()
                ))
                .toList();
    }

    private List<AdkAgentTurnResponse.FunctionResponseEvent>
    safeFunctionResponses(
            List<AdkAgentTurnResponse.FunctionResponseEvent> responses,
            String scenarioId
    ) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }
        return responses.stream()
                .filter(Objects::nonNull)
                .map(response -> new AdkAgentTurnResponse.FunctionResponseEvent(
                        response.id(),
                        response.name(),
                        publicFunctionResponse(response.response(), scenarioId)
                ))
                .toList();
    }

    private Map<String, Object> publicFunctionResponse(
            Map<String, Object> response,
            String scenarioId
    ) {
        Objects.requireNonNull(response, "function response must not be null");
        String publicScenarioId = scenarioId == null
                ? requiredString(response, "scenario_id")
                : scenarioId;
        return Map.of(
                "status", requiredString(response, "status"),
                "safe_summary", requiredString(response, "safe_summary"),
                "scenario_id", publicScenarioId,
                "evidence_ids", requiredStringList(response, "evidence_ids"),
                "source_refs", requiredStringList(response, "source_refs"),
                "write_capability", false,
                "action_executed", false
        );
    }

    private String requiredString(
            Map<String, Object> response,
            String field
    ) {
        Object value = response.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException(
                    "Public function response requires " + field
            );
        }
        return text;
    }

    private List<String> requiredStringList(
            Map<String, Object> response,
            String field
    ) {
        Object value = response.get(field);
        if (!(value instanceof List<?> values)
                || values.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalStateException(
                    "Public function response requires a string list for "
                            + field
            );
        }
        return values.stream().map(String.class::cast).toList();
    }

    private List<String> limitations(List<String> source) {
        List<String> safe = new ArrayList<>(source);
        if (!safe.contains(PROJECTION_LIMITATION)) {
            safe.add(PROJECTION_LIMITATION);
        }
        return List.copyOf(safe);
    }
}
