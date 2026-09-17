package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Profile("rag")
public final class IncidentFollowUpService {

    private static final String DELIVERY = "synchronous_frozen_snapshot";
    private static final String SAFE_BOUNDARY_QUESTION_SV =
            "Förklara agentens verifierade befogenhet och säkerhetsgräns "
                    + "för en skyddad begäran om den här incidenten.";
    private static final String SAFE_BOUNDARY_QUESTION_EN =
            "Explain the agent's verified authority and safety boundary "
                    + "for a protected request about this incident.";
    private static final Map<String, List<IncidentFollowUpRouter.Section>>
            REPLAY_SELECTIONS = Map.of(
            "how_conclusion", List.of(
                    IncidentFollowUpRouter.Section.CAUSE,
                    IncidentFollowUpRouter.Section.KNOWN,
                    IncidentFollowUpRouter.Section.SOURCES
            ),
            "show_sources", List.of(IncidentFollowUpRouter.Section.SOURCES),
            "what_unknown", List.of(IncidentFollowUpRouter.Section.UNKNOWN),
            "customer_impact", List.of(
                    IncidentFollowUpRouter.Section.CUSTOMER_IMPACT
            ),
            "agent_boundary", List.of(IncidentFollowUpRouter.Section.BOUNDARY)
    );

    private final IncidentRunSnapshotRegistry registry;
    private final IncidentFollowUpSnapshotProjector projector;
    private final IncidentFollowUpRouter router;
    private final LiveAiRunGuard liveAiRunGuard;
    private final KnowledgeRagSafetyGate safetyGate;
    private final IncidentFollowUpEventLogger eventLogger;

    public IncidentFollowUpService(
            IncidentRunSnapshotRegistry registry,
            IncidentFollowUpSnapshotProjector projector,
            IncidentFollowUpRouter router,
            LiveAiRunGuard liveAiRunGuard,
            KnowledgeRagSafetyGate safetyGate,
            IncidentFollowUpEventLogger eventLogger
    ) {
        this.registry = registry;
        this.projector = projector;
        this.router = router;
        this.liveAiRunGuard = liveAiRunGuard;
        this.safetyGate = safetyGate;
        this.eventLogger = eventLogger;
    }

    public String registerLive(IncidentLabRunResponse run) {
        return registry.register(projector.project(
                run,
                IncidentFollowUpResponse.Mode.LIVE_AI
        ));
    }

    public String registerReplay(IncidentLabRunResponse run) {
        return registry.register(projector.project(
                run,
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        ));
    }

    public IncidentFollowUpResponse answer(IncidentFollowUpRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        IncidentRunSnapshotRegistry.StoredSnapshot stored = registry
                .find(request.runReference())
                .orElseThrow(() -> new IncidentFollowUpException(
                        IncidentFollowUpException.Code.RUN_REFERENCE_EXPIRED,
                        "The incident run reference is unknown or expired"
                ));
        String fingerprint = registry.fingerprint(request);
        var cached = registry.cachedTurn(
                request.runReference(),
                request.clientTurnId(),
                fingerprint
        );
        if (cached.isPresent()) {
            return cached.orElseThrow();
        }
        if (!registry.reserveTurn(
                request.runReference(),
                request.clientTurnId(),
                fingerprint
        )) {
            return registry.cachedTurn(
                    request.runReference(),
                    request.clientTurnId(),
                    fingerprint
            ).orElseThrow();
        }
        eventLogger.started(
                request.runReference(),
                request.clientTurnId(),
                stored.snapshot().mode()
        );
        try {
            IncidentFollowUpResponse response = execute(stored, request);
            registry.completeTurn(
                    request.runReference(),
                    request.clientTurnId(),
                    response
            );
            eventLogger.completed(response);
            return response;
        } catch (RuntimeException failure) {
            registry.failTurn(request.runReference(), request.clientTurnId());
            eventLogger.failed(
                    request.runReference(),
                    request.clientTurnId(),
                    failure
            );
            throw failure;
        }
    }

