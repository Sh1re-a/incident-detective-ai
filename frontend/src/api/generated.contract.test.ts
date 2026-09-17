import { describe, expect, it } from "vitest";
import {
  INCIDENT_LAB_PLAN_INSTRUCTION_MAX_LENGTH,
} from "./generated";
import type {
  AdkAgentTurnResponse,
  AdkRuntimeEvent,
  AdkWorkflowReceipt,
  DiagnosticProbeReceipt,
  IncidentLabGenerationReceipt,
} from "./generated";

const workflow = {
  type: "sequential_agent",
  expected_agent_order: ["nordly_evidence_agent", "nordly_diagnosis_agent"],
  observed_agent_order: ["nordly_evidence_agent", "nordly_diagnosis_agent"],
  evidence_handoff: "adk_function_response",
  final_response_author: "nordly_diagnosis_agent",
  completed_in_order: true,
} satisfies AdkWorkflowReceipt;

const event = {
  sequence: 1,
  event_id: "event-1",
  invocation_id: "invocation-1",
  author: "nordly_evidence_agent",
  type: "tool_call",
  observed_at: "2026-09-17T10:00:00Z",
  final_response: false,
  content_withheld: false,
  text: null,
  function_calls: [{
    id: "call-1",
    name: "inspect_incident_evidence",
    arguments: { diagnostic_probe: "service_health" },
  }],
  function_responses: [],
  token_usage: null,
  provider_model_version: "gemini-3.1-flash-lite",
} satisfies AdkRuntimeEvent;

const diagnosticProbe = {
  scenario_id: "generated-case-1",
  probe_id: "service_health",
  outcome: "observed",
  safe_summary: "Inspected request-local service health signals.",
  findings: [{
    code: "service_health",
    subject: "CATALOG_SERVICE",
    status: "degraded",
    detail: "One synthetic non-success trace span was observed.",
    evidence_ids: ["trace-1"],
  }],
  truncated: false,
  duration_ms: 2,
  read_only: true,
  action_executed: false,
} satisfies DiagnosticProbeReceipt;

const blockedMode = "blocked_before_ai" satisfies AdkAgentTurnResponse["mode"];
const explicitSeedOrigin = "explicit" satisfies IncidentLabGenerationReceipt["seed_origin"];

describe("hand-maintained Incident Lab API types", () => {
  it("keeps the OpenAPI-backed input boundary", () => {
    expect(INCIDENT_LAB_PLAN_INSTRUCTION_MAX_LENGTH).toBe(500);
  });

  it("represents blocked mode and explicit seed provenance exactly", () => {
    expect(blockedMode).toBe("blocked_before_ai");
    expect(explicitSeedOrigin).toBe("explicit");
  });

  it("keeps workflow, event, and diagnostic probe receipts available to the UI", () => {
    expect(workflow.completed_in_order).toBe(true);
    expect(event.function_calls[0]?.arguments).toEqual({
      diagnostic_probe: "service_health",
    });
    expect(diagnosticProbe.findings[0]?.evidence_ids).toEqual(["trace-1"]);
  });
});
