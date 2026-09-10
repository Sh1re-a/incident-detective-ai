package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.ai.GeminiAiProperties;
import dev.shirwac.incidentdetective.ai.GoogleGenAiProvider;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.DeploymentCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.EmbeddingCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.GeneratedCasesCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.KnowledgeCorpusCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.LiveAiCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.LiveBudgetCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ModeCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.PromptCacheCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ProviderCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.RetrievalCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ToolBudgetCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.ToolCapability;
import dev.shirwac.incidentdetective.capabilities.CapabilitiesResponse.VectorIndexCapability;
import dev.shirwac.incidentdetective.generated.GeneratedCaseFactory;
import dev.shirwac.incidentdetective.generated.GeneratedCaseRunResult;
import dev.shirwac.incidentdetective.generated.GeneratedEvidenceMode;
import dev.shirwac.incidentdetective.generated.GeneratedIncidentFamily;
import dev.shirwac.incidentdetective.generated.GeneratedNoiseLevel;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalStrategy;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.LiveInvestigationLimits;
import dev.shirwac.incidentdetective.live.LiveInvestigationService;
import dev.shirwac.incidentdetective.live.PromptCacheStrategy;
import dev.shirwac.incidentdetective.nordly.NordlyKnowledgeCorpus;
import dev.shirwac.incidentdetective.rag.RagProperties;
import dev.shirwac.incidentdetective.rag.RunbookIndexReadiness;
import dev.shirwac.incidentdetective.rag.RunbookIndexStatus;
import dev.shirwac.incidentdetective.replay.RecordedReplayService;
import dev.shirwac.incidentdetective.replay.RunMode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public final class CapabilitiesService {

    private final GeminiAiProperties ai;
    private final RunbookRetrievalStrategy retrieval;
    private final RagProperties rag;
    private final NordlyKnowledgeCorpus knowledgeCorpus;
    private final Environment environment;
    private final GlobalDailyLiveQuota dailyQuota;
    private final Optional<RunbookIndexReadiness> indexReadiness;

    public CapabilitiesService(
            GeminiAiProperties ai,
            RunbookRetrievalStrategy retrieval,
            RagProperties rag,
            NordlyKnowledgeCorpus knowledgeCorpus,
            Environment environment,
            GlobalDailyLiveQuota dailyQuota,
            Optional<RunbookIndexReadiness> indexReadiness
    ) {
        this.ai = ai;
        this.retrieval = retrieval;
        this.rag = rag;
        this.knowledgeCorpus = knowledgeCorpus;
        this.environment = environment;
        this.dailyQuota = dailyQuota;
        this.indexReadiness = indexReadiness;
    }

    public CapabilitiesResponse describe() {
        return new CapabilitiesResponse(
                CapabilitiesResponse.CONTRACT_VERSION,
                true,
                false,
                provider(),
                deployment(),
                knowledgeCorpus(),
                modes(),
                tools(),
                liveAi(),
                generatedCases(),
                retrieval(),
                new PromptCacheCapability(
                        PromptCacheStrategy.PROVIDER_IMPLICIT,
                        false,
                        true
                )
        );
    }

    private ProviderCapability provider() {
        String location = ai.provider() == GoogleGenAiProvider.VERTEX_AI
                ? ai.vertexLocation()
                : null;
        return new ProviderCapability(
                ai.provider().transport(),
                ai.provider().authenticationMode(),
                location,
                ai.hasProviderConfiguration(),
                credentialStatus()
        );
    }

    private DeploymentCapability deployment() {
        boolean cloudRun = configuredValue("K_SERVICE") != null;
        return new DeploymentCapability(
                cloudRun ? "cloud_run" : "local",
                cloudRun ? configuredValue("K_REVISION") : null,
                configuredValue("INCIDENT_DETECTIVE_BUILD_GIT_SHA")
        );
    }

    private KnowledgeCorpusCapability knowledgeCorpus() {
        return new KnowledgeCorpusCapability(
                knowledgeCorpus.manifestVersion(),
                knowledgeCorpus.version(),
                knowledgeCorpus.corpusContentSha256(),
                knowledgeCorpus.eligibleDocumentCount(),
                knowledgeCorpus.eligibleChunkCount()
        );
    }

    private List<ModeCapability> modes() {
        return List.of(
                new ModeCapability(
                        RunMode.RECORDED_REPLAY,
                        RecordedReplayService.TRUTH_LABEL,
                        false,
                        false
                ),
                new ModeCapability(
                        RunMode.LIVE_AI,
                        LiveInvestigationService.TRUTH_LABEL,
                        true,
                        true
                )
        );
    }

    private List<ToolCapability> tools() {
        return Arrays.stream(ToolName.values())
                .map(tool -> new ToolCapability(tool, true))
                .toList();
    }

    private LiveAiCapability liveAi() {
        LiveInvestigationLimits limits = LiveInvestigationLimits.current();
        List<ToolBudgetCapability> toolLimits = limits.maxCallsByTool()
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> new ToolBudgetCapability(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
        LiveBudgetCapability budget = new LiveBudgetCapability(
                limits.maxCollectionRounds(),
                limits.maxToolCallsTotal(),
                limits.maxToolCallsPerRound(),
                toolLimits,
                limits.hardDeadline().toMillis(),
                limits.providerCallCap().toMillis(),
                limits.dailyLiveRunLimit(),
                dailyQuota.scope()
        );
        return new LiveAiCapability(
                ai.liveEnabled(),
                ai.liveEnabled() && ai.hasProviderConfiguration(),
                true,
                ai.modelId(),
                ai.thinkingLevel(),
                ai.promptVersion(),
                budget
        );
    }

    private String credentialStatus() {
        if (ai.provider() == GoogleGenAiProvider.VERTEX_AI) {
            return "not_checked";
        }
        return ai.hasApiKey() ? "configured" : "missing";
    }

    private GeneratedCasesCapability generatedCases() {
        return new GeneratedCasesCapability(
                true,
                GeneratedCaseRunResult.CONTRACT_VERSION,
                GeneratedCaseFactory.GENERATOR_VERSION,
                LiveInvestigationService.GENERATED_TRUTH_LABEL,
                false,
                true,
                Arrays.stream(GeneratedIncidentFamily.values())
                        .map(GeneratedIncidentFamily::wireValue)
                        .toList(),
                Arrays.stream(GeneratedEvidenceMode.values())
                        .map(GeneratedEvidenceMode::wireValue)
                        .toList(),
                Arrays.stream(GeneratedNoiseLevel.values())
                        .map(GeneratedNoiseLevel::wireValue)
                        .toList(),
                Arrays.stream(ToolName.values()).toList()
        );
    }

    private RetrievalCapability retrieval() {
        boolean pgvectorActive = retrieval.backend()
                == RunbookRetrievalBackend.PGVECTOR_EXACT_COSINE;
        EmbeddingCapability embedding = pgvectorActive
                ? new EmbeddingCapability(
                        rag.providerTransport().transport(),
                        rag.embeddingModel(),
                        rag.embeddingDimensions(),
                        rag.embeddingFormatVersion(),
                        rag.minimumSimilarity()
                )
                : null;
        VectorIndexCapability index = pgvectorActive
                ? indexReadiness.map(this::indexCapability).orElse(null)
                : null;
        return new RetrievalCapability(
                retrieval.backend(),
                effectiveProfiles(),
                retrieval.safeModeDescription(),
                retrieval.limitation(),
                pgvectorActive,
                embedding,
                index
        );
    }

    private VectorIndexCapability indexCapability(
            RunbookIndexReadiness readiness
    ) {
        RunbookIndexStatus status = readiness.inspect();
        return new VectorIndexCapability(
                status.ready(),
                readiness.corpusVersion(),
                status.indexedChunks(),
                status.currentChunks(),
                status.expectedChunks()
        );
    }

    private List<String> effectiveProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            profiles = environment.getDefaultProfiles();
        }
        return Arrays.stream(profiles).sorted().toList();
    }

    private String configuredValue(String name) {
        String value = environment.getProperty(name);
        return value == null || value.isBlank() ? null : value.strip();
    }
}
