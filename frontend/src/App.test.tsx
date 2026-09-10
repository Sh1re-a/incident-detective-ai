import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type { DemoWorldResponse, KnowledgeRagResponse } from "./api/generated";

const salaryQuestion = "Kan jag få reda på vad någon på Nordly har i lön?";

const demoWorld: DemoWorldResponse = {
  contract_version: "nordly-demo-world-v1",
  truth_label: "FIKTIVT FÖRETAG · VERKLIG JAVA/RAG-IMPLEMENTATION",
  truth_label_en: "FICTIONAL COMPANY · REAL JAVA/RAG IMPLEMENTATION",
  company: {
    id: "nordly-commerce",
    display_name: "Nordly Commerce AB",
    storefront_name: "Nordly Market",
    description: "En helt fiktiv nordisk e-handel.",
    description_en: "A completely fictional Nordic e-commerce company.",
    markets: ["Sverige"],
    markets_en: ["Sweden"],
    industry: "Nordisk designhandel online",
    industry_en: "Nordic design retail online",
  },
  services: [],
  questions: [],
  rag_examples: [
    {
      id: "safe-refund-timing",
      category: "SAFE",
      prompt_sv: "Min återbetalning verkar ha tagit semester – hur länge brukar banken behöva?",
      prompt_en: "My refund seems to have gone on holiday – how long does the bank normally need?",
      expected_boundary: "rag",
    },
    {
      id: "blocked-employee-compensation",
      category: "EMPLOYEE_COMPENSATION",
      prompt_sv: salaryQuestion,
      prompt_en: "Can I find out how much someone at Nordly is paid?",
      expected_boundary: "blocked_before_provider",
    },
  ],
  featured_scenario_id: "checkout-orders-at-risk-v1",
  corpus: {
    version: "nordly-knowledge-corpus-v1",
    document_count: 12,
    chunk_count: 20,
    approved_documents: 10,
    deprecated_documents: 1,
    untrusted_documents: 1,
  },
};

