import { cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import type {
  DemoCustomerChatTurnResponse,
  DemoOrderCatalogResponse,
  DemoOrderLookupResponse,
  DemoWorldResponse,
  KnowledgeDocumentLibraryResponse,
  KnowledgeRagResponse,
} from "./api/generated";

const salaryQuestion = "Kan jag få reda på vad någon på Nordly har i lön?";
const corpusSha256 =
  "9ba9e0aeac7d6c090e3a6f6be9779f1734dce56febd032ee50616ec6af72e30a";
const chunkSha256 = "b".repeat(64);

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
    {
      id: "no-match-gift-card-extension",
      category: "NO_MATCH",
      prompt_sv: "Kan Nordly förlänga presentkort från 24 till 36 månader?",
      prompt_en: "Can Nordly extend gift cards from 24 to 36 months?",
      expected_boundary: "insufficient_evidence",
    },
  ],
  featured_scenario_id: "checkout-orders-at-risk-v1",
  corpus: {
    version: "nordly-knowledge-corpus-v2",
    document_count: 16,
    chunk_count: 30,
    approved_documents: 14,
    deprecated_documents: 1,
    untrusted_documents: 1,
  },
};

const demoOrder = {
  order_id: "NORD-2048",
  market: "SE",
  item_count: 2,
  created_at: "2026-09-12T08:14:00Z",
  updated_at: "2026-09-12T10:42:00Z",
  estimated_delivery_from: "2026-09-15",
  estimated_delivery_through: "2026-09-17",
  status_code: "packing",
  status_sv: "Packas",
  status_en: "Packing",
  payment_state: "authorized",
  fulfilment_state: "picking",
  summary_sv: "Betalningen är godkänd och två syntetiska produkter plockas på Nordlys demolager.",
  summary_en: "Payment is authorized and two synthetic products are being picked in Nordly's demo warehouse.",
  next_step_sv: "Nästa registrerade steg är att lagret lämnar paketet till transportören.",
  next_step_en: "The next recorded step is for the warehouse to hand the parcel to the carrier.",
  source_ref: "demo/nordly-demo-orders-v1#NORD-2048",
  evidence_id: "nordly-demo-order-2048-snapshot",
};

const demoOrderReceipt = {
  operation: "get_demo_order" as const,
  read_operations: 1 as const,
  records_returned: 1,
  write_operations: 0 as const,
  ai_calls: 0 as const,
  write_tools_available: false as const,
  action_executed: false as const,
};

const demoOrderCatalog: DemoOrderCatalogResponse = {
  contract_version: "nordly-demo-order-catalog-v1",
  mode: "read_only_synthetic_order_catalog",
  truth_label: "SYNTETISKA DEMOORDER",
  truth_label_en: "SYNTHETIC DEMO ORDERS",
  catalog_version: "nordly-demo-orders-v1",
  snapshot_at: "2026-09-15T10:00:00Z",
  synthetic_only: true,
  order_count: 1,
  orders: [demoOrder],
  action_receipt: { ...demoOrderReceipt, operation: "list_demo_orders" },
  limitations: [],
};

const demoOrderLookup: DemoOrderLookupResponse = {
  contract_version: "nordly-demo-order-lookup-v1",
  mode: "read_only_synthetic_order_lookup",
  truth_label: "SYNTETISKA DEMOORDER · INGEN KUNDDATA · INGEN AI · INGA ÄNDRINGAR",
  truth_label_en: "SYNTHETIC DEMO ORDERS · NO CUSTOMER DATA · NO AI · NO CHANGES",
  catalog_version: "nordly-demo-orders-v1",
  snapshot_at: "2026-09-15T10:00:00Z",
  synthetic_only: true,
  order: demoOrder,
  action_receipt: demoOrderReceipt,
  limitations: [],
};

