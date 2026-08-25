package dev.shirwac.incidentdetective.ai;

import java.util.List;
import java.util.Objects;

public record CollectionModelResult(
        List<CollectionToolCall> toolCalls,
        ModelCallMetadata metadata
) {
    public CollectionModelResult {
        toolCalls = List.copyOf(toolCalls);
        Objects.requireNonNull(metadata, "metadata must not be null");
    }
}
