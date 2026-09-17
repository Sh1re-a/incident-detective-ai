import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import NordlyV2App from "./NordlyV2App";
import type {
  DemoCustomerChatTurnResponse,
  KnowledgeDocumentLibraryResponse,
  LiveAiStatusResponse,
} from "../api/generated";

const protectedDocument = {
  id: "kb-employee-compensation-register",
  version: "1.0",
  display_filename: "employee-compensation-register.md",
  document_type: "register",
  classification: "restricted",
  title: "Synthetic employee compensation register",
  title_sv: "Syntetiskt register över individuell ersättning",
  summary_sv: "Ett avsiktligt begränsat testdokument.",
  summary_en: "An intentionally restricted test document.",
  owner_team: "People Operations",
  lifecycle: "APPROVED",
  effective_from: "2026-06-01",
  effective_until: null,
  access_scopes: ["people_operations"],
  related_document_ids: [],
  rag_eligibility: {
    eligible: false,
    reason_code: "PUBLIC_DEMO_SCOPE_MISSING" as const,
    summary_sv: "Saknar publik demoscope.",
    summary_en: "Missing public demo scope.",
  },
  content_visible: false,
  chunks: [],
};

const documentLibrary: KnowledgeDocumentLibraryResponse = {
  contract_version: "nordly-knowledge-document-library-v2",
  mode: "read_only_corpus",
  truth_label: "SYNTHETISK KORPUS",
  manifest_version: "v1",
  corpus_version: "nordly-knowledge-corpus-v3",
  corpus_content_sha256: "a".repeat(64),
  synthetic_only: true,
  current_vector_search: false,
  document_count: 1,
  chunk_count: 0,
  eligible_document_count: 0,
  eligible_chunk_count: 0,
  eligibility_rule: {
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
  },
  embedding_profile: {
    provider: "google",
    model_id: "gemini-embedding-001",
    dimensions: 768,
  },
  documents: [protectedDocument],
};

const offlineStatus: LiveAiStatusResponse = {
  contract_version: "live-ai-status-v1",
  live_state: "daily_budget_exhausted",
  reason_code: "DAILY_BUDGET_EXHAUSTED",
  resets_at: "2026-09-18T00:00:00Z",
  retry_after_seconds: 3600,
  replay_available: true,
  daily_cost_guard_active: true,
  quota_scope: "database_global",
};

const availableStatus: LiveAiStatusResponse = {
  ...offlineStatus,
  live_state: "available",
  reason_code: "AVAILABLE",
  resets_at: null,
  retry_after_seconds: null,
};

const noReplayStatus: LiveAiStatusResponse = {
  ...offlineStatus,
  replay_available: false,
};

const policyReplay = {
  question: {
    id: "refund-timing",
    prompt_sv: "När syns pengarna efter en återbetalning?",
    prompt_en: "When will a refund appear?",
  },
  retrieval: { ranked_matches: [] },
  answer: {
    summary_sv: "Nordly skickar en godkänd återbetalning samma arbetsdag.",
    summary_en: "Nordly submits an approved refund the same business day.",
  },
};

const boundaryReplay = {
  question: {
    id: "refund-customer-action",
    prompt_sv: "Kan du återbetala ordern åt mig?",
    prompt_en: "Can you refund the order for me?",
  },
  retrieval: { ranked_matches: [] },
  answer: {
    summary_sv: "Jag kan inte genomföra återbetalningen här.",
    summary_en: "I cannot issue the refund here.",
  },
};

const orderLookup = {
  order: {
    order_id: "NORD-2051",
    item_count: 1,
    item_summary_sv: "Aster bordslampa i sandbeige",
    item_summary_en: "Aster table lamp in sand beige",
    status_sv: "Skickad",
    status_en: "Shipped",
    estimated_delivery_from: "2026-09-18",
    estimated_delivery_through: "2026-09-20",
  },
};

const incidentReplay = {
  run_reference: "ilr_test_replay",
  recorded_run: {
    alarm_receipt: {
      alarm_id: "alarm-test",
      service: "checkout_service",
      evidence_ids: [],
      signal: { observed_value: 3, lookback_seconds: 60 },
    },
    backend_logs: [],
    agent_turn: { tool_events: [] },
    localized_presentations: {
      sv: { business_response: { what_happened: "Tre syntetiska köp misslyckades.", impact: "Köpflödet påverkades.", what_remains_unknown: ["Rotorsaken är inte verifierad."] } },
      en: { business_response: { what_happened: "Three synthetic purchases failed.", impact: "The checkout flow was affected.", what_remains_unknown: ["The root cause is not verified."] } },
    },
  },
};