const blockedResponse: KnowledgeRagResponse = {
  contract_version: "nordly-knowledge-rag-v3",
  run_id: "rag-run-test-1",
  mode: "live_rag",
  truth_label: "FIKTIV DATA · VERKLIG BACKEND",
  truth_label_en: "FICTIONAL DATA · REAL BACKEND",
  outcome: "refused",
  provider_route: null,
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
    corpus_version: "nordly-knowledge-corpus-v2",
    corpus_content_sha256: corpusSha256,
    index_snapshot: null,
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
    eligible_document_count: 13,
    eligible_chunk_count: 27,
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
    evaluation_status: "not_applicable",
    schema_pass: true,
    citations_within_retrieved_context: true,
    approved_documents_only: true,
    output_pii_scan_pass: true,
    output_policy_scan_pass: true,
    semantic_claim_support_evaluated: false,
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
  contract_version: "nordly-knowledge-rag-v3",
  run_id: "nordly-rag-live-test-1",
  mode: "live_rag",
  truth_label: "FIKTIV DATA · VERKLIG BACKEND",
  truth_label_en: "FICTIONAL DATA · REAL BACKEND",
  outcome: "answered",
  provider_route: {
    transport: "developer_api",
    authentication_mode: "api_key",
    location: null,
  },
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
    corpus_version: "nordly-knowledge-corpus-v2",
    corpus_content_sha256: corpusSha256,
    index_snapshot: {
      status: "ready",
      ready: true,
      indexed_chunks: 27,
      current_chunks: 27,
      expected_chunks: 27,
    },
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
    eligible_document_count: 13,
    eligible_chunk_count: 27,
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
        content_sha256: chunkSha256,
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
    evaluation_status: "completed",
    schema_pass: true,
    citations_within_retrieved_context: true,
    approved_documents_only: true,
    output_pii_scan_pass: true,
    output_policy_scan_pass: true,
    semantic_claim_support_evaluated: false,
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

const noMatchQuestion = demoWorld.rag_examples.find(
  (example) => example.category === "NO_MATCH",
)?.prompt_sv as string;

const noMatchResponse: KnowledgeRagResponse = {
  ...answeredResponse,
  run_id: "nordly-rag-no-match-test-1",
  outcome: "insufficient_evidence",
  question: { text: noMatchQuestion, locale: "sv", redacted: false },
  phases: answeredResponse.phases.map((phase) =>
    ["bounded_context", "generation", "java_verification"].includes(phase.id)
      ? {
          ...phase,
          status: "skipped" as const,
          executed: false,
          latency_ms: null,
        }
      : phase,
  ),
  retrieval: {
    ...answeredResponse.retrieval,
    ranked_matches: [],
  },
  answer: null,
  verification: {
    ...answeredResponse.verification,
    evaluation_status: "not_applicable",
    overall_outcome: "insufficient_evidence_before_generation",
  },
  receipt: {
    ...answeredResponse.receipt,
    provider_calls: 1,
    embedding_calls: 1,
    generation_calls: 0,
    model_id: null,
    provider_response_id: null,
    token_usage: null,
    estimated_cost_usd: null,
    cost_status: "not_reported",
    cost_basis: "Only query embedding executed.",
  },
  error: {
    code: "NO_MATCH_ABOVE_THRESHOLD",
    summary_sv: "Ingen godkänd källa gav tillräckligt stöd för att svara.",
    summary_en: "No approved source provided enough support to answer.",
  },
};

const documentLibrary: KnowledgeDocumentLibraryResponse = {
  contract_version: "nordly-knowledge-document-library-v2",
  mode: "read_only_corpus",
  truth_label: "FIKTIVA AI-SKAPADE FÖRETAGSDOKUMENT",
  manifest_version: "nordly-knowledge-manifest-v2",
  corpus_version: "nordly-knowledge-corpus-v2",
  corpus_content_sha256: corpusSha256,
  synthetic_only: true,
  current_vector_search: false,
  document_count: 16,
  chunk_count: 30,
  eligible_document_count: 13,
  eligible_chunk_count: 27,
  eligibility_rule: {
    required_lifecycle: "APPROVED",
    required_access_scope: "public_demo",
  },
  embedding_profile: {
    provider: "google_genai",
    model_id: "gemini-embedding-2",
    dimensions: 768,
  },
  documents: [
    {
      id: "kb-returns-refunds",
      version: "2.0",
      display_filename: "NLY-CX-102_returns-and-card-refunds.md",
      document_type: "customer_policy",
      classification: "PUBLIC_DEMO",
      title: "Returns and card refunds",
      title_sv: "Returer och kortåterbetalningar",
      summary_sv: "Nordlys interna kundservicepolicy för returer och kortåterbetalningar.",
      summary_en: "Nordly's internal customer-service policy for returns and card refunds.",
      owner_team: "Customer Operations",
      lifecycle: "APPROVED",
      effective_from: "2026-01-15",
      effective_until: null,
      access_scopes: ["public_demo"],
      related_document_ids: [],
      rag_eligibility: {
        eligible: true,
        reason_code: "APPROVED_PUBLIC_DEMO",
        summary_sv: "Dokumentet får delta i semantisk rankning.",
        summary_en: "The document may enter semantic ranking.",
      },
      content_visible: true,
      chunks: [
        {
          id: "return-eligibility",
          section_heading: "Return eligibility",
          source_ref: "knowledge/kb-returns-refunds#return-eligibility",
          evidence_id: "nordly-evidence-return-eligibility",
          display_summary_sv: "Returrätt avgörs i ett autentiserat supportflöde.",
          display_summary_en: "Return eligibility is decided in an authenticated support flow.",
          text: "Return eligibility is decided in the authenticated support workflow.",
          content_sha256: "a".repeat(64),
        },
        {
          id: "refund-timing-card",
          section_heading: "When an approved card refund appears",
          source_ref: "knowledge/kb-returns-refunds#refund-timing-card",
          evidence_id: "nordly-evidence-refund-timing-card",
          display_summary_sv: "Godkända kortåterbetalningar syns inom två till fem bankdagar.",
          display_summary_en: "Approved card refunds appear within two to five banking days.",
          text: answeredResponse.retrieval.ranked_matches[0].text,
          content_sha256: chunkSha256,
        },
      ],
    },
    {
      id: "kb-employee-compensation-register",
      version: "1.0",
      display_filename: "NLY-PEOPLE-901_employee-compensation-register.md",
      document_type: "people_register",
      classification: "RESTRICTED_SYNTHETIC",
      title: "Employee compensation register",
      title_sv: "Löneregister för medarbetare",
      summary_sv: "Syntetiskt HR-underlag som aldrig får användas i den publika assistenten.",
      summary_en: "Synthetic HR material that must never be used by the public assistant.",
      owner_team: "People Operations",
      lifecycle: "APPROVED",
      effective_from: "2026-01-01",
      effective_until: null,
      access_scopes: ["people_confidential"],
      related_document_ids: [],
      rag_eligibility: {
        eligible: false,
        reason_code: "PUBLIC_DEMO_SCOPE_MISSING",
        summary_sv: "Dokumentet saknar åtkomstområdet public_demo.",
        summary_en: "The document lacks the public_demo access scope.",
      },
      content_visible: false,
      chunks: [
        {
          id: "restricted-compensation",
          section_heading: "Restricted compensation data",
          source_ref: "knowledge/kb-employee-compensation-register#restricted-compensation",
          evidence_id: "nordly-evidence-restricted-compensation",
          display_summary_sv: "Skyddat testunderlag.",
          display_summary_en: "Protected test material.",
          text: null,
          content_sha256: null,
        },
      ],
    },
    {
      id: "kb-legacy-refund-playbook",
      version: "0.9",
      display_filename: "NLY-LEGACY-017_refund-playbook.md",
      document_type: "legacy_playbook",
      classification: "SAFETY_FIXTURE",
      title: "Legacy refund playbook",
      title_sv: "Arkiverad återbetalningsrutin",
      summary_sv: "Utfasad testversion som ska väljas bort från kunskapssökningen.",
      summary_en: "Deprecated test version that must be excluded from knowledge retrieval.",
      owner_team: "Customer Operations",
      lifecycle: "DEPRECATED",
      effective_from: "2025-01-01",
      effective_until: "2025-12-31",
      access_scopes: ["safety_eval_only"],
      related_document_ids: ["kb-returns-refunds"],
      rag_eligibility: {
        eligible: false,
        reason_code: "LIFECYCLE_NOT_APPROVED",
        summary_sv: "Dokumentets livscykel är inte APPROVED.",
        summary_en: "The document lifecycle is not APPROVED.",
      },
      content_visible: false,
      chunks: [
        {
          id: "legacy-refund-rule",
          section_heading: "Deprecated refund rule",
          source_ref: "knowledge/kb-legacy-refund-playbook#legacy-refund-rule",
          evidence_id: "nordly-evidence-legacy-refund-rule",
          display_summary_sv: "Utfasat testunderlag.",
          display_summary_en: "Deprecated test material.",
          text: null,
          content_sha256: null,
        },
      ],
    },
  ],
};

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(),
    json: async () => body,
  } as Response;
}

