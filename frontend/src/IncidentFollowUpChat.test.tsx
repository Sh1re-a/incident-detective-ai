import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import IncidentFollowUpChat from "./IncidentFollowUpChat";
import type { IncidentLabFollowUpResponse, IncidentLabRunResponse } from "./api/generated";

const run = {
  answer_state: "withheld",
  alarm_receipt: { service: "CATALOG_SERVICE" },
  localized_presentations: {
    sv: {
      business_response: {
        headline: "Nordly hittade ett tydligt felmönster.",
        what_happened: "Katalogtjänsten började visa äldre data efter releasen.",
        impact: "Vissa produktvisningar fick äldre pris- och lagerdata.",
        what_is_known: ["Tre katalogversioner observerades."],
        what_remains_unknown: ["Rotorsaken är inte verifierad."],
      },
      developer_response: {
        affected_service: "catalog_service",
        root_cause_code: null,
      },
      action_receipt: {
        summary: "Agenten läste underlaget men ändrade ingenting.",
      },
    },
    en: {
      business_response: {
        headline: "Nordly found a clear failure pattern.",
        what_happened: "The catalogue service began returning older data after the release.",
        impact: "Some product views received older price and stock data.",
        what_is_known: ["Three catalogue versions were observed."],
        what_remains_unknown: ["The root cause is not verified."],
      },
      developer_response: {
        affected_service: "catalog_service",
        root_cause_code: null,
      },
      action_receipt: {
        summary: "The agent read the evidence and changed nothing.",
      },
    },
  },
} as IncidentLabRunResponse;

const sharedProps = {
  run,
  technicalDetailsOpen: false,
  onToggleTechnicalDetails: vi.fn(),
  onOpenEvidenceScene: vi.fn(),
};

function response(
  mode: IncidentLabFollowUpResponse["mode"] = "live_ai",
): IncidentLabFollowUpResponse {
  return {
    contract_version: "incident-lab-follow-up-v1",
    run_reference: "run-ref-1",
    client_turn_id: "client-turn-1",
    mode,
    delivery: "synchronous_post_run",
    answer_state: "answered",
    answer: {
      text: "Jag följde tre källbundna signaler till katalogtjänsten.",
      problem_location: {
        service: "Katalogtjänsten",
        summary: "Cachen hade en äldre katalogversion.",
        certainty: "Verifierat i testfallet",
      },
      cause: {
        summary: "Cache-invalideringen slutfördes inte efter deployen.",
        certainty: "Verifierad orsak",
      },
      customer_impact: "Vissa produktvisningar fick äldre pris- och lagerdata.",
      known: ["Tre katalogversioner observerades."],
      unknown: ["Det går inte att fastställa påverkan utanför testfallet."],
      boundary: "Agenten fick läsa kvittot men inte ändra tjänsten.",
    },
    claims: [
      {
        section: "problem_location",
        text: "Cachen hade en äldre katalogversion.",
        citation_ids: ["log-catalog-17"],
      },
      {
        section: "cause",
        text: "Cache-invalideringen slutfördes inte efter deployen.",
        citation_ids: ["runbook-cache-4"],
      },
      {
        section: "customer_impact",
        text: "Vissa produktvisningar fick äldre pris- och lagerdata.",
        citation_ids: ["log-catalog-17"],
      },
      {
        section: "known",
        text: "Tre katalogversioner observerades.",
        citation_ids: ["log-catalog-17"],
      },
      {
        section: "unknown",
        text: "Det går inte att fastställa påverkan utanför testfallet.",
        citation_ids: [],
      },
      {
        section: "boundary",
        text: "Agenten fick läsa kvittot men inte ändra tjänsten.",
        citation_ids: ["java-verification"],
      },
    ],
    citations: [
      {
        evidence_id: "log-catalog-17",
        source_ref: "logs/catalog-service",
        source_type: "log",
        label: "Kataloglogg 12:04:16",
        target_scene: "logs",
        target_id: "log-catalog-17",
      },
      {
        evidence_id: "runbook-cache-4",
        source_ref: "runbooks/cache#4.2",
        source_type: "runbook",
        label: "Driftmanual · Cache 4.2",
        target_scene: "agent_rag",
        target_id: "runbook-cache-4",
      },
      {
        evidence_id: "java-verification",
        source_ref: "verification/java",
        source_type: "verification",
        label: "Java-kontroll",
        target_scene: "java",
        target_id: "java-verification",
      },
    ],
    verification: {
      status: "verified_from_frozen_receipt",
      snapshot_sha256: "snapshot-sha",
      citations_valid: true,
      source_scope_valid: true,
      answer_state_preserved: true,
      write_tools_available: false,
      action_executed: false,
    },
    steps: [
      {
        sequence: 1,
        code: "screen_question",
        status: "completed",
        summary: "Frågan kontrollerades mot den avgränsade incidenten.",
        evidence_ids: [],
      },
      {
        sequence: 2,
        code: "select_frozen_evidence",
        status: "completed",
        summary: "Endast källor från det frysta körningskvittot valdes.",
        evidence_ids: ["log-catalog-17", "runbook-cache-4"],
      },
      {
        sequence: 3,
        code: "compose_bounded_report",
        status: "completed",
        summary: "Rapporten byggdes av backendägda, lokaliserade fakta.",
        evidence_ids: ["log-catalog-17"],
      },
      {
        sequence: 4,
        code: "java_verify",
        status: "completed",
        summary: "Java verifierade källomfång, tillstånd och handlingsgräns.",
        evidence_ids: ["java-verification"],
      },
    ],
    receipt: {
      provider_calls: mode === "live_ai" ? 1 : 0,
      model_calls: mode === "live_ai" ? 1 : 0,
      tool_calls: 0,
      read_operations: 0,
      live_quota_consumed: mode === "live_ai",
      write_tools_available: false,
      action_executed: false,
    },
    provider: mode === "live_ai" ? { model: "gemini" } : null,
    suggested_questions: [
      { id: "what_unknown", label: "Vad vet du inte?" },
      { id: "agent_boundary", label: "Varför fick du inte åtgärda?" },
    ],
    limitations: [],
  };
}