const approvedPlan = {
  contract_version: "incident-plan-v1",
  incident_family: "payment_timeout",
  severity: "high",
  affected_services: ["payment_adapter"],
  summary: "Syntetiskt betalningslarm",
  synthetic_only: true,
  write_actions_allowed: false,
  human_approval_required: true,
};

const incidentPlanResponse = {
  contract_version: "incident-lab-plan-v1",
  outcome: "plan_ready",
  safety: {
    decision: "allowed",
    reason_code: "ALLOWED",
    summary_sv: "Planen är tillåten.",
    summary_en: "The plan is allowed.",
  },
  java_validation: { decision: "APPROVED", plan: approvedPlan },
  provider_receipt: { transport: "vertex_ai" },
};

const liveIncidentRun = {
  ...incidentReplay.recorded_run,
  run_reference: "ilr_live_test",
  answer_state: "withheld",
};

const runbookEvidence = {
  evidence_type: "runbook",
  evidence_id: "runbook-payment-timeout-precedence",
  display_summary: "Jämför klientens timeout med betalpartnerns svarstid.",
  source_ref: "runbooks/rb-payment-provider-timeouts#timeout-precedence",
  content: {
    document_id: "rb-payment-provider-timeouts",
    chunk_id: "timeout-precedence",
    document_version: "1.0",
    text: "Jämför klientens timeout med betalpartnerns svarstid innan en rotorsak rapporteras.",
  },
};

const liveIncidentRunWithRunbook = {
  ...liveIncidentRun,
  agent_turn: {
    tool_events: [{ evidence: [runbookEvidence], runbook_retrieval: null }],
    receipt: {
      model_calls: 1,
      embedding_calls: 1,
      adk_tool_calls: 1,
      estimated_cost_usd: 0.0002,
    },
  },
};

const runbookDocument = {
  ...protectedDocument,
  id: "kb-payment-provider-timeouts",
  display_filename: "payment-provider-timeouts.md",
  document_type: "runbook",
  classification: "internal_demo",
  title: "Payment provider timeouts",
  title_sv: "Timeouts hos betalpartnern",
  summary_sv: "Godkänd driftinstruktion för timeoutfel.",
  summary_en: "Approved operating guide for timeout failures.",
  owner_team: "Platform Operations",
  access_scopes: ["public_demo"],
  rag_eligibility: {
    eligible: true,
    reason_code: "APPROVED_PUBLIC_DEMO" as const,
    summary_sv: "Godkänd för demot.",
    summary_en: "Approved for the demo.",
  },
  content_visible: true,
  chunks: [{
    id: "timeout-precedence",
    section_heading: "Timeout före rotorsak",
    source_ref: "runbooks/rb-payment-provider-timeouts#timeout-precedence",
    evidence_id: "runbook-payment-timeout-precedence",
    display_summary_sv: "Jämför timeoutgränserna först.",
    display_summary_en: "Compare timeout thresholds first.",
    text: "Jämför klientens timeout med betalpartnerns svarstid innan en rotorsak rapporteras.",
    content_sha256: "b".repeat(64),
  }],
};

const documentLibraryWithRunbook: KnowledgeDocumentLibraryResponse = {
  ...documentLibrary,
  document_count: 2,
  chunk_count: 1,
  eligible_document_count: 1,
  eligible_chunk_count: 1,
  documents: [protectedDocument, runbookDocument],
};

const driftFollowUpResponse = {
  answer: { text: "Jag ser tre betalningsfel inom samma minut, men underlaget räcker ännu inte för att bevisa rotorsaken." },
  citations: [],
};

const citedDriftFollowUpResponse = {
  ...driftFollowUpResponse,
  citations: [{
    evidence_id: "runbook-payment-timeout-precedence",
    source_ref: "runbooks/rb-payment-provider-timeouts#timeout-precedence",
    source_type: "runbook",
    label: "Timeout precedence",
    target_scene: "agent_rag",
    target_id: "runbook-payment-timeout-precedence",
  }],
};

