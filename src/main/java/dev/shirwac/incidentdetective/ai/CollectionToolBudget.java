package dev.shirwac.incidentdetective.ai;

import dev.shirwac.incidentdetective.investigation.tools.ToolName;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The collection capacity that is still available for one investigation run.
 */
public record CollectionToolBudget(
        int remainingCallsTotal,
        int maxCallsThisRound,
        Map<ToolName, Integer> remainingCallsByTool,
        Set<String> discoveredTraceIds
) {

    public CollectionToolBudget {
        if (remainingCallsTotal < 0 || maxCallsThisRound < 0) {
            throw new IllegalArgumentException("Tool-call budgets cannot be negative");
        }
        if (maxCallsThisRound > remainingCallsTotal) {
            throw new IllegalArgumentException(
                    "Round budget cannot exceed the remaining total budget"
            );
        }
        EnumMap<ToolName, Integer> normalized = new EnumMap<>(ToolName.class);
        normalized.putAll(remainingCallsByTool);
        for (ToolName toolName : ToolName.values()) {
            int remaining = normalized.getOrDefault(toolName, 0);
            if (remaining < 0) {
                throw new IllegalArgumentException(
                        "Per-tool budgets cannot be negative"
                );
            }
            normalized.put(toolName, remaining);
        }
        remainingCallsByTool = Map.copyOf(normalized);
        discoveredTraceIds = Set.copyOf(discoveredTraceIds);
    }

    public List<ToolName> allowedTools() {
        if (remainingCallsTotal == 0 || maxCallsThisRound == 0) {
            return List.of();
        }
        return Arrays.stream(ToolName.values())
                .filter(toolName -> remainingCallsByTool.get(toolName) > 0)
                .filter(toolName -> toolName != ToolName.GET_TRACE
                        || !discoveredTraceIds.isEmpty())
                .toList();
    }

    public Map<String, Object> promptView() {
        Map<String, Integer> remainingByWireName = new LinkedHashMap<>();
        for (ToolName toolName : ToolName.values()) {
            remainingByWireName.put(
                    toolName.wireValue(),
                    remainingCallsByTool.get(toolName)
            );
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("remaining_calls_total", remainingCallsTotal);
        view.put("max_calls_this_round", maxCallsThisRound);
        view.put("remaining_calls_by_tool", remainingByWireName);
        view.put(
                "allowed_tools",
                allowedTools().stream().map(ToolName::wireValue).toList()
        );
        view.put("discovered_trace_ids", discoveredTraceIds);
        return Map.copyOf(view);
    }
}
