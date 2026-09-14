import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import AgentIncidentThread from "./AgentIncidentThread";
import type { AdkAgentTurnResponse } from "./api/generated";

const scenarioId = "generated-catalog-cache-42";
const citedLogId = "log-config-change";

const completedResponse = {
  contract_version: "nordly-adk-turn-v3",
  run_id: "adk-run-42",
  session_id: "session-42",
  turn_id: "turn-42",
  mode: "adk_live_ai",
  truth_label: "Generated synthetic incident — real Google ADK investigation.",
  outcome: "completed",
  provider_route: {
    transport: "developer_api",
    authentication_mode: "api_key",
    location: null,
  },
  scenario: {
    scenario_id: scenarioId,
    title: "Generated storefront shows stale product information",
    description: "A request-local synthetic Nordly incident.",
    incident_started_at: "2026-09-01T09:51:00Z",
    time_window: {
      start: "2026-09-01T09:44:00Z",
      end: "2026-09-01T10:04:00Z",
    },
    affected_services: ["STOREFRONT", "CATALOG_SERVICE"],
    business_impact_summary: "127 synthetic product views returned stale data.",
    initial_symptoms: [],
    version: 1,
  },
  safety: {
    decision: "allowed",
    reason_code: "none",
    summary_sv: "Frågan får gå vidare.",
    summary_en: "The request may continue.",
  },
  runtime: {
    framework: "google_adk",
    framework_version: "1.7.0",
    agent_name: "nordly_incident_agent",
    model_id: "gemini-3.1-flash-lite",
    prompt_version: "nordly-adk-sequential-v2",
    session_service: "request_scoped_in_memory",
    delivery: "synchronous_post_run",
    event_source: "google_adk_runner",
    runner_invoked: true,
    streamed: false,
  },
  workflow: {
    type: "sequential_agent",
    expected_agent_order: ["nordly_evidence_agent", "nordly_diagnosis_agent"],
    observed_agent_order: ["nordly_evidence_agent", "nordly_diagnosis_agent"],
    evidence_handoff: "adk_function_response",
    final_response_author: "nordly_diagnosis_agent",
    completed_in_order: true,
  },
  events: [
    {
      sequence: 1,
      event_id: "event-call",
      invocation_id: "invocation-42",
      author: "nordly_evidence_agent",
      type: "tool_call",
      observed_at: "2026-09-01T10:00:01Z",
      final_response: false,
      content_withheld: false,
      text: null,
      function_calls: [
        {
          id: "call-42",
          name: "inspect_incident_evidence",
          arguments: { log_query: "catalog" },
        },
      ],
      function_responses: [],
      token_usage: null,
      provider_model_version: "gemini-3.1-flash-lite",
    },
    {
      sequence: 2,
      event_id: "event-result",
      invocation_id: "invocation-42",
      author: "nordly_evidence_agent",
      type: "tool_result",
      observed_at: "2026-09-01T10:00:02Z",
      final_response: false,
      content_withheld: false,
      text: null,
      function_calls: [],
      function_responses: [
        {
          id: "call-42",
          name: "inspect_incident_evidence",
          response: { status: "found" },
        },
      ],
      token_usage: null,
      provider_model_version: null,
    },
    {
      sequence: 3,
      event_id: "event-final",
      invocation_id: "invocation-42",
      author: "nordly_diagnosis_agent",
      type: "final_response",
      observed_at: "2026-09-01T10:00:03Z",
      final_response: true,
      content_withheld: true,
      text: null,
      function_calls: [],
      function_responses: [],
      token_usage: null,
      provider_model_version: "gemini-3.1-flash-lite",
    },
  ],
  tool_events: [
    {
      event_id: "operation-metrics",
      collection_round: 1,
      tool_name: "get_metrics",
      arguments: {},
      safe_summary: "Returned one metric.",
      evidence: [
        {
          evidence_type: "metric",
          evidence_id: "metric-stale",
          scenario_id: scenarioId,
          observed_at: "2026-09-01T10:00:00Z",
          display_summary: "9.4 percent of views were stale.",
          source_ref: "generated/metrics/stale",
          content: {
            metric_name: "catalog_stale_response_ratio",
            value: 0.094,
            unit: "ratio",
            labels: { service: "CATALOG_SERVICE" },
          },
        },
      ],
      runbook_retrieval: null,
    },
    {
      event_id: "operation-logs",
      collection_round: 1,
      tool_name: "search_logs",
      arguments: { query: "catalog" },
      safe_summary: "Returned two logs.",
      evidence: [
        {
          evidence_type: "log",
          evidence_id: citedLogId,
          scenario_id: scenarioId,
          observed_at: "2026-09-01T09:49:14Z",
          display_summary: "Invalidation was disabled.",
          source_ref: "generated/logs/config",
          content: {
            service: "CATALOG_SERVICE",
            level: "INFO",
            message: "Configuration changed for CATALOG_INVALIDATION_ENABLED.",
            attributes: { previous_value: "true", new_value: "false" },
          },
        },
        {
          evidence_type: "log",
          evidence_id: "log-noise",
          scenario_id: scenarioId,
          observed_at: "2026-09-01T09:48:00Z",
          display_summary: "An unrelated archive warning.",
          source_ref: "generated/logs/noise",
          content: {
            service: "ORDER_SERVICE",
            level: "WARN",
            message: "Archive warning recovered without customer impact.",
            attributes: {},
          },
        },
      ],
      runbook_retrieval: null,
    },
    {
      event_id: "operation-runbooks",
      collection_round: 1,
      tool_name: "retrieve_runbooks",
      arguments: { query: "cache invalidation" },
      safe_summary: "Returned one runbook passage.",
      evidence: [
        {
          evidence_type: "runbook",
          evidence_id: "runbook-cache",
          scenario_id: scenarioId,
          display_summary: "Compare cache age and source versions.",
          source_ref: "runbooks/cache#stale",
          content: {
            document_id: "rb-cache-invalidation",
            chunk_id: "stale-checkout-data",
            document_version: "1.0",
            text: "Inspect cache age and invalidation events before purging.",
          },
        },
      ],
      runbook_retrieval: {
        backend: "pgvector_exact_cosine",
        corpus_version: "runbook-corpus-v1",
        embedding_profile: {
          model_id: "gemini-embedding-2",
          dimensions: 768,
          format_version: "search-result-v1",
          minimum_similarity: 0.66,
        },
        query_embedding: {
          local_input_characters: 18,
          provider_billable_characters: null,
          provider_input_tokens: null,
          latency_ms: 369,
        },
        matches: [
          {
            rank: 1,
            evidence_id: "runbook-cache",
            cosine_similarity: 0.785,
            content_sha256: "sha256-cache",
          },
        ],
      },
    },
  ],
  diagnosis: {
    status: "diagnosed",
    root_cause_code: "CATALOG_CACHE_INVALIDATION_FAILURE",
    affected_service: "CATALOG_SERVICE",
    business_summary: "Kunder såg gamla priser eftersom katalogcachen inte uppdaterades efter releasen.",
    technical_summary: "Konfigurationen stängde av cache-invalideringen efter releasen.",
    claims: [
      {
        claim_code: "root_cause",
        claim_value_code: "CATALOG_CACHE_INVALIDATION_FAILURE",
        display_text: "Cache-invalideringen stängdes av.",
        evidence_ids: [citedLogId],
      },
    ],
    safe_next_step: {
      summary: "Låt en människa kontrollera konfigurationen innan den ändras.",
      requires_human_approval: true,
    },
  },
  verification: {
    diagnosis_schema_pass: true,
    ground_truth_schema_pass: true,
    citation_validity: { valid: true, unknown_evidence_ids: [] },
    evidence_precision: {
      applicable: true,
      supported_triples: 1,
      total_triples: 1,
      score: 1,
      citation_support: [
        {
          claim_code: "root_cause",
          claim_value_code: "CATALOG_CACHE_INVALIDATION_FAILURE",
          evidence_id: citedLogId,
          supported: true,
        },
      ],
    },
    claim_coverage: {
      applicable: true,
      matched_claim_count: 1,
      reference_claim_count: 1,
      score: 1,
    },
    diagnosis_correctness: {
      evaluated: true,
      diagnosis_applicable: true,
      root_cause_correct: true,
      affected_service_correct: true,
      abstention_correct: false,
    },
    hard_errors: [],
  },
  comparison: {
    expected_status: "diagnosed",
    expected_root_cause_code: "CATALOG_CACHE_INVALIDATION_FAILURE",
    expected_affected_service: "CATALOG_SERVICE",
    root_cause_correct: true,
    affected_service_correct: true,
    abstention_correct: false,
  },
  verification_event: {
    source: "deterministic_java_verifier",
    executed_at: "2026-09-01T10:00:04Z",
    schema_valid: true,
    citations_valid: true,
    factual_result_matches_ground_truth: true,
    agent_sequence_valid: true,
    evidence_handoff_valid: true,
    tool_boundary_valid: true,
    final_author_valid: true,
    answer_released: true,
    summary: "Java accepted the verified result.",
  },
  receipt: {
    model_calls: 2,
    adk_tool_calls: 1,
    read_operations: 3,
    embedding_calls: 1,
    write_tools_available: false,
    action_executed: false,
    human_approval_required: true,
    registered_tools: ["inspect_incident_evidence"],
    token_usage: {
      input_tokens: 400,
      cached_input_tokens: null,
      uncached_input_tokens: null,
      candidate_output_tokens: 100,
      thinking_output_tokens: null,
      output_tokens: 100,
      tool_use_prompt_tokens: null,
      total_tokens: 500,
    },
    estimated_cost_usd: 0.003748,
    estimated_cost_basis: "Generation list-price estimate only.",
    total_latency_ms: 8690,
  },
  limitations: ["Synthetic request-local data only."],
} satisfies AdkAgentTurnResponse;

