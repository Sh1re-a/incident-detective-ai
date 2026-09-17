import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import IncidentLabPortal from "./IncidentLabPortal";

const approvedPlan = {
  contract_version: "incident-plan-v1",
  incident_family: "catalog_cache_invalidation",
  severity: "high",
  affected_services: ["catalog_service"],
  summary: "A bounded synthetic catalogue incident.",
  synthetic_only: true,
  write_actions_allowed: false,
  human_approval_required: true,
};

const readyPlan = {
  contract_version: "incident-lab-plan-v1",
  outcome: "plan_ready",
  delivery: "synchronous_post_run",
  truth_label: "Real Gemini proposal.",
  safety: {
    decision: "allowed",
    reason_code: "none",
    summary_sv: "Planen får valideras.",
    summary_en: "The plan may be validated.",
  },
  proposal: {
    status: "candidate",
    summary: "A bounded synthetic catalogue incident.",
    incident_family: "catalog_cache_invalidation",
    requested_severity: "high",
    affected_services: ["catalog_service"],
    requested_blast_radius: "single_service",
  },
  java_validation: {
    decision: "APPROVED",
    plan: approvedPlan,
    adjustments: [],
    rejection: null,
  },
  provider_receipt: {
    transport: "developer_api",
    model: "gemini-3.1-flash-lite",
    provider_response_id: "planner-response",
    token_usage: null,
    latency_ms: 240,
  },
  limitations: [],
};

const noAlarmRun = {
  contract_version: "incident-lab-run-v3",
  outcome: "no_alarm",
  delivery: "synchronous_post_run",
  truth_label: "Backend-generated synthetic telemetry.",
  answer_state: "not_started",
  business_response: {
    headline: "Inget larm behövde utredas",
    what_happened: "Testsignalen höll sig under den fasta tröskeln.",
    impact: "Ingen syntetisk kundpåverkan bekräftades.",
    what_is_known: ["Java utvärderade den returnerade signalen."],
    what_remains_unknown: [],
    safe_next_step: "Fortsätt observera testmiljön.",
    certainty: "Verifierat testutfall",
    human_approval_required: true,
  },
  developer_response: {
    summary: "No alarm.",
    root_cause_code: null,
    affected_service: null,
    verified_claims: [],
    highlighted_log_evidence_ids: [],
    missing_evidence_codes: [],
    failed_verification_checks: [],
    next_read: "Observe.",
  },
  action_receipt: {
    status: "not_started",
    read_operations: 0,
    write_tools_available: false,
    action_executed: false,
    human_approval_required: true,
    proposed_next_step: null,
    summary: "No action.",
  },
  localized_presentations: {
    sv: {
      business_response: {
        headline: "Inget larm behövde utredas",
        what_happened: "Testsignalen höll sig under den fasta tröskeln.",
        impact: "Ingen syntetisk kundpåverkan bekräftades.",
        what_is_known: ["Java utvärderade den returnerade signalen."],
        what_remains_unknown: [],
        safe_next_step: "Fortsätt observera testmiljön.",
        certainty: "Verifierat testutfall",
        human_approval_required: true,
      },
      developer_response: {
        summary: "Inget larm.",
        root_cause_code: null,
        affected_service: null,
        verified_claims: [],
        highlighted_log_evidence_ids: [],
        missing_evidence_codes: [],
        failed_verification_checks: [],
        next_read: "Observera.",
      },
      action_receipt: {
        status: "not_started",
        read_operations: 0,
        write_tools_available: false,
        action_executed: false,
        human_approval_required: true,
        proposed_next_step: null,
        summary: "Ingen åtgärd.",
      },
    },
    en: {
      business_response: {
        headline: "No alert needed investigation",
        what_happened: "The test signal stayed below the threshold.",
        impact: "No synthetic customer impact was confirmed.",
        what_is_known: ["Java evaluated the returned signal."],
        what_remains_unknown: [],
        safe_next_step: "Keep observing the test environment.",
        certainty: "Verified test outcome",
        human_approval_required: true,
      },
      developer_response: {
        summary: "No alarm.",
        root_cause_code: null,
        affected_service: null,
        verified_claims: [],
        highlighted_log_evidence_ids: [],
        missing_evidence_codes: [],
        failed_verification_checks: [],
        next_read: "Observe.",
      },
      action_receipt: {
        status: "not_started",
        read_operations: 0,
        write_tools_available: false,
        action_executed: false,
        human_approval_required: true,
        proposed_next_step: null,
        summary: "No action.",
      },
    },
  },
  plan: approvedPlan,
  generation_receipt: {
    generator_version: "nordly-incident-generator-v2",
    seed: 42,
    seed_origin: "server_generated",
    incident_family: "catalog_cache_invalidation",
    evidence_mode: "insufficient_evidence",
    noise_level: "low",
    scenario_id: "generated-case",
    variant: { variant_id: "variant-1", fingerprint: "fingerprint-1" },
  },
  scenario: {
    scenario_id: "generated-case",
    title: "Synthetic catalogue case",
    description: "A request-local test.",
    incident_started_at: "2026-09-15T08:00:00Z",
    time_window: { start: "2026-09-15T08:00:00Z", end: "2026-09-15T08:10:00Z" },
    affected_services: ["CATALOG_SERVICE"],
    business_impact_summary: "Synthetic only.",
    initial_symptoms: [],
    version: 1,
  },
  backend_logs: [],
  alarm_receipt: null,
  agent_turn: null,
  limitations: [],
};

