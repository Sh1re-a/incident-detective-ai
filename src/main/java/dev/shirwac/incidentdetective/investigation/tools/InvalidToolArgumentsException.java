package dev.shirwac.incidentdetective.investigation.tools;

public final class InvalidToolArgumentsException extends RuntimeException {

    private final ToolName toolName;

    InvalidToolArgumentsException(ToolName toolName, String details) {
        super("Invalid " + toolName.wireValue() + " arguments: " + details);
        this.toolName = toolName;
    }

    public ToolName toolName() {
        return toolName;
    }
}
