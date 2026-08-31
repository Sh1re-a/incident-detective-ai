package dev.shirwac.incidentdetective.capabilities;

import dev.shirwac.incidentdetective.ai.GeminiThinkingLevel;
import dev.shirwac.incidentdetective.investigation.tools.RunbookRetrievalBackend;
import dev.shirwac.incidentdetective.investigation.tools.ToolName;
import dev.shirwac.incidentdetective.live.GlobalDailyLiveQuota;
import dev.shirwac.incidentdetective.live.PromptCacheStrategy;
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
        List<ModeCapability> modes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<ToolCapability> tools,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LiveAiCapability liveAi,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GeneratedCasesCapability generatedCases,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RetrievalCapability retrieval,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PromptCacheCapability promptCache
) {
    public static final String CONTRACT_VERSION = "capabilities-v2";

    public CapabilitiesResponse {
        modes = List.copyOf(modes);
        tools = List.copyOf(tools);
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

    public record LiveAiCapability(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean enabledByConfiguration,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Whether provider credentials are configured; "
                            + "credentials are never returned."
            )
            boolean credentialsConfigured,
            @Schema(
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when the local request prerequisites are "
                            + "configured. This does not claim provider reachability "
                            + "or health."
            )
            boolean requestConfigured,
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
            List<String> evidenceModes,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<String> noiseLevels,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            List<ToolName> allowedTools
    ) {
        public GeneratedCasesCapability {
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
            EmbeddingCapability activeEmbeddingProfile
    ) {
        public RetrievalCapability {
            activeProfiles = List.copyOf(activeProfiles);
        }
    }

    public record EmbeddingCapability(
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
