package dev.shirwac.incidentdetective.planning;

/** Natural-language input for one synthetic incident plan proposal. */
public record IncidentPlanningRequest(String instruction) {

    public static final int MAX_INSTRUCTION_LENGTH = 500;

    public IncidentPlanningRequest {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("instruction must not be blank");
        }
        instruction = instruction.strip();
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new IllegalArgumentException(
                    "instruction must be at most " + MAX_INSTRUCTION_LENGTH
                            + " characters"
            );
        }
    }
}
