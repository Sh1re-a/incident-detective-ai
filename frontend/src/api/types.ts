export type RunMode = "recorded_replay" | "live_ai";
export type DiagnosisStatus = "diagnosed" | "insufficient_evidence";
export type ToolName =
  | "get_metrics"
  | "search_logs"
  | "get_trace"
  | "retrieve_runbooks";

export interface TimeWindow {
  start: string;
  end: string;
}

export interface InitialSymptom {
  symptom_code: string;
  summary: string;
  observed_at: string;
}

export interface Scenario {
  scenario_id: string;
  title: string;
  description: string;
  incident_started_at: string;
  time_window: TimeWindow;
  affected_services: string[];
  business_impact_summary: string;
  initial_symptoms: InitialSymptom[];
  version: number;
}

export interface ScenarioCatalogResponse {
  scenarios: Scenario[];
}

interface EvidenceBase {
  evidence_id: string;
  scenario_id: string;
  display_summary: string;
  source_ref: string;
}

export interface MetricEvidence extends EvidenceBase {
  evidence_type: "metric";
  observed_at: string;
  content: {
    metric_name: string;
    value: number;
    unit: string;
    labels: Record<string, string>;
  };
}

export interface LogEvidence extends EvidenceBase {
  evidence_type: "log";
  observed_at: string;
  content: {
    service: string;
    level: string;
    message: string;
    attributes: Record<string, string>;
  };
}

export interface TraceEvidence extends EvidenceBase {
  evidence_type: "trace";
  observed_at: string;
  content: {
    trace_id: string;
    spans: Array<{
      span_id: string;
      service: string;
      operation: string;
      duration_ms: number;
      status: string;
    }>;
  };
}

export interface RunbookEvidence extends EvidenceBase {
  evidence_type: "runbook";
  content: {
    document_id: string;
    chunk_id: string;
    document_version: string;
    text: string;
  };
}

export type Evidence =
  | MetricEvidence
  | LogEvidence
  | TraceEvidence
  | RunbookEvidence;

export interface Diagnosis {
  status: DiagnosisStatus;
  root_cause_code: string | null;
  affected_service: string | null;
  business_summary: string;
  technical_summary: string;
  claims: Array<{
    claim_code:
      | "root_cause"
      | "affected_service"
      | "trigger"
      | "customer_impact"
      | "observed_symptom"
      | "missing_evidence";
    claim_value_code: string;
    display_text: string;
    evidence_ids: string[];
  }>;
  safe_next_step: {
    summary: string;
    requires_human_approval: boolean;
  };
}

export interface Verification {
  diagnosis_schema_pass: boolean;
  ground_truth_schema_pass: boolean;
  citation_validity: {
    valid: boolean;
    unknown_evidence_ids: string[];
  };
  evidence_precision: {
    applicable: boolean;
    supported_triples: number;
    total_triples: number;
    score: number | null;
    citation_support: Array<{
      claim_code: Diagnosis["claims"][number]["claim_code"];
      claim_value_code: string;
      evidence_id: string;
      supported: boolean;
    }>;
  };
  diagnosis_correctness: {
    evaluated: boolean;
    diagnosis_applicable: boolean;
    root_cause_correct: boolean;
    affected_service_correct: boolean;
    abstention_correct: boolean;
  };
  hard_errors: Array<
    | "diagnosis_schema_invalid"
    | "ground_truth_schema_invalid"
    | "unknown_evidence_id"
  >;
}

export interface ReplayComparison {
  expected_status: DiagnosisStatus;
  expected_root_cause_code: string | null;
  expected_affected_service: string | null;
  root_cause_correct: boolean;
  affected_service_correct: boolean;
  abstention_correct: boolean;
}

export interface ModelTokenUsage {
  input_tokens: number;
  output_tokens: number;
  total_tokens: number;
}

export interface RecordedToolEvent {
  event_id: string;
  tool_name: ToolName;
  safe_summary: string;
  evidence: Evidence[];
}

export interface RunbookRetrievalMetadata {
  backend: "deterministic_fixture" | "pgvector_exact_cosine";
  corpus_version?: string | null;
  embedding_profile?: {
    model_id: string;
    dimensions: number;
    format_version: string;
    minimum_similarity: number;
  } | null;
  query_embedding?: {
    local_input_characters: number;
    provider_billable_characters?: number | null;
    provider_input_tokens?: number | null;
    latency_ms: number;
  } | null;
  matches: Array<{
    rank: number;
    evidence_id: string;
    cosine_similarity?: number | null;
    content_sha256?: string | null;
  }>;
}

export interface LiveToolEvent extends RecordedToolEvent {
  collection_round: number;
  arguments: Record<string, unknown>;
  runbook_retrieval?: RunbookRetrievalMetadata | null;
}

interface CommonRunResult {
  run_id: string;
  scenario_id: string;
  truth_label: string;
  started_at: string;
  completed_at: string;
  latency_ms: number;
  scenario: Scenario;
  diagnosis: Diagnosis;
  verification: Verification;
  comparison: ReplayComparison;
  model_id: string | null;
  prompt_version: string | null;
  token_usage: ModelTokenUsage | null;
  estimated_cost_usd: number | null;
}

export interface RecordedReplayResult extends CommonRunResult {
  mode: "recorded_replay";
  status: "completed";
  tool_events: RecordedToolEvent[];
}

export interface LiveInvestigationResult extends CommonRunResult {
  mode: "live_ai";
  status: "completed" | "verification_failed";
  tool_events: LiveToolEvent[];
  model_calls: Array<{
    phase: "collect" | "synthesize";
    round: number;
    provider_response_id: string | null;
    model_version: string;
    token_usage: ModelTokenUsage;
    latency_ms: number;
  }>;
  estimated_cost_basis: string;
  tool_call_count: number;
  model_call_count: number;
  limitations: string[];
}

export type InvestigationResult =
  | RecordedReplayResult
  | LiveInvestigationResult;

export interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code?:
    | "LIVE_AI_CONFIRMATION_REQUIRED"
    | "LIVE_AI_DISABLED"
    | "LIVE_AI_NOT_CONFIGURED"
    | "LIVE_AI_RATE_LIMITED"
    | "LIVE_INVESTIGATION_TIMEOUT"
    | "MODEL_PROVIDER_TIMEOUT"
    | "MODEL_PROVIDER_ERROR"
    | "MALFORMED_MODEL_RESPONSE"
    | "INVALID_MODEL_TOOL_ARGUMENTS"
    | "SCENARIO_NOT_FOUND";
}
