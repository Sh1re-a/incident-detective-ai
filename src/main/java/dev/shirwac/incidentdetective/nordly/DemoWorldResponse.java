package dev.shirwac.incidentdetective.nordly;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Public, synthetic company world used by the Nordly demo.")
public record DemoWorldResponse(
        String contractVersion,
        String truthLabel,
        String truthLabelEn,
        CompanyProfile company,
        List<ServiceProfile> services,
        List<DemoQuestion> questions,
        List<RagExample> ragExamples,
        String featuredScenarioId,
        CorpusSummary corpus
) {
    public static final String CONTRACT_VERSION = "nordly-demo-world-v1";

    public DemoWorldResponse {
        services = services == null ? null : List.copyOf(services);
        questions = questions == null ? null : List.copyOf(questions);
        ragExamples = ragExamples == null ? null : List.copyOf(ragExamples);
    }

    public record CompanyProfile(
            String id,
            String displayName,
            String storefrontName,
            String description,
            String descriptionEn,
            List<String> markets,
            List<String> marketsEn,
            String industry,
            String industryEn
    ) {
        public CompanyProfile {
            markets = markets == null ? null : List.copyOf(markets);
            marketsEn = marketsEn == null ? null : List.copyOf(marketsEn);
        }
    }

    public record ServiceProfile(
            String id,
            String displayName,
            String displayNameEn,
            String role,
            String roleEn
    ) {
    }

    public record DemoQuestion(
            String id,
            String promptSv,
            String promptEn,
            String customerContextSv,
            String customerContextEn,
            String outcome,
            int estimatedSeconds,
            boolean replayAvailable
    ) {
    }

    public record RagExample(
            String id,
            String category,
            String promptSv,
            String promptEn,
            String expectedBoundary
    ) {
    }

    public record CorpusSummary(
            String version,
            int documentCount,
            int chunkCount,
            int approvedDocuments,
            int deprecatedDocuments,
            int untrustedDocuments
    ) {
    }
}