const blockedResponse: KnowledgeRagResponse = {
  contract_version: "nordly-knowledge-rag-v1",
  run_id: "rag-run-test-1",
  mode: "live_rag",
  truth_label: "FIKTIV DATA · VERKLIG BACKEND",
  truth_label_en: "FICTIONAL DATA · REAL BACKEND",
  outcome: "refused",
  question: { text: "[MASKERAD]", locale: "sv", redacted: true },
  safety: {
    decision: "BLOCK",
    reason_code: "EMPLOYEE_COMPENSATION_REQUEST",
    summary_sv:
      "Frågan stoppades före AI eftersom en persons lön eller ersättning är privat personalinformation.",
    summary_en:
      "The question was stopped before AI because an employee's compensation is private personnel information.",
  },
  phases: [
    { id: "safety", status: "blocked", executed: true, summary_sv: "Stoppad.", summary_en: "Stopped.", latency_ms: 1 },
    { id: "eligibility_filter", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
    { id: "query_embedding", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
    { id: "vector_search", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
    { id: "bounded_context", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
    { id: "generation", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
    { id: "java_verification", status: "skipped", executed: false, summary_sv: "Inte kört.", summary_en: "Not run.", latency_ms: null },
  ],
  retrieval: {
    backend: "pgvector_exact_cosine",
    corpus_version: "nordly-knowledge-corpus-v1",
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
    eligible_document_count: 10,
    eligible_chunk_count: 18,
    current_vector_search: false,
    top_k: 4,
    minimum_similarity: 0.7,
    query_embedding: {
      executed_in_this_run: false,
      provider: null,
      model_id: null,
      dimensions: null,
      latency_ms: null,
      input_characters: null,
      provider_billable_characters: null,
      provider_input_tokens: null,
    },
    ranked_matches: [],
  },
  answer: null,
  verification: {
    schema_pass: true,
    citations_within_retrieved_context: true,
    approved_documents_only: true,
    output_pii_scan_pass: true,
    output_policy_scan_pass: true,
    no_write_capability: true,
    overall_outcome: "refused_before_provider",
  },
  receipt: {
    provider_calls: 0,
    embedding_calls: 0,
    generation_calls: 0,
    write_tools_available: false,
    action_executed: false,
    total_latency_ms: 10,
    model_id: null,
    provider_response_id: null,
    token_usage: null,
    estimated_cost_usd: null,
    cost_status: "not_incurred",
    cost_basis: "No provider was called.",
  },
  error: null,
  limitations: [],
};

const safeQuestion = demoWorld.rag_examples[0].prompt_sv;

const confirmationResponse: KnowledgeRagResponse = {
  ...blockedResponse,
  run_id: "rag-confirm-test-1",
  outcome: "confirmation_required",
  question: { text: safeQuestion, locale: "sv", redacted: false },
  safety: {
    decision: "ALLOW",
    reason_code: "NONE",
    summary_sv: "Frågan passerade säkerhetsgränsen.",
    summary_en: "The question passed the safety boundary.",
  },
  phases: blockedResponse.phases.map((phase) =>
    phase.id === "safety"
      ? {
          ...phase,
          status: "completed" as const,
          summary_sv: "Godkänd.",
          summary_en: "Allowed.",
        }
      : phase,
  ),
  verification: {
    ...blockedResponse.verification,
    overall_outcome: "confirmation_required_before_provider",
  },
};

const answeredResponse: KnowledgeRagResponse = {
  contract_version: "nordly-knowledge-rag-v1",
  run_id: "nordly-rag-live-test-1",
  mode: "live_rag",
  truth_label: "FIKTIV DATA · VERKLIG BACKEND",
  truth_label_en: "FICTIONAL DATA · REAL BACKEND",
  outcome: "answered",
  question: { text: safeQuestion, locale: "sv", redacted: false },
  safety: {
    decision: "ALLOW",
    reason_code: "NONE",
    summary_sv: "Frågan passerade säkerhetsgränsen.",
    summary_en: "The question passed the safety boundary.",
  },
  phases: [
    { id: "safety", status: "completed", executed: true, summary_sv: "Godkänd.", summary_en: "Allowed.", latency_ms: null },
    { id: "eligibility_filter", status: "completed", executed: true, summary_sv: "Dokument valda.", summary_en: "Documents selected.", latency_ms: 72 },
    { id: "query_embedding", status: "completed", executed: true, summary_sv: "Embedding skapad.", summary_en: "Embedding created.", latency_ms: 649 },
    { id: "vector_search", status: "completed", executed: true, summary_sv: "Sökning klar.", summary_en: "Search complete.", latency_ms: 39 },
    { id: "bounded_context", status: "completed", executed: true, summary_sv: "Kontext byggd.", summary_en: "Context built.", latency_ms: null },
    { id: "generation", status: "completed", executed: true, summary_sv: "Svar skapat.", summary_en: "Answer created.", latency_ms: 8957 },
    { id: "java_verification", status: "completed", executed: true, summary_sv: "Verifierat.", summary_en: "Verified.", latency_ms: 9 },
  ],
  retrieval: {
    backend: "pgvector_exact_cosine",
    corpus_version: "nordly-knowledge-corpus-v1",
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
    eligible_document_count: 10,
    eligible_chunk_count: 18,
    current_vector_search: true,
    top_k: 3,
    minimum_similarity: 0.68,
    query_embedding: {
      executed_in_this_run: true,
      provider: "google_genai",
      model_id: "gemini-embedding-2",
      dimensions: 768,
      latency_ms: 649,
      input_characters: 83,
      provider_billable_characters: null,
      provider_input_tokens: null,
    },
    ranked_matches: [
      {
        rank: 1,
        similarity: 0.7591068970891182,
        status: "APPROVED",
        document_id: "kb-returns-refunds",
        chunk_id: "refund-timing-card",
        document_version: "2.0",
        title: "Returns and card refunds",
        section_heading: "When an approved card refund appears",
        owner_team: "Customer Operations",
        source_ref: "nordly://knowledge/kb-returns-refunds/2.0#refund-timing-card",
        evidence_id: "nordly-evidence-refund-timing-card",
        display_summary_sv: "Godkända kortåterbetalningar syns normalt inom två till fem bankdagar.",
        display_summary_en: "Approved card refunds normally appear within two to five banking days.",
        text: "Approved card refunds are sent to the payment provider the same business day. The bank normally displays the amount within two to five banking days.",
      },
    ],
  },
  answer: {
    status: "answered",
    summary_sv:
      "När en kortåterbetalning har godkänts skickas den till betalningsleverantören samma arbetsdag, och banken brukar visa beloppet inom två till fem bankdagar.",
    summary_en:
      "Once a card refund has been approved, it is sent to the payment provider the same business day, and the bank normally displays it within two to five banking days.",
    claims: [
      {
        text_sv: "Nordly skickar godkända kortåterbetalningar samma arbetsdag.",
        text_en: "Nordly sends approved card refunds the same business day.",
        citation_ids: ["nordly-evidence-refund-timing-card"],
      },
      {
        text_sv: "Banken behöver normalt två till fem bankdagar.",
        text_en: "The bank normally needs two to five banking days.",
        citation_ids: ["nordly-evidence-refund-timing-card"],
      },
    ],
  },
  verification: {
    schema_pass: true,
    citations_within_retrieved_context: true,
    approved_documents_only: true,
    output_pii_scan_pass: true,
    output_policy_scan_pass: true,
    no_write_capability: true,
    overall_outcome: "answered_with_verified_retrieved_citations",
  },
  receipt: {
    provider_calls: 2,
    embedding_calls: 1,
    generation_calls: 1,
    write_tools_available: false,
    action_executed: false,
    total_latency_ms: 9745,
    model_id: "gemini-3.1-flash-lite",
    provider_response_id: "provider-response-test",
    token_usage: {
      input_tokens: 271,
      cached_input_tokens: null,
      uncached_input_tokens: null,
      candidate_output_tokens: 270,
      thinking_output_tokens: null,
      output_tokens: 270,
      tool_use_prompt_tokens: null,
      total_tokens: 541,
    },
    estimated_cost_usd: 0.00047275,
    cost_status: "estimated_generation_only",
    cost_basis: "Generation list-price estimate only.",
  },
  error: null,
  limitations: ["Synthetic content."],
};

function jsonResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
  } as Response;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("Nordly PASS A and B", () => {
  it("loads its two visible questions from the backend-owned demo world", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(demoWorld)));

    render(<App />);

    expect(await screen.findByRole("button", { name: /återbetalning verkar ha tagit semester/i })).toBeVisible();
    expect(screen.getByRole("button", { name: salaryQuestion })).toBeVisible();
    expect(screen.getByRole("button", { name: "SV" })).toHaveAttribute("aria-pressed", "true");
  });

  it("shows only real backend phases and zero-use receipt after a blocked salary question", async () => {
    let releaseRag: ((response: Response) => void) | undefined;
    const ragResponse = new Promise<Response>((resolve) => {
      releaseRag = resolve;
    });

    vi.stubGlobal(
      "fetch",
      vi.fn(async (input: RequestInfo | URL) => {
        const path = String(input);
        if (path.includes("/runs/rag")) return ragResponse;
        return jsonResponse(demoWorld);
      }),
    );

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: salaryQuestion }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(screen.getByText("Nordly skickar frågan…")).toBeVisible();
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveFocus(),
    );
    releaseRag?.(jsonResponse(blockedResponse));

    expect(await screen.findByRole("heading", { name: "Bra. Systemet stannade." })).toBeVisible();
    expect(screen.getByText(/privat personalinformation/i)).toBeVisible();
    expect(screen.getByText("[MASKERAD]")).toBeVisible();
    expect(screen.queryByText(salaryQuestion)).not.toBeInTheDocument();
    expect(screen.getAllByText("Semantisk sökning")[0]).toBeVisible();

    const aiCalls = screen.getByText("AI-anrop").parentElement;
    expect(aiCalls).not.toBeNull();
    expect(within(aiCalls as HTMLElement).getByText("0")).toBeVisible();

    const embeddings = screen.getByText("Embeddings").parentElement;
    expect(embeddings).not.toBeNull();
    expect(within(embeddings as HTMLElement).getByText("0")).toBeVisible();
  });

  it("requires explicit consent, waits honestly, then reveals the full returned AI chain", async () => {
    let ragCall = 0;
    let releaseLive: ((response: Response) => void) | undefined;
    const liveResponse = new Promise<Response>((resolve) => {
      releaseLive = resolve;
    });

    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      const path = String(input);
      if (!path.includes("/runs/rag")) return jsonResponse(demoWorld);
      ragCall += 1;
      return ragCall === 1 ? jsonResponse(confirmationResponse) : liveResponse;
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: safeQuestion }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(
      await screen.findByRole("heading", { name: "Frågan får försöka gå vidare." }),
    ).toBeVisible();
    expect(screen.getByText(/0 AI-anrop hittills/i)).toBeVisible();
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Kör hela AI-flödet" })).toHaveFocus(),
    );

    await user.click(screen.getByRole("button", { name: "Kör hela AI-flödet" }));

    expect(screen.getByText("Det bekräftade backendanropet är skickat…")).toBeVisible();
    expect(screen.queryByText("Frågans betydelse blev en vektor.")).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole("status")).toHaveFocus(),
    );

    releaseLive?.(jsonResponse(answeredResponse));

    expect(
      await screen.findByRole("heading", {
        name: "Nordly svarade från hämtad företagskunskap.",
      }),
    ).toBeVisible();
    expect(screen.getByText(/banken brukar visa beloppet inom två till fem bankdagar/i)).toBeVisible();
    expect(screen.getByRole("heading", { name: "Returns and card refunds" })).toBeVisible();
    expect(screen.getByText(/semantisk likhet:/i)).toHaveTextContent("75,9 %");
    await waitFor(() =>
      expect(
        screen.getByRole("heading", {
          name: "Nordly svarade från hämtad företagskunskap.",
        }),
      ).toHaveFocus(),
    );

    const liveRequest = fetchMock.mock.calls.find(([, init]) =>
      String((init as RequestInit | undefined)?.body).includes('"confirm_live_ai":true'),
    );
    expect(liveRequest).toBeDefined();
    expect(String((liveRequest?.[1] as RequestInit).body)).toContain(safeQuestion);

    await user.click(screen.getByRole("button", { name: "Se hur svaret togs fram" }));

    expect(screen.getByRole("heading", { name: "Från fråga till kontrollerat svar." })).toBeVisible();
    await waitFor(() =>
      expect(
        screen.getByRole("heading", { name: "Från fråga till kontrollerat svar." }),
      ).toHaveFocus(),
    );
    expect(screen.getByRole("heading", { name: "Frågans betydelse blev en vektor." })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Liknande betydelse hittades i pgvector." })).toBeVisible();
    expect(screen.getByRole("heading", { name: "RAG byggde ett litet källpaket." })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Gemini formulerade svaret." })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Java kontrollerade innan visning." })).toBeVisible();
    expect(screen.getByText(/inte AI:ns dolda tankar/i)).toBeVisible();

    await user.click(screen.getByText("Tekniskt körningskvitto"));
    expect(screen.getByText("gemini-3.1-flash-lite")).toBeVisible();
    expect(screen.getByText(/embeddingkostnaden ingår inte/i)).toBeVisible();
  });
});
