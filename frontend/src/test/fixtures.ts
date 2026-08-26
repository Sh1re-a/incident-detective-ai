import type {
  LiveInvestigationResult,
  MetricEvidence,
  RecordedReplayResult,
  Scenario,
} from "../api/types";

export const scenario: Scenario = {
  scenario_id: "checkout-orders-at-risk-v1",
  title: "Checkout errors threaten orders",
  description: "Checkout failures increased shortly after a synthetic webshop release.",
  incident_started_at: "2026-08-25T10:02:00Z",
  time_window: {
    start: "2026-08-25T09:55:00Z",
    end: "2026-08-25T10:15:00Z",
  },
  affected_services: ["STOREFRONT", "CHECKOUT_API", "PAYMENT_ADAPTER"],
  business_impact_summary:
    "Synthetic estimate for 10:02–10:12 UTC: 147 of 800 checkout attempts failed.",
  initial_symptoms: [
    {
      symptom_code: "CHECKOUT_ERROR_RATE_HIGH",
      summary: "Checkout failures are above the synthetic baseline.",
      observed_at: "2026-08-25T10:04:00Z",
    },
    {
      symptom_code: "PAYMENT_STEP_SLOW",
      summary: "The payment step is slower than the synthetic baseline.",
      observed_at: "2026-08-25T10:05:00Z",
    },
  ],
  version: 1,
};

export const secondScenario: Scenario = {
  ...scenario,
  scenario_id: "checkout-cart-segment-failures-v1",
  title: "Some carts fail before payment",
  description:
    "Multi-item checkouts in a synthetic inventory rollout began failing shortly after release.",
};

export const metricEvidence: MetricEvidence = {
  evidence_type: "metric",
  evidence_id: "cpt-v1-metric-checkout-failure-rate",
  scenario_id: scenario.scenario_id,
  observed_at: "2026-08-25T10:12:00Z",
  display_summary: "Checkout failure ratio reached 18.4 percent.",
  source_ref: "metrics/checkout/failure-ratio/10-02-10-12",
  content: {
    metric_name: "checkout_failure_ratio",
    value: 0.184,
    unit: "ratio",
    labels: {
      attempts: "800",
      service: "CHECKOUT_API",
    },
  },
};

