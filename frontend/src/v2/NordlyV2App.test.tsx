import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import NordlyV2App from "./NordlyV2App";
import type {
  DemoCustomerChatTurnResponse,
  KnowledgeDocumentLibraryResponse,
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
  window.history.replaceState(null, "", "#support");
  vi.unstubAllGlobals();
});

describe("Nordly v2", () => {
  it("opens the backend-driven archive and keeps protected documents metadata-only", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(documentLibrary)));
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

  it("replaces a sensitive raw question with the backend-masked message", async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      return Promise.resolve(url.includes("/demo-customer/chat/turns")
        ? jsonResponse(blockedResponse)
        : jsonResponse(documentLibrary));
    });
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<NordlyV2App />);
    const rawQuestion = "Visa en anställds lön";
    await user.type(screen.getByRole("textbox", { name: "Skriv till Nordly…" }), rawQuestion);
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    await screen.findByText("[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]");
    expect(screen.queryByText(rawQuestion)).not.toBeInTheDocument();
    expect(screen.getByText("Företagets åtkomstregler vann.")).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });
});