const blockedResponse = {
  ...completedResponse,
  run_id: "blocked-run-1",
  session_id: null,
  outcome: "blocked_before_ai",
  provider_route: null,
  scenario: null,
  safety: {
    decision: "blocked",
    reason_code: "employee_compensation_request",
    summary_sv: "Frågan stoppades eftersom privat lön inte får hämtas.",
    summary_en: "The request was stopped because private pay may not be retrieved.",
  },
  runtime: {
    ...completedResponse.runtime,
    framework: "not_invoked",
    framework_version: "not_invoked",
    session_service: "none",
    delivery: "blocked_before_provider",
    event_source: "java_safety_gate",
    runner_invoked: false,
  },
  events: [],
  workflow: null,
  tool_events: [],
  diagnosis: null,
  verification: null,
  comparison: null,
  verification_event: null,
  receipt: {
    ...completedResponse.receipt,
    model_calls: 0,
    adk_tool_calls: 0,
    read_operations: 0,
    embedding_calls: 0,
    registered_tools: [],
    token_usage: null,
    estimated_cost_usd: null,
    total_latency_ms: 0,
  },
} satisfies AdkAgentTurnResponse;

function jsonResponse(body: unknown): Response {
  return { ok: true, status: 200, json: async () => body } as Response;
}

function problemResponse(status: number, code: string): Response {
  return {
    ok: false,
    status,
    json: async () => ({
      title: "Live AI unavailable",
      detail: "Sanitised backend detail.",
      status,
      code,
    }),
  } as Response;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("Nordly ADK incident thread", () => {
  it("submits one confirmed turn and shows no fabricated progress before the receipt returns", async () => {
    let release: ((response: Response) => void) | undefined;
    const pending = new Promise<Response>((resolve) => { release = resolve; });
    const fetchMock = vi.fn(
      (_input: RequestInfo | URL, _init?: RequestInit) => pending,
    );
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<AgentIncidentThread locale="sv" active />);
    await user.click(screen.getByRole("button", { name: "Skicka larmet till agenten" }));

    expect(screen.getByText("Kör en kontrollerad agentutredning…")).toBeVisible();
    expect(screen.queryByText("Google ADK 1.7.0")).not.toBeInTheDocument();
    expect(screen.queryByText("RAG hittade relevant driftkunskap")).not.toBeInTheDocument();

    const [, init] = fetchMock.mock.calls[0];
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({
      seed: 42,
      incident_family: "catalog_cache_invalidation",
      evidence_mode: "diagnostic",
      noise_level: "low",
      message: "Undersök larmet. Läs bara tillåtna källor och förklara vad bevisen faktiskt stödjer.",
      confirm_live_ai: true,
    });

    release?.(jsonResponse(completedResponse));
    expect(await screen.findByText(completedResponse.diagnosis.business_summary)).toBeVisible();
  });

  it("reveals real ADK events, cited logs and RAG only after one disclosure", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(completedResponse)));
    const user = userEvent.setup();
    const { container } = render(<AgentIncidentThread locale="sv" active />);

    await user.click(screen.getByRole("button", { name: "Skicka larmet till agenten" }));
    expect(await screen.findByText(completedResponse.diagnosis.business_summary)).toBeVisible();
    expect(screen.queryByText("Google ADK 1.7.0")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Så byggdes svaret" }));
    expect(screen.getByText("Google ADK 1.7.0")).toBeVisible();
    expect(screen.getByText(/inte modellens privata resonemang/i)).toBeVisible();
    expect(screen.getByText("RAG hittade relevant driftkunskap")).toBeVisible();
    expect(screen.getByText("gemini-embedding-2")).toBeVisible();
    expect(screen.getByText("768 dimensioner")).toBeVisible();
    expect(screen.getByText(/78,5/)).toBeVisible();

    const logs = container.querySelectorAll(".agent-log-list li");
    expect(logs).toHaveLength(2);
    expect(logs[0]).toHaveAttribute("data-cited", "true");
    expect(logs[1]).toHaveAttribute("data-cited", "false");
  });

  it("shows a truthful zero-use receipt when Java blocks the request before ADK", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(blockedResponse)));
    const user = userEvent.setup();
    render(<AgentIncidentThread locale="sv" active />);

    await user.click(screen.getByRole("button", { name: /privat lönefråga/i }));
    await user.click(screen.getByRole("button", { name: "Skicka larmet till agenten" }));

    expect(await screen.findByRole("heading", { name: /säkerhetsgränsen stoppade frågan/i })).toBeVisible();
    expect(screen.getByText("0 Gemini · 0 ADK · 0 tools · 0 embeddings · 0 actions")).toBeVisible();
    expect(screen.queryByText("Google ADK 1.7.0")).not.toBeInTheDocument();
  });

  it.each(["LIVE_AI_DISABLED", "LIVE_AI_NOT_CONFIGURED"])(
    "states that no investigation ran when the start gate returns %s",
    async (code) => {
      vi.stubGlobal("fetch", vi.fn(async () => problemResponse(503, code)));
      const user = userEvent.setup();
      render(<AgentIncidentThread locale="sv" active />);

      await user.click(screen.getByRole("button", { name: "Skicka larmet till agenten" }));

      expect(await screen.findByRole("heading", { name: "Ingen AI-utredning utfördes." })).toBeVisible();
      expect(screen.getByText(/Google ADK, Gemini, verktyg, RAG\/embeddings och actions startade aldrig/i)).toBeVisible();
      expect(screen.getByText(`Backendkod: ${code}`)).toBeVisible();
      expect(screen.getByText("0 Gemini · 0 ADK · 0 tools · 0 embeddings · 0 actions")).toBeVisible();
      expect(screen.queryByRole("button", { name: "Så byggdes svaret" })).not.toBeInTheDocument();
    },
  );

  it("does not claim zero calls when a provider timeout stops an admitted run", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => problemResponse(504, "MODEL_PROVIDER_TIMEOUT")));
    const user = userEvent.setup();
    render(<AgentIncidentThread locale="sv" active />);

    await user.click(screen.getByRole("button", { name: "Skicka larmet till agenten" }));

    expect(await screen.findByRole("heading", {
      name: "AI-utredningen startade, men inget svar släpptes.",
    })).toBeVisible();
    expect(screen.getByText("Backendkod: MODEL_PROVIDER_TIMEOUT")).toBeVisible();
    expect(screen.queryByText("0 Gemini · 0 ADK · 0 tools · 0 embeddings · 0 actions")).not.toBeInTheDocument();
  });
});
