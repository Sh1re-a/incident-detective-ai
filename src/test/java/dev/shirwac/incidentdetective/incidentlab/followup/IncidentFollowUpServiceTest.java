package dev.shirwac.incidentdetective.incidentlab.followup;

import dev.shirwac.incidentdetective.ai.GoogleGenAiProviderRoute;
import dev.shirwac.incidentdetective.incidentlab.IncidentLabRunResponse;
import dev.shirwac.incidentdetective.live.LiveAiOperation;
import dev.shirwac.incidentdetective.live.LiveAiRunGuard;
import dev.shirwac.incidentdetective.nordly.KnowledgeRagSafetyGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IncidentFollowUpServiceTest {

    private IncidentRunSnapshotRegistry registry;
    private IncidentFollowUpRouter router;
    private LiveAiRunGuard guard;
    private KnowledgeRagSafetyGate safety;
    private IncidentFollowUpService service;

    @BeforeEach
    void setUp() {
        registry = mock(IncidentRunSnapshotRegistry.class);
        router = mock(IncidentFollowUpRouter.class);
        guard = mock(LiveAiRunGuard.class);
        safety = mock(KnowledgeRagSafetyGate.class);
        service = new IncidentFollowUpService(
                registry,
                mock(IncidentFollowUpSnapshotProjector.class),
                router,
                guard,
                safety,
                mock(IncidentFollowUpEventLogger.class)
        );
        when(registry.fingerprint(any())).thenReturn("f".repeat(64));
        when(registry.cachedTurn(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(registry.reserveTurn(any(), any(), any())).thenReturn(true);
    }

    @Test
    void liveQuestionUsesModelOnlyAsSectionRouter() {
        IncidentFollowUpSnapshot snapshot = snapshot(
                IncidentFollowUpResponse.Mode.LIVE_AI
        );
        when(registry.find(any())).thenReturn(Optional.of(
                new IncidentRunSnapshotRegistry.StoredSnapshot(
                        "ilr_12345678901234567890123456789012",
                        snapshot,
                        "a".repeat(64)
                )
        ));
        when(safety.evaluate(any())).thenReturn(new KnowledgeRagSafetyGate.Decision(
                true,
                KnowledgeRagSafetyGate.ReasonCode.NONE,
                "Tillåten",
                "Allowed"
        ));
        when(router.route(any())).thenReturn(new IncidentFollowUpRouter.Result(
                new IncidentFollowUpRouter.Decision(
                        IncidentFollowUpRouter.Intent.INCIDENT_QUESTION,
                        List.of(
                                IncidentFollowUpRouter.Section.CAUSE,
                                IncidentFollowUpRouter.Section.SOURCES
                        )
                ),
                new IncidentFollowUpRouter.ProviderMetadata(
                        new GoogleGenAiProviderRoute(
                                "vertex_ai", "adc", "europe-west1"
                        ),
                        "provider-response",
                        "gemini-test",
                        null,
                        15
                )
        ));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
                .when(guard)
                .runConfirmed(
                        eq(true),
                        eq(LiveAiOperation.INCIDENT_FOLLOW_UP),
                        any()
                );

        IncidentFollowUpResponse response = service.answer(request(
                null,
                true
        ));

        assertEquals(IncidentFollowUpResponse.AnswerState.ANSWERED,
                response.answerState());
        assertEquals("CATALOG_SERVICE",
                response.answer().problemLocation().service());
        assertEquals(1, response.receipt().providerCalls());
        assertEquals(0, response.receipt().toolCalls());
        assertFalse(response.receipt().writeToolsAvailable());
        assertFalse(response.receipt().actionExecuted());
        assertEquals("route_lead_text_sections_only",
                response.provider().purpose());
        List<IncidentFollowUpResponse.Claim> knownClaims = response.claims()
                .stream()
                .filter(claim -> claim.section()
                        == IncidentFollowUpResponse.ClaimSection.KNOWN)
                .toList();
        assertEquals(List.of("alarm-1"), knownClaims.get(0).citationIds());
        assertTrue(knownClaims.get(1).citationIds().contains("log-1"));
        verify(registry).completeTurn(
                eq(response.runReference()),
                eq(response.clientTurnId()),
                eq(response)
        );
    }

    @Test
    void replaySuggestionIsProviderFree() {
        IncidentFollowUpSnapshot snapshot = snapshot(
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        );
        when(registry.find(any())).thenReturn(Optional.of(
                new IncidentRunSnapshotRegistry.StoredSnapshot(
                        "ilr_12345678901234567890123456789012",
                        snapshot,
                        "b".repeat(64)
                )
        ));

        IncidentFollowUpResponse response = service.answer(request(
                "how_conclusion",
                false
        ));

        assertEquals(IncidentFollowUpResponse.Mode.RECORDED_REPLAY,
                response.mode());
        assertEquals(0, response.receipt().providerCalls());
        assertFalse(response.receipt().liveQuotaConsumed());
        assertNull(response.provider());
        verify(guard, never()).runConfirmed(any(Boolean.class), any(), any());
        verify(router, never()).route(any());
    }

    @Test
    void replayFreeQuestionReturnsExplicitUnsupportedState() {
        IncidentFollowUpSnapshot snapshot = snapshot(
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        );
        when(registry.find(any())).thenReturn(Optional.of(
                new IncidentRunSnapshotRegistry.StoredSnapshot(
                        "ilr_12345678901234567890123456789012",
                        snapshot,
                        "c".repeat(64)
                )
        ));

        IncidentFollowUpResponse response = service.answer(request(
                "Kan du boka lunch åt mig?",
                null,
                false
        ));

        assertEquals(
                IncidentFollowUpResponse.AnswerState
                        .REPLAY_QUESTION_NOT_SUPPORTED,
                response.answerState()
        );
        assertEquals(0, response.receipt().providerCalls());
        assertTrue(response.claims().isEmpty());
        assertTrue(response.citations().isEmpty());
        assertNull(response.answer().problemLocation().service());
        assertEquals("blocked", response.steps().getFirst().status());
        assertEquals("skipped", response.steps().get(1).status());
        assertEquals("skipped", response.steps().get(2).status());
        verify(router, never()).route(any());
    }

    @Test
    void replayNaturalIncidentQuestionIsAnsweredFromFrozenReceipt() {
        IncidentFollowUpSnapshot snapshot = snapshot(
                IncidentFollowUpResponse.Mode.RECORDED_REPLAY
        );
        when(registry.find(any())).thenReturn(Optional.of(
                new IncidentRunSnapshotRegistry.StoredSnapshot(
                        "ilr_12345678901234567890123456789012",
                        snapshot,
                        "d".repeat(64)
                )
        ));

        IncidentFollowUpResponse response = service.answer(request(
                "Varför hände det och vilka källor använde du?",
                null,
                false
        ));

        assertEquals(IncidentFollowUpResponse.AnswerState.ANSWERED,
                response.answerState());
        assertTrue(response.answer().text().contains("Verifierad orsak"));
        assertFalse(response.citations().isEmpty());
        assertEquals(0, response.receipt().providerCalls());
        verify(router, never()).route(any());
    }

    @Test
    void blockedQuestionReleasesNoIncidentFactsOrSources() {
        IncidentFollowUpSnapshot snapshot = snapshot(
                IncidentFollowUpResponse.Mode.LIVE_AI
        );
        when(registry.find(any())).thenReturn(Optional.of(
                new IncidentRunSnapshotRegistry.StoredSnapshot(
                        "ilr_12345678901234567890123456789012",
                        snapshot,
                        "e".repeat(64)
                )
        ));
        when(safety.evaluate(any())).thenReturn(new KnowledgeRagSafetyGate.Decision(
                false,
                KnowledgeRagSafetyGate.ReasonCode.PII_REQUEST,
                "Stoppad",
                "Blocked"
        ));

        IncidentFollowUpResponse response = service.answer(request(null, true));

        assertEquals(IncidentFollowUpResponse.AnswerState.OUTSIDE_SCOPE,
                response.answerState());
        assertEquals(0, response.receipt().providerCalls());
        assertEquals(0, response.receipt().modelCalls());
        assertFalse(response.receipt().liveQuotaConsumed());
        assertNull(response.answer().problemLocation().service());
        assertFalse(response.answer().cause().summary()
                .contains("cache-invalideringen"));
        assertTrue(response.answer().known().isEmpty());
        assertTrue(response.answer().unknown().isEmpty());
        assertTrue(response.claims().isEmpty());
        assertTrue(response.citations().isEmpty());
        assertTrue(response.steps().stream()
                .flatMap(step -> step.evidenceIds().stream())
                .findAny()
                .isEmpty());
        assertEquals("blocked", response.steps().getFirst().status());
        assertEquals("skipped", response.steps().get(1).status());
        assertEquals("skipped", response.steps().get(2).status());
        verify(guard, never()).runConfirmed(any(Boolean.class), any(), any());
        verify(router, never()).route(any());
    }

    @Test
    void followUpContractsCannotCarryRawConversationHistory() {
        assertEquals(6, IncidentFollowUpRequest.class
                .getRecordComponents().length);
        assertEquals(3, IncidentFollowUpRouter.Input.class
                .getRecordComponents().length);
    }

    private IncidentFollowUpRequest request(
            String suggestionId,
            boolean confirm
    ) {
        return request(
                "Hur kom du fram till slutsatsen?",
                suggestionId,
                confirm
        );
    }

    private IncidentFollowUpRequest request(
            String question,
            String suggestionId,
            boolean confirm
    ) {
        return new IncidentFollowUpRequest(
                "ilr_12345678901234567890123456789012",
                "turn_12345678",
                question,
                "sv",
                suggestionId,
                confirm
        );
    }

    private IncidentFollowUpSnapshot snapshot(
            IncidentFollowUpResponse.Mode mode
    ) {
        return new IncidentFollowUpSnapshot(
                "scenario-1",
                mode,
                IncidentLabRunResponse.AnswerState.DIAGNOSED,
                "CATALOG_SERVICE",
                report("Verifierad orsak: cache-invalideringen misslyckades."),
                report("Verified cause: cache invalidation failed."),
                List.of(
                        new IncidentFollowUpSnapshot.Source(
                                "log-1", "synthetic/log-1", "log",
                                "Cache mismatch", "Cache mismatch",
                                IncidentFollowUpResponse.TargetScene.LOGS,
                                "log-1"
                        ),
                        new IncidentFollowUpSnapshot.Source(
                                "java-1", "java://verification", "java_verification",
                                "Java verification", "All checks passed",
                                IncidentFollowUpResponse.TargetScene.JAVA,
                                "java-1"
                        ),
                        new IncidentFollowUpSnapshot.Source(
                                "alarm-1", "alarm://catalog-version", "alarm_receipt",
                                "Catalog alarm", "Observed 3, threshold 1",
                                IncidentFollowUpResponse.TargetScene.LOGS,
                                "alarm-1"
                        )
                ),
                List.of(
                        new IncidentFollowUpSnapshot.VerifiedFact(
                                "root_cause",
                                "CATALOG_CACHE_INVALIDATION_FAILURE",
                                List.of("log-1")
                        ),
                        new IncidentFollowUpSnapshot.VerifiedFact(
                                "affected_service",
                                "CATALOG_SERVICE",
                                List.of("log-1")
                        )
                ),
                new IncidentFollowUpSnapshot.Boundary(
                        5, false, false, true, true
                )
        );
    }

    private IncidentFollowUpSnapshot.LocalizedReport report(String cause) {
        return new IncidentFollowUpSnapshot.LocalizedReport(
                "Verifierad incident",
                "Berörd tjänst: Catalog service.",
                cause,
                "Kunder såg gammal data.",
                List.of(
                        "Larmet passerade den deterministiska tröskeln.",
                        "Verifierad orsak: cache-invalideringen misslyckades."
                ),
                List.of("Produktion är inte undersökt."),
                "Verifierad i det syntetiska fallet",
                "Ingen åtgärd utfördes."
        );
    }
}
