package dev.shirwac.incidentdetective.observability;

import dev.shirwac.incidentdetective.ai.ModelCallMetadata;
import dev.shirwac.incidentdetective.ai.ModelPhase;
import dev.shirwac.incidentdetective.ai.ModelProviderException;
import dev.shirwac.incidentdetective.ai.ModelProviderFailure;
import dev.shirwac.incidentdetective.ai.SynthesisModelResult;
import dev.shirwac.incidentdetective.domain.diagnosis.Diagnosis;
import dev.shirwac.incidentdetective.domain.diagnosis.DiagnosisStatus;
import dev.shirwac.incidentdetective.domain.diagnosis.SafeNextStep;
import dev.shirwac.incidentdetective.domain.verification.CitationValidity;
import dev.shirwac.incidentdetective.domain.verification.ClaimCoverage;
import dev.shirwac.incidentdetective.domain.verification.DiagnosisCorrectness;
import dev.shirwac.incidentdetective.domain.verification.EvidencePrecision;
import dev.shirwac.incidentdetective.domain.verification.VerificationReport;
import dev.shirwac.incidentdetective.investigation.CompletedInvestigationVerification;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalMetadata;
import dev.shirwac.incidentdetective.investigation.tools.ToolExecution;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.LiveRunStatus;
import dev.shirwac.incidentdetective.replay.ModelTokenUsage;
import dev.shirwac.incidentdetective.replay.ReplayComparison;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvestigationTelemetryTest {

    private InMemorySpanExporter exporter;
    private SdkTracerProvider tracerProvider;
    private InvestigationTelemetry telemetry;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        telemetry = new InvestigationTelemetry(openTelemetry);
    }

    @AfterEach
    void closeTelemetry() {
        tracerProvider.close();
        exporter.close();
    }

    @Test
    void emitsTheBoundedWorkflowWithOnlyAllowlistedSanitizedAttributes() {
        String secret = "sk-secret raw prompt provider response GroundTruth";
        ModelCallMetadata collectionMetadata = metadata(
                ModelPhase.COLLECT,
                secret
        );
        SynthesisModelResult synthesisResult = new SynthesisModelResult(
                new Diagnosis(
                        DiagnosisStatus.INSUFFICIENT_EVIDENCE,
                        null,
                        null,
                        secret,
                        secret,
                        List.of(),
                        new SafeNextStep(secret, true)
                ),
                metadata(ModelPhase.SYNTHESIZE, secret)
        );
        ToolExecution retrieval = new ToolExecution(
                secret,
                ToolName.RETRIEVE_RUNBOOKS,
                Map.of("query", secret, "api_key", secret),
                secret,
                List.of(),
                pgvectorMetadata()
        );

        try (InvestigationTelemetry.InvestigationSpan investigation =
                     telemetry.startInvestigation(
                             secret,
                             false,
                             2,
                             8,
                             45_000
                     )) {
            try (InvestigationTelemetry.CollectionSpan collection =
                         telemetry.startCollection(
                                 1,
                                 4,
                                 "gemini-3.1-flash-lite"
                         )) {
                collection.modelCompleted(collectionMetadata, 1);
                telemetry.executeTool(
                        1,
                        ToolName.RETRIEVE_RUNBOOKS,
                        () -> retrieval
                );
                collection.completed(0);
            }
            telemetry.synthesize(
                    "gemini-3.1-flash-lite",
                    0,
                    () -> synthesisResult
            );
            telemetry.verify(this::passingVerification);
            investigation.completed(
                    "00000000-0000-0000-0000-000000000001",
                    LiveRunStatus.COMPLETED,
                    0,
                    1,
                    2
            );
        }

        List<SpanData> spans = exporter.getFinishedSpanItems();
        Map<String, SpanData> byName = spans.stream().collect(Collectors.toMap(
                SpanData::getName,
                Function.identity()
        ));
        assertEquals(Set.of(
                InvestigationTelemetry.SPAN_INVESTIGATION,
                InvestigationTelemetry.SPAN_COLLECT,
                InvestigationTelemetry.SPAN_TOOL,
                InvestigationTelemetry.SPAN_RETRIEVAL,
                InvestigationTelemetry.SPAN_SYNTHESIZE,
                InvestigationTelemetry.SPAN_VERIFY
        ), byName.keySet());

        SpanData investigation = byName.get(
                InvestigationTelemetry.SPAN_INVESTIGATION
        );
        SpanData collect = byName.get(InvestigationTelemetry.SPAN_COLLECT);
        SpanData tool = byName.get(InvestigationTelemetry.SPAN_TOOL);
        SpanData retrievalSpan = byName.get(
                InvestigationTelemetry.SPAN_RETRIEVAL
        );
        assertEquals(
                investigation.getSpanContext().getSpanId(),
                collect.getParentSpanContext().getSpanId()
        );
        assertEquals(
                collect.getSpanContext().getSpanId(),
                tool.getParentSpanContext().getSpanId()
        );
        assertEquals(
                tool.getSpanContext().getSpanId(),
                retrievalSpan.getParentSpanContext().getSpanId()
        );

        spans.forEach(span -> {
            Set<String> keys = span.getAttributes().asMap().keySet().stream()
                    .map(AttributeKey::getKey)
                    .collect(Collectors.toSet());
            assertTrue(
                    InvestigationTelemetry.ATTRIBUTE_ALLOWLIST.containsAll(keys),
                    () -> "Unexpected attributes on " + span.getName() + ": " + keys
            );
            assertTrue(span.getEvents().isEmpty());
        });

        String recordedAttributes = spans.stream()
                .flatMap(span -> span.getAttributes().asMap().entrySet().stream())
                .map(entry -> entry.getKey().getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        assertFalse(recordedAttributes.contains(secret));
        assertFalse(recordedAttributes.contains("api_key"));
        assertFalse(recordedAttributes.contains("raw prompt"));
        assertFalse(recordedAttributes.contains("GroundTruth"));
        assertEquals(
                "synthetic_scenario",
                investigation.getAttributes().get(
                        AttributeKey.stringKey("incident.scenario.id")
                )
        );
        assertEquals(
                true,
                retrievalSpan.getAttributes().get(
                        AttributeKey.booleanKey(
                                "incident.retrieval.vector_database.active"
                        )
                )
        );
        assertEquals(
                768L,
                retrievalSpan.getAttributes().get(
                        AttributeKey.longKey(
                                "incident.embedding.dimension.count"
                        )
                )
        );
    }

    @Test
    void recordsOnlyASanitizedErrorCategoryWithoutExceptionPayload() {
        String secret = "sk-secret raw provider body";

        assertThrows(ModelProviderException.class, () -> telemetry.synthesize(
                "gemini-3.1-flash-lite",
                0,
                () -> {
                    throw new ModelProviderException(
                            ModelProviderFailure.UPSTREAM,
                            secret
                    );
                }
        ));

        SpanData span = exporter.getFinishedSpanItems().getFirst();
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals(
                "model_provider.upstream",
                span.getAttributes().get(AttributeKey.stringKey("error.type"))
        );
        assertTrue(span.getEvents().isEmpty());
        String attributes = span.getAttributes().asMap().toString();
        assertFalse(attributes.contains(secret));
    }

    private ModelCallMetadata metadata(ModelPhase phase, String providerId) {
        return new ModelCallMetadata(
                phase,
                1,
                providerId,
                "provider-model-version",
                new ModelTokenUsage(10, 3, 13),
                7
        );
    }

    private RunbookRetrievalMetadata pgvectorMetadata() {
        return RunbookRetrievalMetadata.pgvector(
                "runbook-corpus-v1",
                new RunbookRetrievalMetadata.EmbeddingProfile(
                        "gemini-embedding-2",
                        768,
                        "search-result-v1",
                        0.66
                ),
                new RunbookRetrievalMetadata.QueryEmbeddingUsage(
                        91,
                        91,
                        null,
                        12
                ),
                List.of(new RunbookRetrievalMetadata.Match(
                        1,
                        "evidence-id-hidden-from-span",
                        0.81,
                        "a".repeat(64)
                ))
        );
    }

    private CompletedInvestigationVerification passingVerification() {
        VerificationReport report = new VerificationReport(
                true,
                true,
                new CitationValidity(true, List.of()),
                EvidencePrecision.notApplicable(),
                ClaimCoverage.scored(1, 1),
                DiagnosisCorrectness.diagnosis(true, true),
                List.of()
        );
        return new CompletedInvestigationVerification(
                report,
                new ReplayComparison(
                        DiagnosisStatus.DIAGNOSED,
                        "EXPECTED_ROOT_CAUSE",
                        "EXPECTED_SERVICE",
                        true,
                        true,
                        false
                )
        );
    }
}