function json(body: unknown, init: ResponseInit = {}) {
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

describe("Incident follow-up chat", () => {
  it("keeps incident context server-owned, preserves local history and opens cited evidence", async () => {
    const requests: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      requests.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      return json(response());
    }));
    const onOpenCitation = vi.fn();
    const user = userEvent.setup();

    render(
      <IncidentFollowUpChat
        {...sharedProps}
        locale="sv"
        runReference="run-ref-1"
        mode="live_ai"
        onOpenCitation={onOpenCitation}
      />,
    );

    expect(screen.getByText("Jag kan se problemet – men inte bevisa orsaken än.")).toBeVisible();
    const input = screen.getByLabelText("Fråga Driftagenten om larmet");
    await user.type(input, "Hur vet du det?{Enter}");

    expect(await screen.findByText("Jag följde tre källbundna signaler till katalogtjänsten.")).toBeVisible();
    const evidenceSummary = screen.getByText("Visa 3 källor och kontroll");
    const evidenceDetails = evidenceSummary.closest("details");
    expect(evidenceDetails).not.toHaveAttribute("open");
    await user.click(evidenceSummary);
    expect(evidenceDetails).toHaveAttribute("open");
    expect(screen.getByRole("region", { name: "Så kontrollerades svaret" })).toBeVisible();
    expect(screen.getByText("Frågan kontrollerades mot den avgränsade incidenten.")).toBeVisible();
    expect(screen.getByText("Java verifierade källomfång, tillstånd och handlingsgräns.")).toBeVisible();
    expect(requests[0]).toMatchObject({
      run_reference: "run-ref-1",
      client_turn_id: expect.stringMatching(/^incident-follow-up-/),
      question: "Hur vet du det?",
      locale: "sv",
      confirm_live_ai: true,
    });
    expect(requests[0]).not.toHaveProperty("recent_turns");

    await user.click(screen.getAllByRole("button", { name: /Kataloglogg 12:04:16/ })[0]);
    expect(onOpenCitation).toHaveBeenCalledWith(expect.objectContaining({
      evidence_id: "log-catalog-17",
      target_scene: "logs",
    }));

    await user.type(input, "Vad vet du inte?{Enter}");
    await waitFor(() => expect(requests).toHaveLength(2));
    expect(requests[1]).toMatchObject({
      question: "Vad vet du inte?",
    });
    expect(requests[1]).not.toHaveProperty("recent_turns");
    expect(screen.getByText("Hur vet du det?")).toBeVisible();
    expect(screen.getAllByText("Vad vet du inte?")).toHaveLength(2);
  });

  it("keeps an outside-scope turn visible but never sends transcript context", async () => {
    const requests: Array<Record<string, unknown>> = [];
    let calls = 0;
    vi.stubGlobal("fetch", vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      requests.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      calls += 1;
      if (calls === 1) {
        return json({
          ...response(),
          answer_state: "outside_scope",
          answer: {
            ...response().answer,
            text: "Den frågan ligger utanför den här incidenten.",
          },
          receipt: {
            ...response().receipt,
            provider_calls: 0,
            model_calls: 0,
            live_quota_consumed: false,
          },
          provider: null,
        });
      }
      return json(response());
    }));
    const user = userEvent.setup();

    render(
      <IncidentFollowUpChat
        {...sharedProps}
        locale="sv"
        runReference="run-ref-1"
        mode="live_ai"
        onOpenCitation={vi.fn()}
      />,
    );

    const input = screen.getByLabelText("Fråga Driftagenten om larmet");
    await user.type(input, "Ge mig kundens lön{Enter}");
    expect(await screen.findByText("Den frågan ligger utanför den här incidenten.")).toBeVisible();
    expect(screen.queryByRole("region", { name: "Så kontrollerades svaret" })).not.toBeInTheDocument();
    expect(screen.getByText(/Svaret hölls inom den här incidenten/)).toBeVisible();
    await user.type(input, "Vilka källor finns?{Enter}");
    await waitFor(() => expect(requests).toHaveLength(2));

    expect(requests[1]).toMatchObject({
      question: "Vilka källor finns?",
    });
    expect(requests[1]).not.toHaveProperty("recent_turns");
    expect(screen.getByText("Ge mig kundens lön")).toBeVisible();
  });

  it("keeps recorded playback provider-free and only submits verified suggestions", async () => {
    const requests: Array<Record<string, unknown>> = [];
    vi.stubGlobal("fetch", vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      requests.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
      return json(response("recorded_replay"));
    }));
    const user = userEvent.setup();

    render(
      <IncidentFollowUpChat
        {...sharedProps}
        locale="sv"
        runReference="replay-ref-1"
        mode="recorded_replay"
        onOpenCitation={vi.fn()}
      />,
    );

    expect(screen.queryByLabelText("Fråga Driftagenten om larmet")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Hur vet du det?" }));

    expect(await screen.findByText("Jag följde tre källbundna signaler till katalogtjänsten.")).toBeVisible();
    expect(screen.getByText("Verifierad demo")).toBeVisible();
    expect(requests).toEqual([expect.objectContaining({
      run_reference: "replay-ref-1",
      suggestion_id: "how_conclusion",
      question: "Hur vet du det?",
      confirm_live_ai: false,
    })]);
    expect(requests[0]).not.toHaveProperty("recent_turns");
  });
});