    private IncidentFollowUpResponse execute(
            IncidentRunSnapshotRegistry.StoredSnapshot stored,
            IncidentFollowUpRequest request
    ) {
        IncidentFollowUpSnapshot snapshot = stored.snapshot();
        if (snapshot.mode() == IncidentFollowUpResponse.Mode.RECORDED_REPLAY) {
            List<IncidentFollowUpRouter.Section> sections =
                    request.suggestionId() == null
                            ? replaySections(request.question())
                            : REPLAY_SELECTIONS.get(request.suggestionId());
            if (sections == null) {
                return response(
                        stored,
                        request,
                        IncidentFollowUpResponse.AnswerState
                                .REPLAY_QUESTION_NOT_SUPPORTED,
                        List.of(IncidentFollowUpRouter.Section.BOUNDARY),
                        null,
                        false
                );
            }
            return response(
                    stored,
                    request,
                    IncidentFollowUpResponse.AnswerState.ANSWERED,
                    sections,
                    null,
                    false
            );
        }

        KnowledgeRagSafetyGate.Decision safety = safetyGate.evaluate(
                request.question()
        );
        if (!safety.allowed()
                && safetyGate.containsRawSensitiveValue(request.question())) {
            return response(
                    stored,
                    request,
                    IncidentFollowUpResponse.AnswerState.OUTSIDE_SCOPE,
                    List.of(IncidentFollowUpRouter.Section.BOUNDARY),
                    null,
                    false
            );
        }
        String routingQuestion = safety.allowed()
                ? request.question()
                : safeBoundaryQuestion(request.locale());
        IncidentFollowUpRouter.Result routed = liveAiRunGuard.runConfirmed(
                request.confirmLiveAi(),
                LiveAiOperation.INCIDENT_FOLLOW_UP,
                () -> router.route(new IncidentFollowUpRouter.Input(
                        routingQuestion,
                        request.locale(),
                        snapshot.originalAnswerState().name()
                ))
        );
        if (!safety.allowed()) {
            return response(
                    stored,
                    request,
                    IncidentFollowUpResponse.AnswerState.OUTSIDE_SCOPE,
                    List.of(IncidentFollowUpRouter.Section.BOUNDARY),
                    routed.provider(),
                    true
            );
        }
        boolean outside = routed.decision().intent()
                == IncidentFollowUpRouter.Intent.OUTSIDE_SCOPE;
        return response(
                stored,
                request,
                outside
                        ? IncidentFollowUpResponse.AnswerState.OUTSIDE_SCOPE
                        : IncidentFollowUpResponse.AnswerState.ANSWERED,
                routed.decision().sections(),
                routed.provider(),
                true
        );
    }

    private String safeBoundaryQuestion(String locale) {
        return "en".equals(locale)
                ? SAFE_BOUNDARY_QUESTION_EN
                : SAFE_BOUNDARY_QUESTION_SV;
    }