const customerOrder = {
  ...demoOrder,
  order_id: "NORD-2051",
  market: "DK",
  item_count: 1,
  estimated_delivery_from: "2026-09-18",
  estimated_delivery_through: "2026-09-21",
  status_code: "shipped",
  status_sv: "Skickad",
  status_en: "Shipped",
  payment_state: "captured",
  fulfilment_state: "carrier_handover_recorded",
  summary_sv: "En vara är överlämnad till transportören.",
  summary_en: "One item has been handed to the carrier.",
  next_step_sv: "Nästa registrerade steg är en ny transportörshändelse.",
  next_step_en: "The next recorded step is a new carrier event.",
  source_ref: "demo/nordly-demo-orders-v1#NORD-2051",
  evidence_id: "nordly-demo-order-2051-snapshot",
};

const customerOrderResponse: DemoCustomerChatTurnResponse = {
  contract_version: "nordly-demo-customer-chat-turn-v1",
  turn_id: "customer-turn-order",
  mode: "controlled_synthetic_customer_chat",
  truth_label: "SYNTETISK KUND · FAST BACKENDKONTEXT · INGET PROVIDERANROP",
  truth_label_en: "SYNTHETIC CUSTOMER · FIXED BACKEND CONTEXT · NO PROVIDER CALL",
  outcome: "answered",
  submitted_message: { text: "Var är min order?", locale: "sv", redacted: false },
  context: {
    context_version: "nordly-demo-customer-v1",
    context_id: "fixed-demo-customer",
    current_order_id: "NORD-2051",
    context_source_ref: "demo/nordly-demo-customer-v1#current-order",
    order_source_ref: customerOrder.source_ref,
    synthetic_only: true,
    persistent_memory: false,
    memory_scope: "request_scoped_bounded_history",
  },
  intent: {
    name: "order_status",
    classifier: "deterministic_rules_v2",
    action_requested: false,
  },
  safety: {
    decision: "ALLOW",
    reason_code: "NONE",
    summary_sv: "Frågan passerade säkerhetskontrollen.",
    summary_en: "The question passed the safety check.",
  },
  assistant_message: {
    text_sv:
      "Hej! Jag har kontrollerat din order NORD-2051. Orderstatus: Skickad. Beräknad leverans är 18–21 september. Ordern innehåller 1 vara.",
    text_en:
      "Hi! I checked your order NORD-2051. Order status: Shipped. Estimated delivery is September 18–21. The order contains 1 item.",
  },
  order: customerOrder,
  tool_events: [
    {
      sequence: 1,
      type: "safety",
      initiated_by: "spring_orchestrator",
      model_selected: false,
      name: "screen_customer_message",
      status: "completed",
      executed: true,
      summary_sv: "Frågan passerade säkerhetskontrollen.",
      summary_en: "The question passed the safety check.",
      source_ref: null,
      evidence_ids: [],
      latency_ms: null,
    },
    {
      sequence: 2,
      type: "backend_read",
      initiated_by: "spring_orchestrator",
      model_selected: false,
      name: "get_current_order",
      status: "completed",
      executed: true,
      summary_sv: "Läste demokundens aktuella order från backendkatalogen.",
      summary_en: "Read the demo customer's current order from the backend catalog.",
      source_ref: customerOrder.source_ref,
      evidence_ids: [customerOrder.evidence_id],
      latency_ms: 1,
    },
  ],
  sources: [
    {
      kind: "order_snapshot",
      document_id: null,
      chunk_id: null,
      document_version: "nordly-demo-orders-v1",
      title: "Synthetic current order snapshot",
      section_heading: "shipped",
      source_ref: customerOrder.source_ref,
      evidence_id: customerOrder.evidence_id,
      lifecycle: "VERSIONED_FIXTURE",
      similarity: null,
      display_summary_sv: customerOrder.summary_sv,
      display_summary_en: customerOrder.summary_en,
    },
  ],
  verified_claims: [
    {
      text_sv: "Ordern är skickad.",
      text_en: "The order has shipped.",
      citation_ids: [customerOrder.evidence_id],
    },
  ],
  rag: {
    requested: false,
    outcome: "not_run",
    backend: null,
    corpus_version: null,
    corpus_content_sha256: null,
    current_vector_search: false,
    embedding_executed: false,
    embedding_provider: null,
    embedding_model_id: null,
    embedding_dimensions: null,
    vector_match_count: 0,
    verification_outcome: "not_applicable",
    provider_route: null,
    generation_model_id: null,
    provider_response_id: null,
    token_usage: null,
    error_code: null,
  },
  verification: {
    evaluation_status: "completed",
    fixed_customer_scope: true,
    order_source_verified: true,
    approved_policies_only: false,
    citations_within_returned_sources: true,
    semantic_claim_support_evaluated: false,
    no_business_write_capability: true,
    business_action_executed: false,
    overall_outcome: "released_exact_order_snapshot",
  },
  receipt: {
    read_operations: 1,
    provider_calls: 0,
    embedding_calls: 0,
    vector_searches: 0,
    generation_calls: 0,
    business_write_operations: 0,
    business_write_scope: "customer_order_and_refund_state",
    business_write_tools_available: false,
    business_action_executed: false,
    persistent_memory_used: false,
    total_latency_ms: 4,
    model_id: null,
    estimated_cost_usd: null,
    cost_status: "not_incurred",
    cost_basis: "No provider call was made.",
  },
  error: null,
  limitations: [],
};

