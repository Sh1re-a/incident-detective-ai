import { cleanup, render, screen, waitFor } from "@testing-library/react";
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
    expect(supportEndButton.closest(".chat-column")).toHaveClass("chat-column--has-end-control");
    expect(fetchMock.mock.calls.filter(([input]) => String(input).includes("/runs/recorded-replay"))).toHaveLength(2);

    await user.click(supportEndButton);
    expect(screen.getByRole("heading", { name: "AI:n är offline just nu." })).toBeInTheDocument();
    expect(screen.queryByText("När syns pengarna efter en återbetalning?")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Driftagent" }));
    expect(await screen.findByRole("heading", { name: "Driftagenten håller koll." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Spela verifierad replay" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Kontrollera AI igen" })).toBeInTheDocument();
    expect(screen.queryByText("Live-AI pausad · se replay")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Fråga om rapporten…" })).not.toBeInTheDocument();
    expect(screen.getByText(/AI-genererat syntetiskt fall/)).toBeInTheDocument();
    expect(screen.getByText("Registrerad backendkörning · inga nya AI-anrop")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Spela verifierad replay" }));
    const driftEndButton = await screen.findByRole("button", { name: "Avsluta samtal" });
    expect(driftEndButton.closest(".chat-column")).toHaveClass("chat-column--has-end-control");
    await user.click(await screen.findByRole("button", { name: "Visa resultat nu" }));
    expect(screen.getByRole("textbox", { name: "Fråga om rapporten…" })).toBeEnabled();
    expect(screen.queryByText("Live-AI pausad · se replay")).not.toBeInTheDocument();
    await user.click(driftEndButton);
    expect(await screen.findByRole("heading", { name: "Driftagenten håller koll." })).toBeInTheDocument();
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
    expect(await screen.findByRole("heading", { name: "Driftagenten håller koll." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Replay är inte tillgänglig" })).toBeDisabled();
    expect(screen.queryByRole("textbox", { name: "Fråga om rapporten…" })).not.toBeInTheDocument();
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
