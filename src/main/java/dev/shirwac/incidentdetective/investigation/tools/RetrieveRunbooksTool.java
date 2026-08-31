package dev.shirwac.incidentdetective.investigation.tools;

import dev.shirwac.incidentdetective.investigation.InvestigationData;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public final class RetrieveRunbooksTool {

    private final RunbookRetrievalStrategy retrieval;
    private final Validator validator;

    public RetrieveRunbooksTool(
            RunbookRetrievalStrategy retrieval,
            Validator validator
    ) {
        this.retrieval = retrieval;
        this.validator = validator;
    }

    public ToolName name() {
        return ToolName.RETRIEVE_RUNBOOKS;
    }

    public RetrieveRunbooksResult execute(
            String scenarioId,
            RetrieveRunbooksArguments arguments
    ) {
        validate(arguments);
        return retrieval.retrieve(scenarioId, arguments);
    }

    public RetrieveRunbooksResult execute(
            InvestigationData data,
            RetrieveRunbooksArguments arguments
    ) {
        validate(arguments);
        return retrieval.retrieve(data, arguments);
    }

    public String safeModeDescription() {
        return retrieval.safeModeDescription();
    }

    public String limitation() {
        return retrieval.limitation();
    }

    private void validate(RetrieveRunbooksArguments arguments) {
        if (arguments == null) {
            throw new InvalidToolArgumentsException(name(), "arguments are required");
        }
        Set<ConstraintViolation<RetrieveRunbooksArguments>> violations =
                validator.validate(arguments);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(violation -> violation.getPropertyPath()
                            + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new InvalidToolArgumentsException(name(), details);
        }
        if (tokens(arguments.query()).isEmpty()) {
            throw new InvalidToolArgumentsException(
                    name(),
                    "query must contain a searchable letter or digit"
            );
        }
    }

    private List<String> tokens(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 3)
                .distinct()
                .toList();
    }

}