    private List<IncidentFollowUpRouter.Section> replaySections(
            String question
    ) {
        String value = Normalizer.normalize(
                        question == null ? "" : question,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
        LinkedHashSet<IncidentFollowUpRouter.Section> sections =
                new LinkedHashSet<>();
        if (value.matches(".*(?:varfor|orsak|cause|why|hur kom|how did).*")) {
            sections.add(IncidentFollowUpRouter.Section.CAUSE);
            sections.add(IncidentFollowUpRouter.Section.KNOWN);
        }
        if (value.matches(".*(?:vet (?:du )?inte|osaker|unknown|dont know|do not know).*")) {
            sections.add(IncidentFollowUpRouter.Section.UNKNOWN);
        }
        if (value.matches(".*(?:kund|customer|paverkan|impact|affar|business|kop).*")) {
            sections.add(IncidentFollowUpRouter.Section.CUSTOMER_IMPACT);
            sections.add(IncidentFollowUpRouter.Section.KNOWN);
        }
        if (value.matches(".*(?:befogen|atgard|andra|allowed|boundary|change|do).*")) {
            sections.add(IncidentFollowUpRouter.Section.BOUNDARY);
        }
        if (value.matches(".*(?:vad hande|sammanfatta|rapport|status|what happened|summary).*")) {
            sections.add(IncidentFollowUpRouter.Section.SUMMARY);
            sections.add(IncidentFollowUpRouter.Section.CUSTOMER_IMPACT);
            sections.add(IncidentFollowUpRouter.Section.KNOWN);
            sections.add(IncidentFollowUpRouter.Section.UNKNOWN);
        }
        if (value.matches(".*(?:kall|source|bevis|evidence|logg|runbook).*")) {
            sections.add(IncidentFollowUpRouter.Section.SOURCES);
        }
        return sections.isEmpty() ? null : List.copyOf(sections);
    }

    private IncidentFollowUpResponse response(
            IncidentRunSnapshotRegistry.StoredSnapshot stored,
            IncidentFollowUpRequest request,
            IncidentFollowUpResponse.AnswerState answerState,
            List<IncidentFollowUpRouter.Section> sections,
            IncidentFollowUpRouter.ProviderMetadata provider,
            boolean liveQuotaConsumed
    ) {
        IncidentFollowUpSnapshot snapshot = stored.snapshot();
        IncidentFollowUpSnapshot.LocalizedReport report = "en".equals(
                request.locale()
        ) ? snapshot.en() : snapshot.sv();
        boolean replayUnsupported = answerState
                == IncidentFollowUpResponse.AnswerState
                .REPLAY_QUESTION_NOT_SUPPORTED;
        boolean outside = answerState
                == IncidentFollowUpResponse.AnswerState.OUTSIDE_SCOPE;
        boolean incidentFactsReleased = !replayUnsupported && !outside;
        List<IncidentFollowUpResponse.Claim> claims = incidentFactsReleased
                ? claims(snapshot, report)
                : List.of();
        List<IncidentFollowUpResponse.Citation> citations = citations(
                snapshot,
                claims
        );
        String text = replayUnsupported
                ? replayUnsupportedText(request.locale())
                : outside
                ? outsideText(request.locale(), report.boundary())
                : compose(report, sections, request.locale(), citations.size());
        List<String> evidenceIds = citations.stream()
                .map(IncidentFollowUpResponse.Citation::evidenceId)
                .toList();
        String notReleased = local(request.locale(),
                "Inte publicerat för den här frågan.",
                "Not released for this question.");
        IncidentFollowUpResponse.Answer answer = incidentFactsReleased
                ? new IncidentFollowUpResponse.Answer(
                text,
                new IncidentFollowUpResponse.ProblemLocation(
                        service(snapshot),
                        report.problemLocation(),
                        report.certainty()
                ),
                new IncidentFollowUpResponse.Finding(
                        report.cause(),
                        report.certainty()
                ),
                report.customerImpact(),
                report.known(),
                report.unknown(),
                report.boundary()
        )
                : new IncidentFollowUpResponse.Answer(
                text,
                new IncidentFollowUpResponse.ProblemLocation(
                        null,
                        notReleased,
                        "not_evaluated"
                ),
                new IncidentFollowUpResponse.Finding(
                        notReleased,
                        "not_evaluated"
                ),
                notReleased,
                List.of(),
                List.of(),
                report.boundary()
        );
        String releaseStatus = incidentFactsReleased ? "completed" : "skipped";
        String screenStatus = incidentFactsReleased ? "completed" : "blocked";
        List<String> javaStepEvidence = incidentFactsReleased
                ? javaEvidence(snapshot)
                : List.of();
        return new IncidentFollowUpResponse(
                IncidentFollowUpResponse.CONTRACT_VERSION,
                request.runReference(),
                request.clientTurnId(),
                snapshot.mode(),
                DELIVERY,
                answerState,
                answer,
                claims,
                citations,
                List.of(
                        step(1, "screen_question", screenStatus,
                                local(request.locale(),
                                        "Frågan kontrollerades mot den avgränsade incidenten.",
                                        "The question was screened against the bounded incident."),
                                List.of()),
                        step(2, "select_frozen_evidence", releaseStatus,
                                incidentFactsReleased
                                        ? local(request.locale(),
                                        "Endast källor från det frysta körningskvittot valdes.",
                                        "Only sources from the frozen run receipt were selected.")
                                        : local(request.locale(),
                                        "Inga incidentkällor publicerades för frågan.",
                                        "No incident sources were released for the question."),
                                evidenceIds),
                        step(3, "compose_bounded_report", releaseStatus,
                                incidentFactsReleased
                                        ? local(request.locale(),
                                        "Rapporten byggdes av backendägda, lokaliserade fakta.",
                                        "The report was composed from backend-owned localized facts.")
                                        : local(request.locale(),
                                        "Ingen incidentrapport byggdes; endast systemgränsen returnerades.",
                                        "No incident report was composed; only the system boundary was returned."),
                                evidenceIds),
                        step(4, "java_verify", "completed",
                                local(request.locale(),
                                        "Java verifierade källomfång, tillstånd och handlingsgräns.",
                                        "Java verified source scope, answer state, and action boundary."),
                                javaStepEvidence)
                ),
                new IncidentFollowUpResponse.Verification(
                        "verified_from_frozen_receipt",
                        stored.snapshotSha256(),
                        true,
                        true,
                        true,
                        false,
                        false
                ),
                new IncidentFollowUpResponse.Receipt(
                        provider == null ? 0 : 1,
                        provider == null ? 0 : 1,
                        0,
                        1,
                        liveQuotaConsumed,
                        false,
                        false
                ),
                provider == null ? null : new IncidentFollowUpResponse.ProviderReceipt(
                        provider.route(),
                        provider.modelVersion(),
                        provider.responseId(),
                        provider.tokenUsage(),
                        provider.latencyMs(),
                        "route_lead_text_sections_only"
                ),
                suggestions(request.locale()),
                List.of(
                        local(request.locale(),
                                "Svaret kan endast förklara den avslutade syntetiska körningen.",
                                "The answer can only explain the completed synthetic run."),
                        local(request.locale(),
                                "Ingen ny hämtning, utredning eller åtgärd utfördes.",
                                "No new retrieval, investigation, or action was performed.")
                )
        );
    }

    private List<IncidentFollowUpResponse.Claim> claims(
            IncidentFollowUpSnapshot snapshot,
            IncidentFollowUpSnapshot.LocalizedReport report
    ) {
        List<String> location = ids(snapshot, "affected_service");
        List<String> cause = ids(snapshot, "root_cause");
        List<String> impact = ids(snapshot, "customer_impact");
        List<String> observed = ids(snapshot, "observed_symptom");
        List<String> alarm = snapshot.sources().stream()
                .filter(source -> "alarm_receipt".equals(source.sourceType()))
                .map(IncidentFollowUpSnapshot.Source::evidenceId)
                .toList();
        List<String> java = javaEvidence(snapshot);
        List<String> scenario = snapshot.sources().stream()
                .filter(source -> "synthetic_scenario".equals(
                        source.sourceType()
                ))
                .map(IncidentFollowUpSnapshot.Source::evidenceId)
                .toList();
        List<IncidentFollowUpResponse.Claim> result = new ArrayList<>();
        result.add(claim(IncidentFollowUpResponse.ClaimSection.PROBLEM_LOCATION,
                report.problemLocation(), fallback(location, java)));
        result.add(claim(IncidentFollowUpResponse.ClaimSection.CAUSE,
                report.cause(), fallback(cause, java)));
        result.add(claim(IncidentFollowUpResponse.ClaimSection.CUSTOMER_IMPACT,
                report.customerImpact(), fallback(impact, scenario, java)));
        for (int index = 0; index < report.known().size(); index++) {
            String text = report.known().get(index);
            List<String> directSupport;
            if (index == 0 && !alarm.isEmpty()) {
                directSupport = alarm;
            } else if (snapshot.originalAnswerState()
                    == IncidentLabRunResponse.AnswerState.DIAGNOSED) {
                directSupport = merge(cause, location, java);
            } else if (snapshot.originalAnswerState()
                    == IncidentLabRunResponse.AnswerState
                    .INSUFFICIENT_EVIDENCE && !observed.isEmpty()) {
                directSupport = observed;
            } else {
                directSupport = java;
            }
            result.add(claim(
                    IncidentFollowUpResponse.ClaimSection.KNOWN,
                    text,
                    directSupport
            ));
        }
        report.unknown().forEach(text -> result.add(claim(
                IncidentFollowUpResponse.ClaimSection.UNKNOWN,
                text,
                java
        )));
        result.add(claim(IncidentFollowUpResponse.ClaimSection.BOUNDARY,
                report.boundary(), java));
        return List.copyOf(result);
    }

    private List<IncidentFollowUpResponse.Citation> citations(
            IncidentFollowUpSnapshot snapshot,
            List<IncidentFollowUpResponse.Claim> claims
    ) {
        Set<String> required = claims.stream()
                .flatMap(claim -> claim.citationIds().stream())
                .collect(java.util.stream.Collectors.toCollection(
                        LinkedHashSet::new
                ));
        Map<String, IncidentFollowUpSnapshot.Source> available =
                new LinkedHashMap<>();
        snapshot.sources().forEach(source -> available.put(
                source.evidenceId(),
                source
        ));
        if (!available.keySet().containsAll(required)) {
            throw new IncidentFollowUpException(
                    IncidentFollowUpException.Code.RESPONSE_NOT_VERIFIABLE,
                    "A follow-up claim referenced evidence outside the frozen snapshot"
            );
        }
        return required.stream().map(available::get).map(source ->
                new IncidentFollowUpResponse.Citation(
                        source.evidenceId(),
                        source.sourceRef(),
                        source.sourceType(),
                        source.label(),
                        source.targetScene(),
                        source.targetId()
                )).toList();
    }

    private String compose(
            IncidentFollowUpSnapshot.LocalizedReport report,
            List<IncidentFollowUpRouter.Section> sections,
            String locale,
            int sourceCount
    ) {
        List<String> sentences = new ArrayList<>();
        for (IncidentFollowUpRouter.Section section : sections) {
            switch (section) {
                case SUMMARY -> sentences.add(report.headline() + ".");
                case PROBLEM_LOCATION -> sentences.add(report.problemLocation());
                case CAUSE -> sentences.add(report.cause());
                case CUSTOMER_IMPACT -> sentences.add(report.customerImpact());
                case KNOWN -> sentences.addAll(report.known());
                case UNKNOWN -> sentences.addAll(report.unknown());
                case SOURCES -> sentences.add(local(locale,
                        "Svaret stöds av " + sourceCount
                                + " källor i körningens frysta kvitto.",
                        "The answer is backed by " + sourceCount
                                + " sources in the frozen run receipt."));
                case BOUNDARY -> sentences.add(report.boundary());
            }
        }
        return sentences.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String replayUnsupportedText(String locale) {
        return local(locale,
                "Det här är en verifierad repris. Välj en av följdfrågorna nedan; ingen modell körs under reprisen.",
                "This is a verified replay. Choose one of the follow-up questions below; no model runs during replay.");
    }

    private String outsideText(String locale, String boundary) {
        return local(locale,
                "Jag kan förklara den här incidentens signaler, källor, påverkan och verifiering. Frågan ligger utanför det frysta körningskvittot. ",
                "I can explain this incident's signals, sources, impact, and verification. The question is outside the frozen run receipt. ")
                + boundary;
    }

    private String service(IncidentFollowUpSnapshot snapshot) {
        return snapshot.problemService();
    }

    private List<String> ids(IncidentFollowUpSnapshot snapshot, String code) {
        return snapshot.verifiedFacts().stream()
                .filter(fact -> code.equals(fact.claimCode()))
                .flatMap(fact -> fact.evidenceIds().stream())
                .distinct()
                .toList();
    }

    @SafeVarargs
    private final List<String> fallback(List<String>... candidates) {
        for (List<String> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return List.of();
    }

    private List<String> javaEvidence(IncidentFollowUpSnapshot snapshot) {
        return snapshot.sources().stream()
                .filter(source -> "java_verification".equals(source.sourceType()))
                .map(IncidentFollowUpSnapshot.Source::evidenceId)
                .toList();
    }

    @SafeVarargs
    private final List<String> merge(List<String>... values) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (List<String> value : values) {
            if (value != null) {
                merged.addAll(value);
            }
        }
        return List.copyOf(merged);
    }

    private IncidentFollowUpResponse.Claim claim(
            IncidentFollowUpResponse.ClaimSection section,
            String text,
            List<String> citations
    ) {
        return new IncidentFollowUpResponse.Claim(section, text, citations);
    }

    private IncidentFollowUpResponse.Step step(
            int sequence,
            String code,
            String status,
            String summary,
            List<String> evidenceIds
    ) {
        return new IncidentFollowUpResponse.Step(
                sequence,
                code,
                status,
                summary,
                evidenceIds
        );
    }

    private List<IncidentFollowUpResponse.SuggestedQuestion> suggestions(
            String locale
    ) {
        return List.of(
                suggestion("how_conclusion", locale,
                        "Hur kom du fram till slutsatsen?",
                        "How did you reach the conclusion?"),
                suggestion("show_sources", locale,
                        "Visa källorna", "Show the sources"),
                suggestion("what_unknown", locale,
                        "Vad vet du fortfarande inte?",
                        "What is still unknown?"),
                suggestion("customer_impact", locale,
                        "Hur påverkades kunden?",
                        "How was the customer affected?"),
                suggestion("agent_boundary", locale,
                        "Vad fick agenten inte göra?",
                        "What was the agent not allowed to do?")
        );
    }

    private IncidentFollowUpResponse.SuggestedQuestion suggestion(
            String id,
            String locale,
            String sv,
            String en
    ) {
        return new IncidentFollowUpResponse.SuggestedQuestion(
                id,
                local(locale, sv, en)
        );
    }

    private String local(String locale, String sv, String en) {
        return "en".equals(locale) ? en : sv;
    }
}