const customerCancelResponse: DemoCustomerChatTurnResponse = {
  ...customerOrderResponse,
  turn_id: "customer-turn-cancel",
  outcome: "outside_authority",
  submitted_message: { text: "Jag vill avbryta den", locale: "sv", redacted: false },
  intent: {
    name: "cancel_order",
    classifier: "deterministic_rules_v2",
    action_requested: true,
  },
  safety: {
    decision: "BLOCK",
    reason_code: "WRITE_ACTION",
    summary_sv: "AI:n är read-only och får inte ändra företagets system.",
    summary_en: "The AI is read-only and cannot change company systems.",
  },
  assistant_message: {
    text_sv:
      "Jag förstår att du vill avbeställa. Det ligger utanför min befogenhet och ingen ändring gjordes. Ring 123 så hjälper vi dig vidare.",
    text_en:
      "I understand that you want to cancel. That is outside my authority and no change was made. Call 123 for further help.",
  },
  tool_events: [
    {
      ...customerOrderResponse.tool_events[0],
      status: "blocked",
      summary_sv: "Åtgärden stoppades av säkerhetskontrollen.",
      summary_en: "The action was stopped by the safety check.",
    },
    customerOrderResponse.tool_events[1],
  ],
  receipt: { ...customerOrderResponse.receipt, read_operations: 3 },
  verification: {
    ...customerOrderResponse.verification,
    approved_policies_only: true,
    overall_outcome: "released_outside_authority",
  },
};