const blockedResponse = {
  contract_version: "nordly-demo-customer-chat-turn-v1",
  turn_id: "blocked-turn",
  mode: "controlled_synthetic_customer_chat",
  outcome: "refused",
  submitted_message: {
    text: "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]",
    locale: "sv",
    redacted: true,
  },
  context: {
    context_version: "nordly-demo-customer-context-v1",
    context_id: "fixed-demo-customer",
    customer_display_name: "Shirwac \"Shirre\" Abib",
    customer_preferred_name: "Shirre",
    current_order_id: "NORD-2051",
    context_source_ref: "demo/context",
    order_source_ref: "demo/order",
    synthetic_only: true,
    persistent_memory: false,
    memory_scope: "request_scoped_bounded_history",
  },
  intent: {},
  safety: {
    decision: "BLOCK",
    reason_code: "EMPLOYEE_COMPENSATION_REQUEST",
    summary_sv: "Frågan stoppades före AI.",
    summary_en: "The question was stopped before AI.",
  },
  assistant_message: {
    text_sv: "Jag kan inte hjälpa till med privata löneuppgifter.",
    text_en: "I cannot help with private salary data.",
  },
  order: null,
  tool_events: [],
  sources: [],
  verified_claims: [],
  rag: {},
  verification: {},
  receipt: {
    read_operations: 0,
    provider_calls: 0,
    business_write_operations: 0,
    business_write_tools_available: false,
    business_action_executed: false,
    persistent_memory_used: false,
  },
  error: null,
  limitations: [],
} as unknown as DemoCustomerChatTurnResponse;

const handledBoundaryResponse = {
  ...blockedResponse,
  outcome: "outside_authority",
  assistant_message: {
    text_sv: "Jag kan förklara policyn, men jag kan inte ändra eller återbetala ordern här.",
    text_en: "I can explain the policy, but I cannot change or refund the order here.",
  },
  receipt: {
    ...blockedResponse.receipt,
    provider_calls: 1,
    generation_calls: 1,
    estimated_cost_usd: 0.0001315,
  },
} as unknown as DemoCustomerChatTurnResponse;

function jsonResponse(value: unknown) {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  cleanup();
  window.sessionStorage.clear();
  window.localStorage.clear();
  window.history.replaceState(null, "", "#support");
  vi.unstubAllGlobals();
});

