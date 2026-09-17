package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.adk.AdkAgentTurnResponse;
import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.domain.evidence.RunbookEvidence;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.live.LiveToolEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Removes model narrative and retains only public, verified follow-up facts. */
@Component
public final class IncidentFollowUpSnapshotProjector {

    public IncidentFollowUpSnapshot project(
            IncidentLabRunResponse run,
            IncidentFollowUpResponse.Mode mode
    ) {
        IncidentLabRunResponse.LocalizedPresentation sv =
                run.localizedPresentations().sv();
        IncidentLabRunResponse.LocalizedPresentation en =
                run.localizedPresentations().en();
        Map<String, IncidentFollowUpSnapshot.Source> sources =
                new LinkedHashMap<>();
        run.backendLogs().forEach(evidence -> addSource(sources, evidence));
        String scenarioSourceId = "synthetic-scenario-"
                + run.scenario().scenarioId();
        sources.put(scenarioSourceId, new IncidentFollowUpSnapshot.Source(
                scenarioSourceId,
                "synthetic://scenario/" + run.scenario().scenarioId(),
                "synthetic_scenario",
                run.scenario().title(),
                run.scenario().businessImpactSummary(),
                IncidentFollowUpResponse.TargetScene.LOGS,
                scenarioSourceId
        ));
        AdkAgentTurnResponse agent = run.agentTurn();
        if (agent != null) {
            agent.toolEvents().stream()
                    .map(LiveToolEvent::evidence)
                    .flatMap(List::stream)
                    .forEach(evidence -> addSource(sources, evidence));
        }
        String javaId = "java-verification-" + run.scenario().scenarioId();
        String javaDetail = agent != null && agent.verificationEvent() != null
                ? agent.verificationEvent().summary()
                : "Java answer state: " + run.answerState().wireValue();
        sources.put(javaId, new IncidentFollowUpSnapshot.Source(
                javaId,
                "java://incident-lab/verification/"
                        + run.scenario().scenarioId(),
                "java_verification",
                "Java verification receipt",
                javaDetail,
                IncidentFollowUpResponse.TargetScene.JAVA,
                javaId
        ));
        if (run.alarmReceipt() != null) {
            String id = run.alarmReceipt().alarmId();
            sources.put(id, new IncidentFollowUpSnapshot.Source(
                    id,
                    "alarm://" + run.alarmReceipt().ruleId(),
                    "alarm_receipt",
                    "Deterministic alarm receipt",
                    run.alarmReceipt().signal().name() + ": "
                            + run.alarmReceipt().signal().observedValue() + " "
                            + run.alarmReceipt().signal().unit(),
                    IncidentFollowUpResponse.TargetScene.LOGS,
                    id
            ));
        }

        List<IncidentFollowUpSnapshot.VerifiedFact> facts =
                run.developerResponse().verifiedClaims().stream()
                        .map(claim -> new IncidentFollowUpSnapshot.VerifiedFact(
                                claim.claimCode(),
                                claim.claimValueCode(),
                                claim.evidenceIds()
                        ))
                        .toList();
        boolean released = run.answerState()
                == IncidentLabRunResponse.AnswerState.DIAGNOSED
                || run.answerState()
                == IncidentLabRunResponse.AnswerState.INSUFFICIENT_EVIDENCE;
        return new IncidentFollowUpSnapshot(
                run.scenario().scenarioId(),
                mode,
                run.answerState(),
                run.developerResponse().affectedService(),
                report(sv, "sv", run.answerState()),
                report(en, "en", run.answerState()),
                List.copyOf(sources.values()),
                facts,
                new IncidentFollowUpSnapshot.Boundary(
                        run.actionReceipt().readOperations(),
                        false,
                        false,
                        true,
                        released
                )
        );
    }

    private IncidentFollowUpSnapshot.LocalizedReport report(
            IncidentLabRunResponse.LocalizedPresentation presentation,
            String locale,
            IncidentLabRunResponse.AnswerState answerState
    ) {
        IncidentLabRunResponse.BusinessResponse business =
                presentation.businessResponse();
        IncidentLabRunResponse.DeveloperResponse developer =
                presentation.developerResponse();
        String service = developer.affectedService();
        String location = service == null
                ? ("sv".equals(locale)
                ? "Ingen berörd tjänst är verifierad."
                : "No affected service is verified.")
                : ("sv".equals(locale) ? "Berörd tjänst: " : "Affected service: ")
                + humanize(service) + ".";
        String cause = answerState == IncidentLabRunResponse.AnswerState.DIAGNOSED
                ? business.whatIsKnown().stream()
                .filter(value -> value.toLowerCase(Locale.ROOT)
                        .contains("cause")
                        || value.toLowerCase(Locale.ROOT).contains("orsak"))
                .findFirst()
                .orElseGet(() -> ("sv".equals(locale)
                        ? "Verifierad orsak: "
                        : "Verified cause: ")
                        + humanize(developer.rootCauseCode()) + ".")
                : ("sv".equals(locale)
                ? "Rotorsaken kan inte fastställas från det verifierade underlaget."
                : "The root cause cannot be established from the verified evidence.");
        return new IncidentFollowUpSnapshot.LocalizedReport(
                business.headline(),
                location,
                cause,
                business.impact(),
                business.whatIsKnown(),
                business.whatRemainsUnknown(),
                business.certainty(),
                presentation.actionReceipt().summary()
        );
    }

    private void addSource(
            Map<String, IncidentFollowUpSnapshot.Source> sources,
            Evidence evidence
    ) {
        String detail = evidence instanceof RunbookEvidence runbook
                ? runbook.content().text()
                : evidence.displaySummary();
        IncidentFollowUpResponse.TargetScene scene =
                evidence instanceof RunbookEvidence
                        ? IncidentFollowUpResponse.TargetScene.AGENT_RAG
                        : IncidentFollowUpResponse.TargetScene.LOGS;
        sources.putIfAbsent(evidence.evidenceId(),
                new IncidentFollowUpSnapshot.Source(
                        evidence.evidenceId(),
                        evidence.sourceRef(),
                        evidence.type().name().toLowerCase(Locale.ROOT),
                        evidence.displaySummary(),
                        detail,
                        scene,
                        evidence.evidenceId()
                ));
    }

    private String humanize(String code) {
        String value = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
