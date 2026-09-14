package dev.shirwac.incidentdetective.generated;

import org.springframework.stereotype.Service;

import java.util.Objects;

/** Generates a case and exposes the seed and variant identity required for replay. */
@Service
public final class GeneratedCaseGenerationService {

    private final GeneratedCaseFactory cases;
    private final GeneratedCaseSeedResolver seeds;

    public GeneratedCaseGenerationService(
            GeneratedCaseFactory cases,
            GeneratedCaseSeedResolver seeds
    ) {
        this.cases = Objects.requireNonNull(cases, "cases must not be null");
        this.seeds = Objects.requireNonNull(seeds, "seeds must not be null");
    }

    public GeneratedCaseGeneration generate(
            GeneratedCaseGenerationRequest request
    ) {
        Objects.requireNonNull(request, "request must not be null");
        GeneratedCaseSeed seed = seeds.resolve(request.seed());
        GeneratedCaseRequest replayRequest = request.replayRequest(seed);
        GeneratedCase generatedCase = cases.create(replayRequest);
        GeneratedCaseReceipt receipt = GeneratedCaseReceipt.from(
                GeneratedCaseFactory.GENERATOR_VERSION,
                seed,
                replayRequest,
                generatedCase
        );
        return new GeneratedCaseGeneration(generatedCase, receipt);
    }
}