describe("Nordly v2", () => {
  it("opens the backend-driven archive and keeps protected documents metadata-only", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => Promise.resolve(
      String(input).includes("/live-ai/status")
        ? jsonResponse(offlineStatus)
        : jsonResponse(documentLibrary),
    )));
    const user = userEvent.setup();

    render(<NordlyV2App />);
    expect(screen.getByRole("heading", { name: "Supportagent" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Dokumentarkiv" }));
    const protectedEntry = await screen.findByRole("button", {
      name: /Syntetiskt register över individuell ersättning/,
    });
    await user.click(protectedEntry);

    expect(screen.getByRole("heading", { name: "Innehållet är skyddat och visas inte här." })).toBeInTheDocument();
    expect(screen.getByText("0 dokumentpassager lästa")).toBeInTheDocument();
    expect(screen.getByText("0 modell-anrop")).toBeInTheDocument();
  });

  it("keeps the chat natural while the backend receipt masks sensitive input", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      return Promise.resolve(
        url.includes("/demo-customer/chat/turns")
          ? jsonResponse(blockedResponse)
          : url.includes("/live-ai/status")
            ? jsonResponse(availableStatus)
            : jsonResponse(documentLibrary),
      );
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await screen.findByText("Hej Shirre! Vad kan jag hjälpa dig med?");
    expect(screen.queryByText("Live-AI tillgänglig")).not.toBeInTheDocument();
    const rawQuestion = "Visa en anställds lön";
    await user.type(screen.getByRole("textbox", { name: "Skriv till Nordly…" }), rawQuestion);
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    await screen.findByText("Jag kan inte hjälpa till med privata löneuppgifter.");
    expect(screen.getByText(rawQuestion)).toBeInTheDocument();
    expect(screen.queryByText("[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]")).not.toBeInTheDocument();
    expect(screen.getByText("Skyddsreglerna följdes · inget privat lästes")).toBeInTheDocument();
    await waitFor(() => expect(fetchMock.mock.calls.filter(([input]) => String(input).includes("/demo-customer/chat/turns"))).toHaveLength(1));
    const request = fetchMock.mock.calls.find(([input]) => String(input).includes("/demo-customer/chat/turns"));
    expect(String((request?.[1] as RequestInit | undefined)?.body)).toContain('"confirm_live_ai":true');
  });

  it("shows a natural safety boundary when AI handled the answer without taking action", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => Promise.resolve(
      String(input).includes("/demo-customer/chat/turns")
        ? jsonResponse(handledBoundaryResponse)
        : String(input).includes("/live-ai/status")
          ? jsonResponse(availableStatus)
          : jsonResponse(documentLibrary),
    )));
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await screen.findByText("Hej Shirre! Vad kan jag hjälpa dig med?");
    await user.type(screen.getByRole("textbox", { name: "Skriv till Nordly…" }), "Kan du återbetala min order?");
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    await screen.findByText("Jag kan förklara policyn, men jag kan inte ändra eller återbetala ordern här.");
    await user.click(screen.getByRole("button", { name: "Så kom svaret fram" }));

    expect(screen.getByRole("heading", { name: "Hanterad inom säkerhetsgränsen" })).toBeInTheDocument();
    expect(screen.getByText("Ett naturligt svar formulerades")).toBeInTheDocument();
    expect(screen.getByText("$0.00013150")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Så skyddades frågan" })).not.toBeInTheDocument();
  });

  it("shows a clean two-action offline start without chat or a status badge", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) return Promise.resolve(jsonResponse(offlineStatus));
      if (url.includes("refund-timing")) return Promise.resolve(jsonResponse(policyReplay));
      if (url.includes("refund-customer-action")) return Promise.resolve(jsonResponse(boundaryReplay));
      if (url.includes("/demo-orders/NORD-2051")) return Promise.resolve(jsonResponse(orderLookup));
      if (url.includes("/incident-lab/runs/recorded-replay")) return Promise.resolve(jsonResponse(incidentReplay));
      return Promise.resolve(jsonResponse(documentLibrary));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    render(<NordlyV2App />);
    expect(await screen.findByRole("heading", { name: "AI:n är offline just nu." })).toBeInTheDocument();
    expect(screen.queryByText("Live-AI pausad · se replay")).not.toBeInTheDocument();
    expect(screen.queryByText("Hej Shirre! Vad kan jag hjälpa dig med?")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Skriv till Nordly…" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Spela verifierad replay" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kontrollera AI igen" })).toBeInTheDocument();
    expect(screen.getByText("Verifierade källor · Endast läsning · Interaktiv AI-demo")).toBeInTheDocument();
    expect(screen.getByText("Portfolio-demo med syntetisk data · Använd Live-AI ansvarsfullt – begränsad dagskvot")).toBeInTheDocument();
    expect(screen.queryByText("All data är syntetisk")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Spela verifierad replay" }));
    expect(await screen.findByText("När syns pengarna efter en återbetalning?")).toBeInTheDocument();
    const supportEndButton = screen.getByRole("button", { name: "Avsluta samtal" });
    expect(supportEndButton).toBeInTheDocument();
    expect(supportEndButton.closest(".agent-session-header")).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes("/runs/recorded-replay"))).toHaveLength(2);

    await user.click(supportEndButton);
    expect(screen.getByRole("heading", { name: "AI:n är offline just nu." })).toBeInTheDocument();
    expect(screen.queryByText("När syns pengarna efter en återbetalning?")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    expect(await screen.findByRole("heading", { name: "Driftagenten väntar på en signal." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Spela verifierad replay" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kontrollera AI igen" })).toBeInTheDocument();
    expect(screen.queryByText("Live-AI pausad · se replay")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Fråga om rapporten…" })).not.toBeInTheDocument();
    expect(screen.getByText(/Starta ett syntetiskt larm/)).toBeInTheDocument();
    expect(screen.getByText("Historisk inspelning · 0 nya AI-anrop")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Spela verifierad replay" }));
    const driftEndButton = await screen.findByRole("button", { name: "Avsluta samtal" });
    expect(driftEndButton.closest(".agent-session-header")).toBeInTheDocument();
    expect(await screen.findByRole("textbox", { name: "Fråga om rapporten…" }, { timeout: 4_500 })).toBeDisabled();
    expect(screen.getByPlaceholderText("Fri chatt kräver Live-AI")).toBeInTheDocument();
    expect(screen.queryByText("Vet du varför?")).not.toBeInTheDocument();
    expect(screen.queryByText("Live-AI pausad · se replay")).not.toBeInTheDocument();
    await user.click(driftEndButton);
    expect(await screen.findByRole("heading", { name: "Driftagenten väntar på en signal." })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Avsluta samtal" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Fråga om rapporten…" })).not.toBeInTheDocument();
  });

  it("states clearly when backend replay is unavailable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => Promise.resolve(
      String(input).includes("/live-ai/status")
        ? jsonResponse(noReplayStatus)
        : jsonResponse(documentLibrary),
    )));
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await screen.findByRole("heading", { name: "AI:n är offline just nu." });
    expect(screen.getByRole("button", { name: "Replay är inte tillgänglig" })).toBeDisabled();
    expect(screen.getByText(/Varken Live-AI eller replay är tillgänglig/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    expect(await screen.findByRole("heading", { name: "Driftagenten väntar på en signal." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Replay är inte tillgänglig" })).toBeDisabled();
    expect(screen.queryByRole("textbox", { name: "Fråga om rapporten…" })).not.toBeInTheDocument();
  });

  it("runs the approved live Drift flow and enables genuine free follow-up chat", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) return Promise.resolve(jsonResponse(availableStatus));
      if (url.includes("/incident-lab/plans")) return Promise.resolve(jsonResponse(incidentPlanResponse));
      if (url.includes("/incident-lab/follow-ups")) return Promise.resolve(jsonResponse(driftFollowUpResponse));
      if (url.endsWith("/api/v1/incident-lab/runs")) return Promise.resolve(jsonResponse(liveIncidentRun));
      if (url.includes("/incident-lab/runs/recorded-replay")) return Promise.resolve(jsonResponse(incidentReplay));
      return Promise.resolve(jsonResponse(documentLibrary));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    expect(await screen.findByRole("button", { name: "Starta live-utredning" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Spela verifierad replay" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Starta live-utredning" }));

    const input = await screen.findByRole("textbox", { name: "Fråga om rapporten…" }, { timeout: 4_500 });
    expect(input).toBeEnabled();
    await user.type(input, "va hände egentlien?? fattar nt");
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    expect(await screen.findByText("Jag ser tre betalningsfel inom samma minut, men underlaget räcker ännu inte för att bevisa rotorsaken.")).toBeInTheDocument();

    const planRequest = fetchMock.mock.calls.find(([url]) => String(url).includes("/incident-lab/plans"));
    const runRequest = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/api/v1/incident-lab/runs"));
    const followUpRequest = fetchMock.mock.calls.find(([url]) => String(url).includes("/incident-lab/follow-ups"));
    expect(String((planRequest?.[1] as RequestInit | undefined)?.body)).toContain('"confirm_live_ai":true');
    expect(String((planRequest?.[1] as RequestInit | undefined)?.body)).toContain("tre tidsgränsöverskridna betalningar");
    expect(String((runRequest?.[1] as RequestInit | undefined)?.body)).toContain('"confirm_live_ai":true');
    expect(String((followUpRequest?.[1] as RequestInit | undefined)?.body)).toContain('"confirm_live_ai":true');
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes("/runs/recorded-replay"))).toHaveLength(0);
  });

  it("does not imply that a Drift document supported a follow-up with zero citations", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) return Promise.resolve(jsonResponse(availableStatus));
      if (url.includes("/incident-lab/plans")) return Promise.resolve(jsonResponse(incidentPlanResponse));
      if (url.includes("/incident-lab/follow-ups")) return Promise.resolve(jsonResponse(driftFollowUpResponse));
      if (url.endsWith("/api/v1/incident-lab/runs")) return Promise.resolve(jsonResponse(liveIncidentRunWithRunbook));
      return Promise.resolve(jsonResponse(documentLibraryWithRunbook));
    }));
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    await user.click(await screen.findByRole("button", { name: "Starta live-utredning" }));
    const input = await screen.findByRole("textbox", { name: "Fråga om rapporten…" }, { timeout: 4_500 });
    await user.type(input, "Vad vet du inte?");
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    const answer = await screen.findByText(driftFollowUpResponse.answer.text);
    const answerBubble = answer.closest(".message-bubble");
    expect(answerBubble).not.toBeNull();
    expect(answerBubble?.querySelector(".source-chips")).toBeNull();
  });

  it("opens the exact Drift document passage returned in follow-up citations", async () => {
    vi.stubGlobal("fetch", vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) return Promise.resolve(jsonResponse(availableStatus));
      if (url.includes("/incident-lab/plans")) return Promise.resolve(jsonResponse(incidentPlanResponse));
      if (url.includes("/incident-lab/follow-ups")) return Promise.resolve(jsonResponse(citedDriftFollowUpResponse));
      if (url.endsWith("/api/v1/incident-lab/runs")) return Promise.resolve(jsonResponse(liveIncidentRunWithRunbook));
      return Promise.resolve(jsonResponse(documentLibraryWithRunbook));
    }));
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    await user.click(await screen.findByRole("button", { name: "Starta live-utredning" }));
    const input = await screen.findByRole("textbox", { name: "Fråga om rapporten…" }, { timeout: 4_500 });
    await user.type(input, "Vilken driftinstruktion stödjer rapporten?");
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    const answer = await screen.findByText(citedDriftFollowUpResponse.answer.text);
    const answerBubble = answer.closest(".message-bubble");
    expect(answerBubble).not.toBeNull();
    await user.click(within(answerBubble as HTMLElement).getByRole("button", { name: /Timeouts hos betalpartnern/ }));

    expect(await screen.findByRole("heading", { name: "Timeouts hos betalpartnern" })).toBeInTheDocument();
    expect(screen.getByText("Passage som användes i svaret")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Tillbaka till Drift" })).toBeInTheDocument();
  });

  it("does not silently replace a failed paid Drift run with replay", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) return Promise.resolve(jsonResponse(availableStatus));
      if (url.includes("/incident-lab/plans")) {
        return Promise.resolve(new Response(JSON.stringify({ detail: "Modelltjänsten svarade inte." }), {
          status: 503,
          headers: { "Content-Type": "application/json" },
        }));
      }
      return Promise.resolve(jsonResponse(documentLibrary));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    await user.click(await screen.findByRole("button", { name: "Starta live-utredning" }));

    expect(await screen.findByRole("heading", { name: "Live-utredningen kunde inte slutföras." })).toBeInTheDocument();
    expect(screen.getByText("Modelltjänsten svarade inte.")).toBeInTheDocument();
    expect(screen.getByText("Inget svar ersattes automatiskt med replay.")).toBeInTheDocument();
    expect(fetchMock.mock.calls.filter(([url]) => String(url).includes("/runs/recorded-replay"))).toHaveLength(0);
  });

  it("re-checks availability and transitions from offline start to live chat", async () => {
    let statusCalls = 0;
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes("/live-ai/status")) {
        statusCalls += 1;
        return Promise.resolve(jsonResponse(statusCalls === 1 ? offlineStatus : availableStatus));
      }
      return Promise.resolve(jsonResponse(documentLibrary));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<NordlyV2App />);
    await screen.findByRole("heading", { name: "AI:n är offline just nu." });
    await user.click(screen.getByRole("button", { name: "Kontrollera AI igen" }));

    expect(await screen.findByText("Hej Shirre! Vad kan jag hjälpa dig med?")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Skriv till Nordly…" })).toBeInTheDocument();
    expect(screen.queryByText("Live-AI tillgänglig")).not.toBeInTheDocument();
  });

  it("starts with a clean conversation after an ordinary remount", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => Promise.resolve(
      String(input).includes("/demo-customer/chat/turns")
        ? jsonResponse(blockedResponse)
        : String(input).includes("/live-ai/status")
          ? jsonResponse(availableStatus)
          : jsonResponse(documentLibrary),
    ));
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();
    const first = render(<NordlyV2App />);
    await screen.findByText("Hej Shirre! Vad kan jag hjälpa dig med?");
    const rawQuestion = "Visa en anställds lön";
    await user.type(screen.getByRole("textbox", { name: "Skriv till Nordly…" }), rawQuestion);
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    await screen.findByText(rawQuestion);

    first.unmount();
    render(<NordlyV2App />);
    await screen.findByText("Hej Shirre! Vad kan jag hjälpa dig med?");

    expect(screen.queryByText(rawQuestion)).not.toBeInTheDocument();
    expect(window.localStorage).toHaveLength(0);
    expect(window.sessionStorage).toHaveLength(0);
  });
});