const customerBlockedResponse: DemoCustomerChatTurnResponse = {
  ...customerOrderResponse,
  turn_id: "customer-turn-blocked",
  outcome: "refused",
  submitted_message: {
    text: "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]",
    locale: "sv",
    redacted: true,
  },
  intent: {
    name: "unsupported",
    classifier: "deterministic_rules_v2",
    action_requested: false,
  },
  safety: {
    decision: "BLOCK",
    reason_code: "EMPLOYEE_COMPENSATION_REQUEST",
    summary_sv: "Frågan stoppades före kunddata, dokument och AI.",
    summary_en: "The question was stopped before customer data, documents and AI.",
  },
  assistant_message: {
    text_sv: "Jag kan inte lämna ut privat information om en medarbetares lön.",
    text_en: "I cannot disclose private information about an employee's salary.",
  },
  order: null,
  tool_events: [{
    ...customerOrderResponse.tool_events[0],
    status: "blocked",
    summary_sv: "Frågan stoppades före kunddata, dokument och AI.",
    summary_en: "The question was stopped before customer data, documents and AI.",
  }],
  sources: [],
  verified_claims: [],
  verification: {
    ...customerOrderResponse.verification,
    evaluation_status: "not_applicable",
    order_source_verified: false,
    citations_within_returned_sources: false,
    overall_outcome: "refused_before_customer_tools",
  },
  receipt: { ...customerOrderResponse.receipt, read_operations: 0 },
};

const customerDownstreamResponse: DemoCustomerChatTurnResponse = {
  ...customerOrderResponse,
  turn_id: "customer-turn-downstream",
  outcome: "unavailable",
  submitted_message: {
    text: "Hur arbetar Nordlys kundservice?",
    locale: "sv",
    redacted: false,
  },
  intent: {
    name: "company_knowledge",
    classifier: "gemini_function_router",
    action_requested: false,
  },
  assistant_message: {
    text_sv:
      "Jag förstod frågan, men kunde inte starta den skyddade informationshämtningen just nu. Jag vill inte gissa. Ingen ändring gjordes.",
    text_en:
      "I understood the question, but could not start the protected information retrieval right now. I will not guess. No change was made.",
  },
  order: null,
  tool_events: [
    customerOrderResponse.tool_events[0],
    {
      sequence: 2,
      type: "model_routing",
      initiated_by: "gemini_customer_router",
      model_selected: true,
      name: "search_approved_company_knowledge",
      status: "completed",
      executed: true,
      summary_sv: "AI:n valde en avgränsad kunskapssökning.",
      summary_en: "AI selected a bounded knowledge search.",
      source_ref: null,
      evidence_ids: [],
      latency_ms: 22,
    },
    {
      sequence: 3,
      type: "backend_step",
      initiated_by: "spring_orchestrator",
      model_selected: false,
      name: "run_selected_customer_capability",
      status: "failed",
      executed: false,
      summary_sv: "Nästa skyddade steg kunde inte starta.",
      summary_en: "The next protected step could not start.",
      source_ref: null,
      evidence_ids: [],
      latency_ms: null,
    },
  ],
  sources: [],
  verified_claims: [],
  verification: {
    ...customerOrderResponse.verification,
    evaluation_status: "not_run",
    order_source_verified: false,
    citations_within_returned_sources: false,
    overall_outcome: "not_released_downstream_live_boundary",
  },
  receipt: {
    ...customerOrderResponse.receipt,
    read_operations: 0,
    provider_calls: 1,
    generation_calls: 1,
    model_id: "gemini-3.1-flash-lite-preview",
  },
  error: {
    code: "CUSTOMER_CHAT_DOWNSTREAM_LIVE_BUSY",
    summary_sv: "Frågan tolkades, men det valda live-steget stoppades av systemets skyddsgräns.",
    summary_en: "The question was routed, but the selected live step was stopped by the system boundary.",
  },
};

const customerConfirmationResponse: DemoCustomerChatTurnResponse = {
  ...customerOrderResponse,
  turn_id: "customer-turn-confirmation",
  outcome: "confirmation_required",
  submitted_message: { text: "Kan jag få pengarna tillbaka?", locale: "sv", redacted: false },
  intent: {
    name: "refund_policy",
    classifier: "deterministic_rules_v2",
    action_requested: false,
  },
  assistant_message: {
    text_sv: "Jag behöver söka i Nordlys företagsdokument för att svara säkert. Vill du fortsätta?",
    text_en: "I need to search Nordly's company documents to answer safely. Would you like to continue?",
  },
  rag: {
    ...customerOrderResponse.rag,
    requested: true,
    outcome: "confirmation_required",
    backend: "pgvector_exact_cosine",
    corpus_version: "nordly-knowledge-corpus-v2",
    corpus_content_sha256: corpusSha256,
    verification_outcome: "not_run_confirmation_required",
  },
  error: {
    code: "LIVE_AI_CONFIRMATION_REQUIRED",
    summary_sv: "Bekräfta den begränsade livekörningen först.",
    summary_en: "Confirm the bounded live run first.",
  },
};

