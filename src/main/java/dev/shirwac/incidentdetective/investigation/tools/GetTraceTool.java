package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.domain.evidence.TraceEvidence;
import dev.shirwac.incidentdetective.investigation.InvestigationData;
import dev.shirwac.incidentdetective.investigation.InvestigationDataCatalog;
import dev.shirwac.incidentdetective.investigation.InvestigationScenarioNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public final class GetTraceTool {

    private static final Comparator<TraceEvidence> TRACE_ORDER =
            Comparator.comparing(TraceEvidence::observedAt)
                    .thenComparing(TraceEvidence::evidenceId);

    private final InvestigationDataCatalog catalog;
    private final Validator validator;

    public GetTraceTool(InvestigationDataCatalog catalog, Validator validator) {
        this.catalog = catalog;
        this.validator = validator;
    }

    public ToolName name() {
        return ToolName.GET_TRACE;
    }

    public GetTraceResult execute(
            String scenarioId,
            GetTraceArguments arguments
    ) {
        validate(arguments);
        InvestigationData data = catalog.findById(scenarioId)
                .orElseThrow(() -> new InvestigationScenarioNotFoundException(scenarioId));
        String currentScenarioId = data.scenario().scenarioId();

        return data.evidenceInventory().stream()
                .filter(TraceEvidence.class::isInstance)
                .map(TraceEvidence.class::cast)
                .filter(trace -> currentScenarioId.equals(trace.scenarioId()))
                .filter(trace -> arguments.traceId()
                        .equals(trace.content().traceId()))
                .min(TRACE_ORDER)
                .map(trace -> GetTraceResult.found(arguments.traceId(), trace))
                .orElseGet(() -> GetTraceResult.missing(arguments.traceId()));
    }

    private void validate(GetTraceArguments arguments) {
        if (arguments == null) {
            throw new InvalidToolArgumentsException(name(), "arguments are required");
        }

        Set<ConstraintViolation<GetTraceArguments>> violations =
                validator.validate(arguments);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidToolArgumentsException(name(), details);
        }
    }
}