export const recordedResult: RecordedReplayResult = {
  run_id: "00000000-0000-4000-8000-000000000001",
  scenario_id: scenario.scenario_id,
  mode: "recorded_replay",
  truth_label: "Simulated incident — recorded deterministic replay.",
  status: "completed",
  started_at: "2026-08-25T10:20:00Z",
  completed_at: "2026-08-25T10:20:00.004Z",
  latency_ms: 4,
  scenario,
  tool_events: [
    {
      event_id: "cpt-v1-tool-metrics",
      tool_name: "get_metrics",
      safe_summary: "Read checkout failures for the incident window.",
      evidence: [metricEvidence],
    },
  ],
  diagnosis: {
    status: "diagnosed",
    root_cause_code: "PAYMENT_TIMEOUT_CONFIG",
    affected_service: "PAYMENT_ADAPTER",
    business_summary:
      "Synthetic checkout attempts are failing because payment authorization now times out too early.",
    technical_summary:
      "A synthetic timeout change matches the observed payment failures.",
    claims: [
      {
        claim_code: "root_cause",
        claim_value_code: "PAYMENT_TIMEOUT_CONFIG",
        display_text: "The payment timeout is below the observed request duration.",
        evidence_ids: [metricEvidence.evidence_id],
      },
    ],
    safe_next_step: {
      summary: "Review restoring the previous timeout after human approval.",
      requires_human_approval: true,
    },
  },
  verification: {
    diagnosis_schema_pass: true,
    ground_truth_schema_pass: true,
    citation_validity: {
      valid: true,
      unknown_evidence_ids: [],
    },
    evidence_precision: {
      applicable: true,
      supported_triples: 1,
      total_triples: 1,
      score: 1,
      citation_support: [
        {
          claim_code: "root_cause",
          claim_value_code: "PAYMENT_TIMEOUT_CONFIG",
          evidence_id: metricEvidence.evidence_id,
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
    expected_root_cause_code: "PAYMENT_TIMEOUT_CONFIG",
    expected_affected_service: "PAYMENT_ADAPTER",
    root_cause_correct: true,
    affected_service_correct: true,
    abstention_correct: false,
  },
  model_id: null,
  prompt_version: null,
  token_usage: null,
  estimated_cost_usd: null,
};

export const liveResult: LiveInvestigationResult = {
  ...recordedResult,
  run_id: "00000000-0000-4000-8000-000000000002",
  mode: "live_ai",
  truth_label: "Simulated incident — real AI investigation.",
  tool_events: [
    {
      ...recordedResult.tool_events[0],
      collection_round: 1,
      arguments: { metric_names: ["checkout_failure_ratio"] },
    },
    {
      event_id: "live-runbook-event",
      tool_name: "retrieve_runbooks",
      safe_summary: "Returned one bounded runbook chunk using pgvector retrieval.",
      evidence: [],
      collection_round: 1,
      arguments: { query: "payment timeout", max_results: 4 },
      runbook_retrieval: {
        backend: "pgvector_exact_cosine",
        corpus_version: "runbook-corpus-v1",
        embedding_profile: {
          model_id: "gemini-embedding-2",
          dimensions: 768,
          format_version: "search-result-v1",
          minimum_similarity: 0.6620781500197453,
        },
        query_embedding: {
          local_input_characters: 58,
          provider_billable_characters: null,
          provider_input_tokens: null,
          latency_ms: 312,
        },
        matches: [
          {
            rank: 1,
            evidence_id: "runbook-payment-timeout-precedence",
            cosine_similarity: 0.7782714501721635,
            content_sha256:
              "4ab593fed99519615d70d254c57ab8617e5860a922b7890a3838fda80c2ca036",
          },
        ],
      },
    },
  ],
  model_id: "gemini-3.5-flash-lite",
  prompt_version: "gemini-live-v3",
  token_usage: {
    input_tokens: 3967,
    cached_input_tokens: 800,
    uncached_input_tokens: 3167,
    candidate_output_tokens: 700,
    thinking_output_tokens: 108,
    output_tokens: 808,
    tool_use_prompt_tokens: 0,
    total_tokens: 4775,
  },
  estimated_cost_usd: 0.0029941,
  estimated_cost_basis:
    "Gemini paid Standard list prices checked 2026-08-26; this is not a provider invoice and the API does not report whether the run was free-tier billed. Provider-reported cached input tokens were priced at the context-caching rate; remaining input tokens used the normal rate. No explicit cache or storage cost was used.",
  prompt_cache: {
    strategy: "provider_implicit",
    provider_reported_model_calls: 2,
    model_call_count: 2,
    cached_input_tokens: 800,
    cache_hit_observed: true,
  },
  model_cost_breakdown: {
    uncached_input_usd: 0.0009501,
    cached_input_usd: 0.000024,
    output_usd: 0.00202,
    observed_cache_savings_usd: 0.000216,
  },
  model_calls: [
    {
      phase: "collect",
      round: 1,
      provider_response_id: "synthetic-response-id",
      model_version: "gemini-3.5-flash-lite",
      token_usage: {
        input_tokens: 2000,
        cached_input_tokens: 500,
        uncached_input_tokens: 1500,
        candidate_output_tokens: 180,
        thinking_output_tokens: 20,
        output_tokens: 200,
        tool_use_prompt_tokens: 0,
        total_tokens: 2200,
      },
      latency_ms: 1700,
    },
    {
      phase: "synthesize",
      round: 1,
      provider_response_id: "synthetic-response-id-2",
      model_version: "gemini-3.5-flash-lite",
      token_usage: {
        input_tokens: 1967,
        cached_input_tokens: 300,
        uncached_input_tokens: 1667,
        candidate_output_tokens: 520,
        thinking_output_tokens: 88,
        output_tokens: 608,
        tool_use_prompt_tokens: 0,
        total_tokens: 2575,
      },
      latency_ms: 2400,
    },
  ],
  tool_call_count: 2,
  model_call_count: 2,
  limitations: ["Synthetic incident data only."],
};