const customerRagResponse: DemoCustomerChatTurnResponse = {
  ...customerConfirmationResponse,
  turn_id: "customer-turn-rag",
  outcome: "answered",
  assistant_message: {
    text_sv: "När en återbetalning har godkänts brukar banken visa beloppet inom två till fem bankdagar.",
    text_en: "Once a refund is approved, the bank normally displays it within two to five banking days.",
  },
  tool_events: [
    customerOrderResponse.tool_events[0],
    {
      sequence: 2,
      type: "embedding",
      initiated_by: "knowledge_rag_service",
      model_selected: false,
      name: "embed_policy_question",
      status: "completed",
      executed: true,
      summary_sv: "Frågans betydelse kodades med Gemini embedding.",
      summary_en: "The question meaning was encoded with a Gemini embedding.",
      source_ref: null,
      evidence_ids: [],
      latency_ms: 20,
    },
    {
      sequence: 3,
      type: "vector_search",
      initiated_by: "knowledge_rag_service",
      model_selected: false,
      name: "semantic_search_policy",
      status: "completed",
      executed: true,
      summary_sv: "pgvector hittade ett godkänt policyavsnitt.",
      summary_en: "pgvector found an approved policy passage.",
      source_ref: "knowledge/kb-returns-refunds#refund-timing-card",
      evidence_ids: ["nordly-evidence-refund-timing-card"],
      latency_ms: 8,
    },
  ],
  sources: [
    {
      kind: "company_policy",
      document_id: "kb-returns-refunds",
      chunk_id: "refund-timing-card",
      document_version: "2.0",
      title: "Returns and card refunds",
      section_heading: "When an approved card refund appears",
      source_ref: "knowledge/kb-returns-refunds#refund-timing-card",
      evidence_id: "nordly-evidence-refund-timing-card",
      lifecycle: "APPROVED",
      similarity: 0.759,
      display_summary_sv: "Godkända kortåterbetalningar syns inom två till fem bankdagar.",
      display_summary_en: "Approved card refunds appear within two to five banking days.",
    },
  ],
  verified_claims: [{
    text_sv: "Banken behöver normalt två till fem bankdagar.",
    text_en: "The bank normally needs two to five banking days.",
    citation_ids: ["nordly-evidence-refund-timing-card"],
  }],
  rag: {
    ...customerConfirmationResponse.rag,
    outcome: "answered",
    current_vector_search: true,
    embedding_executed: true,
    embedding_provider: "google_genai",
    embedding_model_id: "gemini-embedding-2",
    embedding_dimensions: 768,
    vector_match_count: 1,
    verification_outcome: "answered_with_verified_retrieved_citations",
    generation_model_id: "gemini-3.1-flash-lite",
  },
  verification: {
    ...customerOrderResponse.verification,
    approved_policies_only: true,
    citations_within_returned_sources: true,
    overall_outcome: "answered_with_verified_retrieved_citations",
  },
  receipt: {
    ...customerOrderResponse.receipt,
    read_operations: 2,
    provider_calls: 2,
    embedding_calls: 1,
    vector_searches: 1,
    generation_calls: 1,
    model_id: "gemini-3.1-flash-lite",
  },
  error: null,
};

function readOnlyBackendResponse(input: RequestInfo | URL) {
  const path = String(input);
  if (path.includes("/api/v1/knowledge/documents")) return jsonResponse(documentLibrary);
  if (path.endsWith("/api/v1/demo-orders")) return jsonResponse(demoOrderCatalog);
  return jsonResponse(demoWorld);
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.sessionStorage.clear();
  window.history.replaceState(null, "", "#customer-help");
});

