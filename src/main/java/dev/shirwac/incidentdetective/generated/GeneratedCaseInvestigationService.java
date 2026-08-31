package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.live.LiveInvestigationResult;
import dev.shirwac.incidentdetective.live.LiveInvestigationService;
import org.springframework.stereotype.Service;

@Service
public final class GeneratedCaseInvestigationService {

    private final GeneratedCaseFactory cases;
    private final LiveInvestigationService investigations;

    public GeneratedCaseInvestigationService(
            GeneratedCaseFactory cases,
            LiveInvestigationService investigations
    ) {
        this.cases = cases;
        this.investigations = investigations;
    }

    public GeneratedCaseRunResult investigate(GeneratedCaseLiveRequest request) {
        GeneratedCase generated = cases.create(request.generatedCaseRequest());
        LiveInvestigationResult investigation = investigations.investigateGenerated(
                generated.investigationData(),
                generated.hiddenGroundTruth(),
                request.liveRequest()
        );
        return new GeneratedCaseRunResult(
                GeneratedCaseRunResult.CONTRACT_VERSION,
                new GeneratedCaseRunResult.GenerationMetadata(
                        GeneratedCaseFactory.GENERATOR_VERSION,
                        request.seed(),
                        request.evidenceMode(),
                        request.noiseLevel()
                ),
                investigation
        );
    }
}
