package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.diagnostic.DiagnosticProbeId;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.PromptCacheStrategy;
import dev.shirwac.incidentdetective.planning.IncidentService;
import dev.shirwac.incidentdetective.replay.RunMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CapabilitiesResponse(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = CONTRACT_VERSION,
                example = CONTRACT_VERSION
        )
        String contractVersion,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "True because every incident, signal and runbook is synthetic."
        )
        boolean syntheticOnly,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Always false. The backend never executes remediation."
        )
        boolean remediationEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ProviderCapability provider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DeploymentCapability deployment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        KnowledgeCorpusCapability knowledgeCorpus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ModeCapability> modes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ToolCapability> tools,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        DiagnosticProbeCapability diagnosticProbe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        IncidentLabCapability incidentLab,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LiveAiCapability liveAi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GeneratedCasesCapability generatedCases,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RetrievalCapability retrieval,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PromptCacheCapability promptCache
) {
    public static final String CONTRACT_VERSION = "capabilities-v5";

    public CapabilitiesResponse {
        modes = List.copyOf(modes);
        tools = List.copyOf(tools);
    }

    public record IncidentLabCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True only when the required profile, ADK, live AI, "
                            + "and non-secret provider routing configuration are enabled. "
                            + "This does not claim provider health or reachability."
            )
            boolean enabled,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {
                            "enabled_by_configuration_not_health_checked",
                            "rag_profile_required",
                            "adk_disabled",
                            "live_ai_disabled",
                            "provider_routing_not_configured"
                    }
            )
            String availabilityReason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> requiredProfiles,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String planContractVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String runContractVersion,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = "sequential_agent"
            )
            String orchestration,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> expectedAgentOrder,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = "synchronous_post_run"
            )
            String delivery,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean streaming,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean alarmRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> answerStates,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean explicitConfirmationRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean syntheticOnly,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean writeToolsAvailable,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean actionExecuted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean humanApprovalRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            IncidentLabToolCapability registeredTool,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Locales included in the backend-owned incident "
                            + "family catalog copy."
            )
            List<String> supportedLocales,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<IncidentFamilyCapability> incidentFamilies
    ) {
        public IncidentLabCapability {
            requiredProfiles = List.copyOf(requiredProfiles);
            expectedAgentOrder = List.copyOf(expectedAgentOrder);
            answerStates = List.copyOf(answerStates);
            supportedLocales = List.copyOf(supportedLocales);
            incidentFamilies = List.copyOf(incidentFamilies);
        }
    }

    public record IncidentLabToolCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = "inspect_incident_evidence"
            )
            String functionName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean readOnly,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean caseBound,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ToolName> readOperations,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Name of the top-level diagnostic_probe capability "
                            + "that defines this nested probe boundary."
            )
            String diagnosticProbeReference,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<DiagnosticProbeId> allowedDiagnosticProbeIds
    ) {
        public IncidentLabToolCapability {
            readOperations = List.copyOf(readOperations);
            allowedDiagnosticProbeIds = List.copyOf(allowedDiagnosticProbeIds);
        }
    }

    public record IncidentFamilyCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalizedCopy label,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalizedCopy description,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalizedCopy customerImpact,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            IncidentService service,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String alarmSignal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalizedCopy alarmSignalConcept
    ) {
    }

    public record LocalizedCopy(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String sv,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String en
    ) {
    }

    public record ProviderCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"developer_api", "vertex_ai"}
            )
            String transport,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"api_key", "adc"}
            )
            String authenticationMode,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "Configured Vertex AI location, or null for the "
                            + "Gemini Developer API."
            )
            String location,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when the selected provider's non-secret "
                            + "routing prerequisites are present. This does not "
                            + "prove authentication or provider reachability."
            )
            boolean routingConfigurationComplete,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"configured", "missing", "not_checked"},
                    description = "API-key presence for the Developer API, or "
                            + "not_checked for Vertex ADC. The capability probe "
                            + "never loads or validates ADC credentials."
            )
            String credentialStatus
    ) {
    }

    public record DeploymentCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"local", "cloud_run"}
            )
            String platform,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "Cloud Run revision reported by the runtime, or null."
            )
            String revision,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "Build Git SHA injected into the runtime, or null "
                            + "when it was not supplied."
            )
            String buildGitSha
    ) {
    }

    public record KnowledgeCorpusCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String manifestVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String corpusVersion,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Deterministic SHA-256 fingerprint of the eligible "
                            + "Nordly knowledge corpus."
            )
            String corpusContentSha256,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
            int eligibleDocumentCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
            int eligibleChunkCount
    ) {
    }

    public record ModeCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            RunMode mode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String truthLabel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean modelBacked,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean explicitConfirmationRequired
    ) {
    }

    public record ToolCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ToolName name,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True for every exposed investigation function."
            )
            boolean readOnly
    ) {
    }

    public record DiagnosticProbeCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = "run_diagnostic_probe"
            )
            String functionName,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<DiagnosticProbeId> allowedProbeIds,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True because the probe can inspect only the current generated case."
            )
            boolean caseBound,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean readOnly,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Always false. A diagnostic probe never changes backend state."
            )
            boolean actionExecuted
    ) {
        public DiagnosticProbeCapability {
            allowedProbeIds = List.copyOf(allowedProbeIds);
        }
    }

    public record LiveAiCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean enabledByConfiguration,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when live mode and the provider's non-secret "
                            + "routing prerequisites are configured. This does not "
                            + "claim successful authentication, provider reachability "
                            + "or health."
            )
            boolean requestRoutingConfigured,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean explicitConfirmationRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String modelId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            GeminiThinkingLevel thinkingLevel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String promptVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LiveBudgetCapability budget
    ) {
    }

    public record LiveBudgetCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int maxCollectionRounds,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int maxToolCallsTotal,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int maxToolCallsPerRound,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ToolBudgetCapability> maxCallsByTool,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            long hardDeadlineMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            long providerCallCapMs,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int dailyLiveRunLimit,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"process_local", "database_global"}
            )
            GlobalDailyLiveQuota.Scope dailyQuotaScope
    ) {
        public LiveBudgetCapability {
            maxCallsByTool = List.copyOf(maxCallsByTool);
        }
    }

    public record GeneratedCasesCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean enabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String contractVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String generatorVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String truthLabel,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean userSuppliedDataAccepted,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean requestLocalOnly,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> incidentFamilies,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> evidenceModes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> noiseLevels,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ToolName> allowedTools
    ) {
        public GeneratedCasesCapability {
            incidentFamilies = List.copyOf(incidentFamilies);
            evidenceModes = List.copyOf(evidenceModes);
            noiseLevels = List.copyOf(noiseLevels);
            allowedTools = List.copyOf(allowedTools);
        }
    }

    public record ToolBudgetCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ToolName tool,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int maxCallsPerInvestigation
    ) {
    }

    public record RetrievalCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            RunbookRetrievalBackend backend,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Explicit active profiles, or effective default "
                            + "profiles when none were explicit."
            )
            List<String> activeProfiles,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String modeDescription,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String limitation,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when the runtime selected the pgvector strategy. "
                            + "This does not claim that the corpus index is ready."
            )
            boolean vectorDatabaseBackendActive,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "Active embedding configuration, or null when "
                            + "fixture retrieval is active."
            )
            EmbeddingCapability activeEmbeddingProfile,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    nullable = true,
                    description = "Current corpus/index readiness reported by the "
                            + "active pgvector backend, or null for fixture retrieval."
            )
            VectorIndexCapability indexStatus
    ) {
        public RetrievalCapability {
            activeProfiles = List.copyOf(activeProfiles);
        }
    }

    public record VectorIndexCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean ready,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String corpusVersion,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
            long indexedChunks,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
            long currentChunks,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
            int expectedChunks
    ) {
    }

    public record EmbeddingCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = {"developer_api", "vertex_ai"}
            )
            String providerTransport,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String modelId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
            int dimensions,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String formatVersion,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    minimum = "-1",
                    maximum = "1"
            )
            double minimumSimilarity
    ) {
    }

    public record PromptCacheCapability(
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = "provider_implicit"
            )
            PromptCacheStrategy strategy,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean explicitCachingEnabled,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Cache hits are claimed only from provider-reported "
                            + "cached token metadata."
            )
            boolean cacheHitClaimsRequireProviderMetadata
    ) {
    }
}