describe("Nordly PASS A and B", () => {
  it("shows the backend-owned company archive as a third scene and opens only eligible documents", async () => {
    window.history.replaceState(null, "", "#documents");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => readOnlyBackendResponse(input)));

    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByRole("link", { name: /Dokumentarkiv/i })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("heading", { name: "Nordlys dokumentarkiv." })).toBeVisible();
    expect(await screen.findByText("Returer och kortåterbetalningar")).toBeVisible();
    expect(screen.getByText("Löneregister för medarbetare")).toBeVisible();
    expect(screen.getByText("Arkiverad återbetalningsrutin")).toBeVisible();
    expect(screen.getByText("Används av AI")).toBeVisible();
    expect(screen.getByText("Skyddad källa")).toBeVisible();
    expect(screen.getByText("Ej aktiv källa")).toBeVisible();
    expect(screen.queryByText("Customer Operations")).not.toBeInTheDocument();
    expect(screen.queryByText(/APPROVED \+ public_demo/i)).not.toBeInTheDocument();
    expect(screen.queryByText("2.0")).not.toBeInTheDocument();

    expect(
      screen.queryByRole("button", { name: /Öppna dokument: Löneregister för medarbetare/i }),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Öppna dokument: Returer och kortåterbetalningar" }),
    );
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Returer och kortåterbetalningar" })).toBeVisible();
    expect(within(dialog).getByText(/authenticated support workflow/i)).toBeVisible();
    expect(within(dialog).queryByText("RAG hämtade denna passage")).not.toBeInTheDocument();
  });

  it("translates the document archive without replacing backend content", async () => {
    window.history.replaceState(null, "", "#documents");
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => readOnlyBackendResponse(input)));

    const user = userEvent.setup();
    render(<App />);
    await screen.findByText("Returer och kortåterbetalningar");
    await user.click(screen.getByRole("button", { name: "EN" }));

    expect(screen.getByRole("heading", { name: "Nordly's document archive." })).toBeVisible();
    expect(screen.getByText("Returns and card refunds")).toBeVisible();
    expect(screen.getByText("Employee compensation register")).toBeVisible();
    expect(screen.getByText("Used by AI")).toBeVisible();
    expect(screen.getByText("Protected source")).toBeVisible();
    expect(screen.getByText("Inactive source")).toBeVisible();
  });

  it("frames Nordly as a friendly, bounded customer assistant", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => readOnlyBackendResponse(input)));

    render(<App />);

    expect(screen.getByRole("link", { name: "Shirwac Abib, Applied AI" })).toBeVisible();
    expect(screen.getAllByText("PERSONLIGT APPLIED AI-ARBETSPROV")[0]).toBeVisible();
    expect(screen.getByText("Nordly Kundhjälp")).toBeVisible();
    expect(screen.getByText("Endast läsning")).toBeVisible();
    expect(screen.getByText(/Kända högriskfrågor stoppas före AI/i)).toBeVisible();
    expect(screen.getByText(/skrivverktyg saknas/i)).toBeVisible();
    expect(screen.getByText(/Jag kan läsa information, men aldrig ändra en order/i)).toBeVisible();
    expect(screen.getByText(/Fiktiv miljö · endast syntetisk data/i)).toBeVisible();
    expect(await screen.findByRole("button", { name: "Var är min order?" })).toBeVisible();
  });

  it("combines practical customer prompts with the backend-owned safety example", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => readOnlyBackendResponse(input)));

    render(<App />);

    expect(await screen.findByRole("button", { name: "Var är min order?" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Jag vill avbryta min order" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Kan jag få pengarna tillbaka?" })).toBeVisible();
    expect(screen.getByRole("button", { name: salaryQuestion })).toBeVisible();
  });

  it("gets natural order status through the dedicated backend tool and shows its receipt", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        return jsonResponse(customerOrderResponse);
      }
      return readOnlyBackendResponse(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Var är min order?" }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    const reply = await screen.findByLabelText("Svar från Nordly");
    expect(reply).toHaveTextContent(/Jag har kontrollerat din order NORD-2051/i);
    expect(reply).toHaveTextContent("Skickad");
    expect(reply).toHaveTextContent("18 september 2026–21 september 2026");
    expect(screen.queryByText(customerOrder.source_ref)).not.toBeInTheDocument();

    const chatCall = fetchMock.mock.calls.find(([input]) =>
      String(input).endsWith("/api/v1/demo-customer/chat/turns"),
    );
    expect(chatCall).toBeDefined();
    expect(String((chatCall?.[1] as RequestInit).body)).toContain('"message":"Var är min order?"');
    expect(String((chatCall?.[1] as RequestInit).body)).toContain('"confirm_live_ai":true');

    await user.click(screen.getByRole("button", { name: "Så gick det till" }));
    expect(screen.getByText(/Läste demokundens aktuella order/i)).toBeVisible();
    expect(screen.getByText(customerOrder.source_ref)).toBeVisible();
    expect(screen.getByText(/RAG behövdes inte/i)).toBeVisible();
    const receipt = screen.getByLabelText("Körningskvitto");
    expect(within(within(receipt).getByText("Backendläsningar").parentElement as HTMLElement).getByText("1")).toBeVisible();
    expect(within(within(receipt).getByText("Ändringar").parentElement as HTMLElement).getByText("0")).toBeVisible();
  });

  it("explains a downstream stop without claiming that no AI ran", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        return jsonResponse(customerDownstreamResponse);
      }
      return readOnlyBackendResponse(input);
    }));

    const user = userEvent.setup();
    render(<App />);

    await user.type(
      screen.getByRole("textbox", { name: "Skriv en fråga till Nordly…" }),
      "Hur arbetar Nordlys kundservice?",
    );
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    await screen.findByText(/kunde inte starta den skyddade informationshämtningen/i);

    await user.click(screen.getByRole("button", { name: "Så gick det till" }));
    expect(screen.getByText(/AI:n tolkade frågan/i)).toBeVisible();
    expect(screen.getByText(/innan något verktyg hann läsa data/i)).toBeVisible();
    expect(screen.queryByText(/Inga verktyg eller AI behövdes/i)).not.toBeInTheDocument();
    const receipt = screen.getByLabelText("Körningskvitto");
    expect(within(within(receipt).getByText("AI-anrop").parentElement as HTMLElement).getByText("1")).toBeVisible();
  });

  it("keeps a real transcript and safely answers a cancellation follow-up", async () => {
    let chatCall = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        chatCall += 1;
        return jsonResponse(chatCall === 1 ? customerOrderResponse : customerCancelResponse);
      }
      return readOnlyBackendResponse(input);
    }));

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Var är min order?" }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    await screen.findByText(/Jag har kontrollerat din order NORD-2051/i);

    const input = screen.getByRole("textbox", { name: "Skriv en fråga till Nordly…" });
    await user.type(input, "Jag vill avbryta den");
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(await screen.findByText(/utanför min befogenhet/i)).toBeVisible();
    expect(screen.getByText("Var är min order?")).toBeVisible();
    expect(screen.getByText("Jag vill avbryta den")).toBeVisible();
    expect(screen.getAllByLabelText("Svar från Nordly")).toHaveLength(2);

    const disclosures = screen.getAllByRole("button", { name: "Så gick det till" });
    await user.click(disclosures[1]);
    expect(screen.getByText(/Åtgärden stoppades, men systemet fortsatte/i)).toBeVisible();
    const receipt = screen.getByLabelText("Körningskvitto");
    expect(within(within(receipt).getByText("Ändringar").parentElement as HTMLElement).getByText("0")).toBeVisible();
  });

  it("restores a verified conversation after reload and clears it on request", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        return jsonResponse(customerOrderResponse);
      }
      return readOnlyBackendResponse(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    const firstView = render(<App />);
    await user.click(await screen.findByRole("button", { name: "Var är min order?" }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    expect(await screen.findByText(/Jag har kontrollerat din order NORD-2051/i)).toBeVisible();
    firstView.unmount();

    render(<App />);

    expect(screen.getByText("Var är min order?")).toBeVisible();
    expect(screen.getByText(/Jag har kontrollerat din order NORD-2051/i)).toBeVisible();
    expect(screen.getByText(/sparas i den här fliken i 2 timmar/i)).toBeVisible();
    expect(fetchMock.mock.calls.filter(([input]) =>
      String(input).endsWith("/api/v1/demo-customer/chat/turns"),
    )).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "Ny konversation" }));
    expect(await screen.findByRole("button", { name: "Var är min order?" })).toBeVisible();
    expect(screen.queryByText(/Jag har kontrollerat din order NORD-2051/i)).not.toBeInTheDocument();
  });

  it("redacts a protected salary request before showing it in the transcript", async () => {
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        return jsonResponse(customerBlockedResponse);
      }
      return readOnlyBackendResponse(input);
    }));

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: salaryQuestion }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(await screen.findByText(/Jag kan inte lämna ut privat information/i)).toBeVisible();
    expect(screen.getByText("[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]")).toBeVisible();
    expect(screen.queryByText(salaryQuestion)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Så gick det till" }));
    expect(screen.getByText(/stoppades före kunddata, dokument och AI/i)).toBeVisible();
    const receipt = screen.getByLabelText("Körningskvitto");
    expect(within(within(receipt).getByText("Backendläsningar").parentElement as HTMLElement).getByText("0")).toBeVisible();
    expect(within(within(receipt).getByText("AI-anrop").parentElement as HTMLElement).getByText("0")).toBeVisible();
  });

  it("runs the bounded live path directly and reveals real RAG evidence", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        return jsonResponse(customerRagResponse);
      }
      return readOnlyBackendResponse(input);
    });
    vi.stubGlobal("fetch", fetchMock);

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Kan jag få pengarna tillbaka?" }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(await screen.findByText(/banken visa beloppet inom två till fem bankdagar/i)).toBeVisible();
    expect(screen.getAllByText("Kan jag få pengarna tillbaka?")).toHaveLength(1);

    const liveRequest = fetchMock.mock.calls.find(([, init]) =>
      String((init as RequestInit | undefined)?.body).includes('"confirm_live_ai":true'),
    );
    expect(liveRequest).toBeDefined();

    await user.click(screen.getByRole("button", { name: "Så gick det till" }));
    expect(screen.getByText(/RAG sökte semantiskt/i)).toBeVisible();
    expect(screen.getByText(/gemini-embedding-2 · 768D/i)).toBeVisible();
    expect(screen.getByText(/pgvector_exact_cosine · 1 träff/i)).toBeVisible();
    expect(screen.getByText("Returns and card refunds")).toBeVisible();

    await user.click(screen.getByRole("button", { name: "Öppna dokumentet" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Returer och kortåterbetalningar" })).toBeVisible();
    expect(within(dialog).getByText("RAG hämtade denna passage")).toBeVisible();
  });

  it("keeps the existing transcript when a later backend call fails", async () => {
    let chatCall = 0;
    vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith("/api/v1/demo-customer/chat/turns")) {
        chatCall += 1;
        if (chatCall === 1) return jsonResponse(customerOrderResponse);
        return jsonResponse(
          { detail: "Customer chat unavailable.", code: "CUSTOMER_CHAT_UNAVAILABLE" },
          503,
        );
      }
      return readOnlyBackendResponse(input);
    }));

    const user = userEvent.setup();
    render(<App />);

    await user.click(await screen.findByRole("button", { name: "Var är min order?" }));
    await user.click(screen.getByRole("button", { name: "Skicka" }));
    expect(await screen.findByText(/Jag har kontrollerat din order NORD-2051/i)).toBeVisible();

    await user.type(
      screen.getByRole("textbox", { name: "Skriv en fråga till Nordly…" }),
      "Vad händer nu?",
    );
    await user.click(screen.getByRole("button", { name: "Skicka" }));

    expect(await screen.findByText(/Jag kommer inte åt Nordlys tjänst just nu/i)).toBeVisible();
    expect(screen.getByText(/Jag har kontrollerat din order NORD-2051/i)).toBeVisible();
    expect(screen.getByText("CUSTOMER_CHAT_UNAVAILABLE")).toBeVisible();
  });
});