const liveStatus = {
  contract_version: "live-ai-status-v1",
  live_state: "available",
  reason_code: "ready",
  resets_at: null,
  retry_after_seconds: null,
  replay_available: false,
  daily_cost_guard_active: true,
  quota_scope: "database_global",
};

const replayUnavailable = {
  contract_version: "incident-lab-replay-availability-v1",
  available: false,
  replay_contract_version: "incident-lab-replay-v1",
  truth_label: "Ingen replay.",
  truth_label_en: "No replay.",
  reason_code: "golden_recording_not_captured",
};

function response(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", ...init.headers },
    ...init,
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("Incident Lab recruiter portal", () => {
  it("waits for explicit approval and submits only the Java-approved plan", async () => {
    const requests: Array<{ path: string; body?: Record<string, unknown> }> = [];
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      const body = init?.body ? JSON.parse(String(init.body)) as Record<string, unknown> : undefined;
      requests.push({ path, body });
      if (path.endsWith("/api/v1/live-ai/status")) return response(liveStatus);
      if (path.endsWith("/api/v1/incident-lab/recorded-replay")) return response(replayUnavailable);
      if (path.endsWith("/api/v1/incident-lab/plans")) return response(readyPlan);
      if (path.endsWith("/api/v1/incident-lab/runs")) return response(noAlarmRun);
      throw new Error(`Unexpected request: ${path}`);
    }));

    const user = userEvent.setup();
    render(<IncidentLabPortal locale="sv" active />);
    await screen.findByText("Live AI tillgänglig");
    await user.click(screen.getByRole("button", { name: /Låt AI:n föreslå testfallet/ }));

    expect(await screen.findByRole("heading", { name: "AI:n föreslog. Java bestämde gränserna." })).toBeVisible();
    expect(requests.filter(({ path }) => path.endsWith("/api/v1/incident-lab/runs"))).toHaveLength(0);

    await user.click(screen.getByRole("button", { name: /Iscensätt larmet/ }));
    expect(await screen.findByRole("heading", { name: "Inget larm behövde utredas" })).toBeVisible();

    const runRequest = requests.find(({ path }) => path.endsWith("/api/v1/incident-lab/runs"));
    expect(runRequest?.body).toEqual({ plan: approvedPlan, confirm_live_ai: true });
    expect(runRequest?.body).not.toHaveProperty("seed");
    expect(runRequest?.body).not.toHaveProperty("evidence_mode");
  });

  it("keeps a blocked planning result visible and never starts the run", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/api/v1/live-ai/status")) return response(liveStatus);
      if (path.endsWith("/api/v1/incident-lab/recorded-replay")) return response(replayUnavailable);
      if (path.endsWith("/api/v1/incident-lab/plans")) {
        return response({
          ...readyPlan,
          outcome: "blocked_before_ai",
          proposal: null,
          java_validation: null,
          provider_receipt: null,
          safety: {
            decision: "blocked",
            reason_code: "real_environment_not_allowed",
            summary_sv: "Begäran stoppades före AI.",
            summary_en: "The request was stopped before AI.",
          },
        });
      }
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<IncidentLabPortal locale="sv" active />);
    await screen.findByText("Live AI tillgänglig");
    await user.click(screen.getByRole("button", { name: /Låt AI:n föreslå testfallet/ }));

    expect(await screen.findByRole("heading", { name: "Bra. Säkerhetsgränsen stoppade begäran." })).toBeVisible();
    expect(screen.getByText("Begäran stoppades före AI.")).toBeVisible();
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith("/api/v1/incident-lab/runs"))).toBe(false);
  });

  it("fails closed after a refreshed live-status check fails", async () => {
    let statusCalls = 0;
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/api/v1/live-ai/status")) {
        statusCalls += 1;
        if (statusCalls === 1) return response(liveStatus);
        throw new Error("status unavailable");
      }
      if (path.endsWith("/api/v1/incident-lab/recorded-replay")) return response(replayUnavailable);
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const { rerender } = render(<IncidentLabPortal locale="sv" active />);
    await screen.findByText("Live AI tillgänglig");
    expect(screen.getByLabelText("Beskriv ett syntetiskt driftproblem")).toBeEnabled();

    rerender(<IncidentLabPortal locale="sv" active={false} />);
    rerender(<IncidentLabPortal locale="sv" active />);

    expect(await screen.findByText("Live-status kunde inte bekräftas")).toBeVisible();
    expect(screen.getByLabelText("Beskriv ett syntetiskt driftproblem")).toBeDisabled();
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith("/api/v1/incident-lab/plans"))).toBe(false);
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith("/api/v1/incident-lab/runs/recorded-replay"))).toBe(false);
  });

  it("offers replay explicitly when live budget is exhausted and labels zero current AI", async () => {
    const replayAvailability = { ...replayUnavailable, available: true, reason_code: "ready" };
    const replayResult = {
      contract_version: "incident-lab-replay-v1",
      replay_id: "golden-1",
      playback_id: "playback-1",
      mode: "recorded_replay",
      delivery: "synchronous_recorded_playback",
      truth_label: "Förinspelad körning.",
      truth_label_en: "Recorded run.",
      served_at: "2026-09-15T09:00:00Z",
      recorded_instruction: "Recorded synthetic case",
      recorded_instruction_locale: "sv",
      recorded_plan: readyPlan,
      recorded_run: noAlarmRun,
      provenance: {
        fixture_version: "v1",
        recording_source: "captured_public_api",
        recorded_at: "2026-09-15T08:00:00Z",
        source_content_git_sha: "abcdef1234567890",
        runtime_build_git_sha: null,
        runtime_build_identity_verified: false,
        resource_sha256: "1234567890abcdef",
        resource_sha256_verified_at_startup: true,
        original_plan_contract_version: "incident-lab-plan-v1",
        original_run_contract_version: "incident-lab-run-v3",
      },
      playback_receipt: {
        provider_calls: 0,
        model_calls: 0,
        adk_tool_calls: 0,
        read_operations: 0,
        embedding_calls: 0,
        vector_search_executed: false,
        live_quota_consumed: false,
        write_tools_available: false,
        action_executed: false,
        estimated_cost_usd: null,
        cost_status: "not_incurred",
      },
      limitations: [],
    };
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith("/api/v1/live-ai/status")) {
        return response({ ...liveStatus, live_state: "daily_budget_exhausted", replay_available: true });
      }
      if (path.endsWith("/api/v1/incident-lab/recorded-replay")) return response(replayAvailability);
      if (path.endsWith("/api/v1/incident-lab/runs/recorded-replay")) return response(replayResult);
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<IncidentLabPortal locale="sv" active />);
    expect(await screen.findByText("Dagens livekörningar är slut")).toBeVisible();
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith("/api/v1/incident-lab/runs/recorded-replay"))).toBe(false);
    await user.click(screen.getByRole("button", { name: "Se verifierad förinspelad körning" }));

    expect((await screen.findAllByRole("heading", { name: "Inget larm behövde utredas" }))[0]).toBeVisible();
    expect(screen.getByText("Förinspelad körning.")).toBeVisible();
    expect(screen.queryByText("FÖRINSPELAD KÖRNING · INGEN AI KÖRS NU")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Visa tekniska bevis" }));
    expect(screen.getAllByText("Förinspelad körning.")).toHaveLength(2);
    expect(screen.queryByText("FÖRINSPELAD KÖRNING · INGEN AI KÖRS NU")).not.toBeInTheDocument();
    expect(screen.getByText(/0 provideranrop · 0 modellanrop · 0 embeddings/)).toBeVisible();
    expect(screen.getByText(/Källinnehåll abcdef123456… · runtime-build ej verifierad · checksumma verifierad vid start/)).toBeVisible();
  });
});
