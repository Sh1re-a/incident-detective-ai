package dev.shirwac.incidentdetective.generated;

import dev.shirwac.incidentdetective.domain.evidence.Evidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.rag.ClasspathRunbookCorpus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Builds one immutable request-local case and attaches the shared runbook corpus. */
@Service
public final class GeneratedCaseFactory {

    public static final String GENERATOR_VERSION = "payment-timeout-generator-v1";

    private final ClasspathRunbookCorpus runbooks;
    private final PaymentTimeoutGeneratedCaseGenerator generator =
            new PaymentTimeoutGeneratedCaseGenerator();

    public GeneratedCaseFactory(ClasspathRunbookCorpus runbooks) {
        this.runbooks = runbooks;
    }

    public GeneratedCase create(GeneratedCaseRequest request) {
        GeneratedCase generated = generator.generate(request);
        List<Evidence> evidence = new ArrayList<>(
                generated.investigationData().evidenceInventory()
        );
        runbooks.entries().stream()
                .map(entry -> entry.asEvidence(generated.scenario().scenarioId()))
                .forEach(evidence::add);
        return new GeneratedCase(
                generated.scenario(),
                new InvestigationData(generated.scenario(), List.copyOf(evidence)),
                generated.hiddenGroundTruth()
        );
    }
}
