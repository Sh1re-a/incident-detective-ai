// Typed from the local Incident Detective OpenAPI contract at /v3/api-docs.

export type InvestigationMode = "recorded_replay" | "live_ai";
export type ToolName =
  | "get_metrics"
  | "search_logs"
  | "get_trace"
  | "retrieve_runbooks";
export type DiagnosisStatus = "diagnosed" | "insufficient_evidence";
export type GeneratedEvidenceMode = "diagnostic" | "insufficient_evidence";
export type GeneratedNoiseLevel = "none" | "low";
export type GeneratedIncidentFamily =
  | "payment_timeout"
  | "catalog_cache_invalidation"
  | "order_event_backlog"
  | "order_idempotency_failure";
export type ClaimCode =
  | "root_cause"
  | "affected_service"
  | "trigger"
  | "customer_impact"
  | "observed_symptom"
  | "missing_evidence";

export interface ModeCapability {
  mode: InvestigationMode;
  truth_label: string;
  model_backed: boolean;
  explicit_confirmation_required: boolean;
}

export interface ToolCapability {
  name: ToolName;
  read_only: boolean;
}

export interface ToolBudgetCapability {
  tool: ToolName;
  max_calls_per_investigation: number;
}

export interface LiveBudgetCapability {
  max_collection_rounds: number;
  max_tool_calls_total: number;
  max_tool_calls_per_round: number;
  max_calls_by_tool: ToolBudgetCapability[];
  hard_deadline_ms: number;
  provider_call_cap_ms: number;
  daily_live_run_limit: number;
  daily_quota_scope: "process_local" | "database_global";
}

export interface LiveAiCapability {
  enabled_by_configuration: boolean;
  request_routing_configured: boolean;
  explicit_confirmation_required: boolean;
  model_id: string;
  thinking_level: "MINIMAL" | "LOW" | "MEDIUM" | "HIGH";
  prompt_version: string;
  budget: LiveBudgetCapability;
}

export interface GeneratedCasesCapability {
  enabled: boolean;
  contract_version: string;
  generator_version: string;
  truth_label: string;
  user_supplied_data_accepted: boolean;
  request_local_only: boolean;
  incident_families: GeneratedIncidentFamily[];
  evidence_modes: GeneratedEvidenceMode[];
  noise_levels: GeneratedNoiseLevel[];
  allowed_tools: ToolName[];
}

export interface EmbeddingCapability {
  provider_transport: "developer_api" | "vertex_ai";
  model_id: string;
  dimensions: number;
  format_version: string;
  minimum_similarity: number;
}

export interface ProviderCapability {
  transport: "developer_api" | "vertex_ai";
  authentication_mode: "api_key" | "adc";
  location: string | null;
  routing_configuration_complete: boolean;
  credential_status: "configured" | "missing" | "not_checked";
}

export interface DeploymentCapability {
  platform: "local" | "cloud_run";
  revision: string | null;
  build_git_sha: string | null;
}

export interface KnowledgeCorpusCapability {
  manifest_version: string;
  corpus_version: string;
  corpus_content_sha256: string;
  eligible_document_count: number;
  eligible_chunk_count: number;
}

export interface VectorIndexCapability {
  ready: boolean;
  corpus_version: string;
  indexed_chunks: number;
  current_chunks: number;
  expected_chunks: number;
}

export interface RetrievalCapability {
  backend: "deterministic_fixture" | "pgvector_exact_cosine";
  active_profiles: string[];
  mode_description: string;
  limitation: string;
  vector_database_backend_active: boolean;
  active_embedding_profile: EmbeddingCapability | null;
  index_status: VectorIndexCapability | null;
}

export interface PromptCacheCapability {
  strategy: "provider_implicit";
  explicit_caching_enabled: boolean;
  cache_hit_claims_require_provider_metadata: boolean;
}

export type DiagnosticProbeId =
  | "service_health"
  | "dependency_status"
  | "release_metadata"
  | "config_fingerprint_diff";

export interface DiagnosticProbeCapability {
  function_name: "run_diagnostic_probe";
  allowed_probe_ids: DiagnosticProbeId[];
  case_bound: boolean;
  read_only: boolean;
  action_executed: false;
}

export interface LocalizedCopy {
  sv: string;
  en: string;
}

export type IncidentService =
  | "storefront"
  | "checkout_api"
  | "payment_adapter"
  | "catalog_service"
  | "order_service"
  | "order_event_consumer"
  | "inventory_service";

export interface IncidentFamilyCapability {
  id: GeneratedIncidentFamily;
  label: LocalizedCopy;
  description: LocalizedCopy;
  customer_impact: LocalizedCopy;
  service: IncidentService;
  alarm_signal: string;
  alarm_signal_concept: LocalizedCopy;
}

export interface IncidentLabToolCapability {
  function_name: "inspect_incident_evidence";
  read_only: boolean;
  case_bound: boolean;
  read_operations: ToolName[];
  diagnostic_probe_reference: "diagnostic_probe";
  allowed_diagnostic_probe_ids: DiagnosticProbeId[];
}

export interface IncidentLabCapability {
  enabled: boolean;
  availability_reason:
    | "enabled_by_configuration_not_health_checked"
    | "rag_profile_required"
    | "adk_disabled"
    | "live_ai_disabled"
    | "provider_routing_not_configured";
  required_profiles: string[];
  plan_contract_version: string;
  run_contract_version: string;
  orchestration: "sequential_agent";
  expected_agent_order: string[];
  delivery: "synchronous_post_run";
  streaming: false;
  alarm_required: true;
  answer_states: IncidentLabAnswerState[];
  explicit_confirmation_required: true;
  synthetic_only: true;
  write_tools_available: false;
  action_executed: false;
  human_approval_required: true;
  registered_tool: IncidentLabToolCapability;
  supported_locales: KnowledgeRagLocale[];
  incident_families: IncidentFamilyCapability[];
}

export interface CapabilitiesResponse {
  contract_version: "capabilities-v5";
  synthetic_only: boolean;
  remediation_enabled: boolean;
  provider: ProviderCapability;
  deployment: DeploymentCapability;
  knowledge_corpus: KnowledgeCorpusCapability;
  modes: ModeCapability[];
  tools: ToolCapability[];
  diagnostic_probe: DiagnosticProbeCapability;
  incident_lab: IncidentLabCapability;
  live_ai: LiveAiCapability;
  generated_cases: GeneratedCasesCapability;
  retrieval: RetrievalCapability;
  prompt_cache: PromptCacheCapability;
}

export interface RetrievalEvalProvenance {
  historical_frozen_run: boolean;
  suite_version: string;
  suite_sha256: string;
  corpus_version: string;
  corpus_content_sha256: string;
  git_sha: string;
  executed_at: string;
}

export interface RetrievalEvalConfiguration {
  backend: string;
  embedding_provider: string;
  embedding_model: string;
  embedding_dimensions: number;
  embedding_format_version: string;
  corpus_document_count: number;
  corpus_chunk_count: number;
  top_k: number;
  minimum_similarity: number;
  threshold_calibrated_on_development: boolean;
  configured_threshold_matches_calibration: boolean;
}

export interface RetrievalEvalSplitMetrics {
  split: "development" | "held_out";
  positive_cases: number;
  positive_hits: number;
  hit_at_k: number;
  mean_reciprocal_rank: number;
  no_match_cases: number;
  correct_no_matches: number;
  no_match_accuracy: number;
  benign_unsafe_top1_rate: number;
}

export interface RetrievalEvalProviderUsage {
  embedding_calls: number;
  local_input_characters: number;
  provider_billable_characters: number | null;
  provider_input_tokens: number | null;
  provider_usage_metadata_complete: boolean;
  provider_call_latency_ms: number;
  evaluation_latency_ms: number;
  estimated_list_price_cost_usd: number | null;
  cost_status: string;
}

export interface RetrievalEvalSafetyBoundary {
  adversarial_synthesis_safety_evaluated: boolean;
  status: string;
}

export interface RetrievalEvalProofResponse {
  summary_version: "retrieval-proof-summary-v1";
  status: "measured";
  provenance: RetrievalEvalProvenance;
  retrieval: RetrievalEvalConfiguration;
  development: RetrievalEvalSplitMetrics;
  held_out: RetrievalEvalSplitMetrics;
  provider_usage: RetrievalEvalProviderUsage;
  safety_boundary: RetrievalEvalSafetyBoundary;
}

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

export interface MetricContent {
  metric_name: string;
  value: number;
  unit: string;
  labels: Record<string, string>;
}

export interface LogContent {
  service: string;
  level: string;
  message: string;
  attributes: Record<string, string>;
}

export interface TraceSpan {
  span_id: string;
  service: string;
  operation: string;
  duration_ms: number;
  status: string;
}

export interface TraceContent {
  trace_id: string;
  spans: TraceSpan[];
}

export interface RunbookContent {
  document_id: string;
  chunk_id: string;
  document_version: string;
  text: string;
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
  content: MetricContent;
}

export interface LogEvidence extends EvidenceBase {
  evidence_type: "log";
  observed_at: string;
  content: LogContent;
}

export interface TraceEvidence extends EvidenceBase {
  evidence_type: "trace";
  observed_at: string;
  content: TraceContent;
}

export interface RunbookEvidence extends EvidenceBase {
  evidence_type: "runbook";
  content: RunbookContent;
}

export type Evidence =
  | MetricEvidence
  | LogEvidence
  | TraceEvidence
  | RunbookEvidence;

export interface RecordedToolResult {
  event_id: string;
  tool_name: ToolName;
  safe_summary: string;
  evidence: Evidence[];
}

export type RunbookRetrievalBackend =
  | "deterministic_fixture"
  | "pgvector_exact_cosine";

export interface RunbookEmbeddingProfile {
  model_id: string;
  dimensions: number;
  format_version: string;
  minimum_similarity: number;
}

export interface QueryEmbeddingUsage {
  local_input_characters: number;
  provider_billable_characters: number | null;
  provider_input_tokens: number | null;
  latency_ms: number;
}

export interface RunbookRetrievalMatch {
  rank: number;
  evidence_id: string;
  cosine_similarity: number | null;
  content_sha256: string | null;
}

export interface RunbookRetrievalMetadata {
  backend: RunbookRetrievalBackend;
  corpus_version: string | null;
  embedding_profile: RunbookEmbeddingProfile | null;
  query_embedding: QueryEmbeddingUsage | null;
  matches: RunbookRetrievalMatch[];
}

export interface LiveToolEvent extends RecordedToolResult {
  collection_round: number;
  arguments: Record<string, unknown>;
  runbook_retrieval: RunbookRetrievalMetadata | null;
}

export interface Claim {
  claim_code: ClaimCode;
  claim_value_code: string;
  display_text: string;
  evidence_ids: string[];
}

export interface SafeNextStep {
  summary: string;
  requires_human_approval: boolean;
}

export interface Diagnosis {
  status: DiagnosisStatus;
  root_cause_code: string | null;
  affected_service: string | null;
  business_summary: string;
  technical_summary: string;
  claims: Claim[];
  safe_next_step: SafeNextStep;
}

export interface CitationValidity {
  valid: boolean;
  unknown_evidence_ids: string[];
}

export interface CitationSupportResult {
  claim_code: ClaimCode;
  claim_value_code: string;
  evidence_id: string;
  supported: boolean;
}

export interface EvidencePrecision {
  applicable: boolean;
  supported_triples: number;
  total_triples: number;
  score: number | null;
  citation_support: CitationSupportResult[];
}

export interface ClaimCoverage {
  applicable: boolean;
  matched_claim_count: number;
  reference_claim_count: number;
  score: number | null;
}

export interface DiagnosisCorrectness {
  evaluated: boolean;
  diagnosis_applicable: boolean;
  root_cause_correct: boolean;
  affected_service_correct: boolean;
  abstention_correct: boolean;
}

export interface VerificationReport {
  diagnosis_schema_pass: boolean;
  ground_truth_schema_pass: boolean;
  citation_validity: CitationValidity;
  evidence_precision: EvidencePrecision;
  claim_coverage: ClaimCoverage;
  diagnosis_correctness: DiagnosisCorrectness;
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

export interface RecordedReplayResult {
  run_id: string;
  scenario_id: string;
  mode: "recorded_replay";
  truth_label: string;
  status: "completed";
  started_at: string;
  completed_at: string;
  latency_ms: number;
  scenario: Scenario;
  tool_events: RecordedToolResult[];
  diagnosis: Diagnosis;
  verification: VerificationReport;
  comparison: ReplayComparison;
  model_id: null;
  prompt_version: null;
  token_usage: null;
  estimated_cost_usd: null;
}

export interface ModelTokenUsage {
  input_tokens: number | null;
  cached_input_tokens: number | null;
  uncached_input_tokens: number | null;
  candidate_output_tokens: number | null;
  thinking_output_tokens: number | null;
  output_tokens: number | null;
  tool_use_prompt_tokens: number | null;
  total_tokens: number | null;
}

export type AdkAgentOutcome =
  | "completed"
  | "verification_failed"
  | "blocked_before_ai";

export type AdkSafetyDecisionCode = "allowed" | "blocked";

export type AdkSafetyReasonCode =
  | "none"
  | "pii_request"
  | "employee_compensation_request"
  | "secret_request"
  | "prompt_injection"
  | "financial_action"
  | "write_action";

export interface AdkAgentTurnRequest {
  seed: number;
  incident_family: GeneratedIncidentFamily;
  evidence_mode: GeneratedEvidenceMode;
  noise_level: GeneratedNoiseLevel;
  message: string;
  confirm_live_ai: boolean;
}

export interface AdkSafetyDecision {
  decision: AdkSafetyDecisionCode;
  reason_code: AdkSafetyReasonCode;
  summary_sv: string;
  summary_en: string;
}

export interface AdkRuntimeProvenance {
  framework: "google_adk" | "not_invoked";
  framework_version: string;
  agent_name: string;
  model_id: string;
  prompt_version: string;
  session_service: "request_scoped_in_memory" | "none";
  delivery: "synchronous_post_run" | "blocked_before_provider";
  event_source: "google_adk_runner" | "java_safety_gate";
  runner_invoked: boolean;
  streamed: boolean;
}

export interface AdkWorkflowReceipt {
  type: "sequential_agent";
  expected_agent_order: string[];
  observed_agent_order: string[];
  evidence_handoff: "adk_function_response";
  final_response_author: string;
  completed_in_order: boolean;
}

export interface AdkFunctionCallEvent {
  id: string | null;
  name: string;
  arguments: Record<string, unknown>;
}

export interface AdkFunctionResponseEvent {
  id: string | null;
  name: string;
  response: Record<string, unknown>;
}

export type AdkRuntimeEventType =
  | "tool_call"
  | "tool_result"
  | "runtime_error"
  | "final_response"
  | "runtime_event";

export interface AdkRuntimeEvent {
  sequence: number;
  event_id: string;
  invocation_id: string;
  author: string;
  type: AdkRuntimeEventType;
  observed_at: string;
  final_response: boolean;
  content_withheld: boolean;
  text: string | null;
  function_calls: AdkFunctionCallEvent[];
  function_responses: AdkFunctionResponseEvent[];
  token_usage: ModelTokenUsage | null;
  provider_model_version: string | null;
}

export interface AdkVerificationEvent {
  source: string;
  executed_at: string;
  schema_valid: boolean;
  citations_valid: boolean;
  direct_evidence_support_valid: boolean;
  factual_result_matches_ground_truth: boolean;
  agent_sequence_valid: boolean;
  evidence_handoff_valid: boolean;
  tool_boundary_valid: boolean;
  final_author_valid: boolean;
  answer_released: boolean;
  summary: string;
}

export interface AdkControlReceipt {
  model_calls: number;
  adk_tool_calls: number;
  read_operations: number;
  embedding_calls: number;
  write_tools_available: boolean;
  action_executed: boolean;
  human_approval_required: boolean;
  registered_tools: string[];
  token_usage: ModelTokenUsage | null;
  estimated_cost_usd: number | null;
  estimated_cost_basis: string;
  total_latency_ms: number;
}

export interface AdkAgentTurnResponse {
  contract_version: "nordly-adk-turn-v4";
  run_id: string;
  session_id: string | null;
  turn_id: string;
  mode: "adk_live_ai";
  truth_label: string;
  outcome: AdkAgentOutcome;
  provider_route: GoogleGenAiProviderRoute | null;
  scenario: Scenario | null;
  safety: AdkSafetyDecision;
  runtime: AdkRuntimeProvenance;
  workflow: AdkWorkflowReceipt | null;
  events: AdkRuntimeEvent[];
  tool_events: LiveToolEvent[];
  diagnosis: Diagnosis | null;
  verification: VerificationReport | null;
  comparison: ReplayComparison | null;
  verification_event: AdkVerificationEvent | null;
  receipt: AdkControlReceipt;
  limitations: string[];
}

export type LiveAiState =
  | "available"
  | "daily_budget_exhausted"
  | "disabled"
  | "not_configured";

export interface LiveAiStatusResponse {
  contract_version: "live-ai-status-v1";
  live_state: LiveAiState;
  reason_code: string;
  resets_at: string | null;
  retry_after_seconds: number | null;
  replay_available: boolean;
  daily_cost_guard_active: boolean;
  quota_scope: "process_local" | "database_global";
}

export interface IncidentPlan {
  contract_version: "incident-plan-v1";
  incident_family: GeneratedIncidentFamily;
  severity: string;
  affected_services: string[];
  summary: string;
  synthetic_only: true;
  write_actions_allowed: false;
  human_approval_required: true;
}

export interface IncidentLabPlanProposal {
  status: "candidate" | "unsupported";
  summary: string;
  incident_family: GeneratedIncidentFamily | null;
  requested_severity: string | null;
  affected_services: string[];
  requested_blast_radius: string;
}

export interface IncidentLabPlanValidation {
  decision: "APPROVED" | "NARROWED" | "REJECTED";
  plan: IncidentPlan | null;
  adjustments: string[];
  rejection: { code: string; safe_message: string } | null;
}

export interface IncidentLabPlannerReceipt {
  transport: string;
  model: string;
  provider_response_id: string | null;
  token_usage: ModelTokenUsage | null;
  latency_ms: number;
}

export interface IncidentLabPlanRequest {
  instruction: string;
  confirm_live_ai: boolean;
}

export interface IncidentLabPlanResponse {
  contract_version: "incident-lab-plan-v1";
  outcome: "plan_ready" | "plan_rejected" | "blocked_before_ai";
  delivery: "synchronous_post_run" | "blocked_before_provider";
  truth_label: string;
  safety: IncidentLabSafetyDecision;
  proposal: IncidentLabPlanProposal | null;
  java_validation: IncidentLabPlanValidation | null;
  provider_receipt: IncidentLabPlannerReceipt | null;
  limitations: string[];
}

export interface IncidentLabSafetyDecision {
  decision: "allowed" | "blocked";
  reason_code: string;
  summary_sv: string;
  summary_en: string;
}

export interface IncidentLabRunRequest {
  plan: IncidentPlan;
  seed?: number;
  evidence_mode?: GeneratedEvidenceMode;
  confirm_live_ai: boolean;
}

export interface IncidentLabBusinessResponse {
  headline: string;
  what_happened: string;
  impact: string;
  what_is_known: string[];
  what_remains_unknown: string[];
  safe_next_step: string;
  certainty: string;
  human_approval_required: boolean;
}

export interface IncidentLabVerifiedClaim {
  claim_code: string;
  claim_value_code: string;
  evidence_ids: string[];
}

export interface IncidentLabDeveloperResponse {
  summary: string;
  root_cause_code: string | null;
  affected_service: string | null;
  verified_claims: IncidentLabVerifiedClaim[];
  highlighted_log_evidence_ids: string[];
  missing_evidence_codes: string[];
  failed_verification_checks: string[];
  next_read: string;
}

export interface IncidentLabActionReceipt {
  status: string;
  read_operations: number;
  write_tools_available: false;
  action_executed: false;
  human_approval_required: true;
  proposed_next_step: string | null;
  summary: string;
}

export interface IncidentLabLocalizedPresentation {
  business_response: IncidentLabBusinessResponse;
  developer_response: IncidentLabDeveloperResponse;
  action_receipt: IncidentLabActionReceipt;
}

export interface IncidentLabAlarmReceipt {
  alarm_id: string;
  rule_id: string;
  incident_family: GeneratedIncidentFamily;
  scenario_id: string;
  service: string;
  triggered_at: string;
  signal: {
    name: string;
    comparison: "at_least";
    threshold_value: number;
    observed_value: number;
    unit: string;
    lookback_seconds: number | null;
  };
  evidence_ids: string[];
}

export interface IncidentLabGenerationReceipt {
  generator_version: string;
  seed: number;
  seed_origin: "server_generated" | "user_supplied";
  incident_family: GeneratedIncidentFamily;
  evidence_mode: GeneratedEvidenceMode;
  noise_level: GeneratedNoiseLevel;
  scenario_id: string;
  variant: { variant_id: string; fingerprint: string };
}

export type IncidentLabAnswerState =
  | "diagnosed"
  | "insufficient_evidence"
  | "withheld"
  | "not_started";

export interface IncidentLabRunResponse {
  contract_version: "incident-lab-run-v3";
  outcome: string;
  delivery: "synchronous_post_run";
  truth_label: string;
  answer_state: IncidentLabAnswerState;
  business_response: IncidentLabBusinessResponse;
  developer_response: IncidentLabDeveloperResponse;
  action_receipt: IncidentLabActionReceipt;
  localized_presentations: Record<KnowledgeRagLocale, IncidentLabLocalizedPresentation>;
  plan: IncidentPlan;
  generation_receipt: IncidentLabGenerationReceipt;
  scenario: Scenario;
  backend_logs: LogEvidence[];
  alarm_receipt: IncidentLabAlarmReceipt | null;
  agent_turn: AdkAgentTurnResponse | null;
  limitations: string[];
}

export interface IncidentLabReplayAvailabilityResponse {
  contract_version: "incident-lab-replay-availability-v1";
  available: boolean;
  replay_contract_version: "incident-lab-replay-v1";
  truth_label: string;
  truth_label_en: string;
  reason_code: string;
}

export interface IncidentLabReplayResponse {
  contract_version: "incident-lab-replay-v1";
  replay_id: string;
  playback_id: string;
  mode: "recorded_replay";
  delivery: "synchronous_recorded_playback";
  truth_label: string;
  truth_label_en: string;
  served_at: string;
  recorded_instruction: string;
  recorded_instruction_locale: string;
  recorded_plan: IncidentLabPlanResponse;
  recorded_run: IncidentLabRunResponse;
  provenance: {
    fixture_version: string;
    recording_source: string;
    recorded_at: string;
    source_content_git_sha: string;
    runtime_build_git_sha: string | null;
    runtime_build_identity_verified: boolean;
    resource_sha256: string;
    resource_sha256_verified_at_startup: boolean;
    original_plan_contract_version: string;
    original_run_contract_version: string;
  };
  playback_receipt: {
    provider_calls: 0;
    model_calls: 0;
    adk_tool_calls: 0;
    read_operations: 0;
    embedding_calls: 0;
    vector_search_executed: false;
    live_quota_consumed: false;
    write_tools_available: false;
    action_executed: false;
    estimated_cost_usd: null;
    cost_status: "not_incurred";
  };
  limitations: string[];
}

export interface ModelCallMetadata {
  phase: "collect" | "synthesize";
  round: number;
  provider_response_id: string | null;
  model_version: string | null;
  token_usage: ModelTokenUsage | null;
  latency_ms: number;
}

export interface PromptCacheTelemetry {
  strategy: "provider_implicit";
  provider_reported_model_calls: number;
  model_call_count: number;
  cached_input_tokens: number | null;
  cache_hit_observed: boolean;
}

export interface ModelCostBreakdown {
  uncached_input_usd: number | null;
  cached_input_usd: number | null;
  output_usd: number | null;
  observed_cache_savings_usd: number | null;
}

export interface LiveInvestigationRequest {
  confirm_live_ai: true;
}

export interface GeneratedCaseLiveRequest {
  incident_family: GeneratedIncidentFamily;
  seed: number;
  evidence_mode: GeneratedEvidenceMode;
  noise_level: GeneratedNoiseLevel;
  confirm_live_ai: true;
}

export interface GenerationMetadata {
  generator_version: string;
  incident_family: GeneratedIncidentFamily;
  seed: number;
  evidence_mode: GeneratedEvidenceMode;
  noise_level: GeneratedNoiseLevel;
}

export interface LiveInvestigationResult {
  run_id: string;
  scenario_id: string;
  mode: "live_ai";
  truth_label: string;
  status: "completed" | "verification_failed";
  started_at: string;
  completed_at: string;
  latency_ms: number;
  scenario: Scenario;
  tool_events: LiveToolEvent[];
  diagnosis: Diagnosis;
  verification: VerificationReport;
  comparison: ReplayComparison;
  model_id: string;
  prompt_version: string;
  model_calls: ModelCallMetadata[];
  token_usage: ModelTokenUsage | null;
  prompt_cache: PromptCacheTelemetry;
  estimated_cost_usd: number | null;
  model_cost_breakdown: ModelCostBreakdown | null;
  estimated_cost_basis: string;
  tool_call_count: number;
  model_call_count: number;
  limitations: string[];
}

export interface GeneratedCaseRunResult {
  contract_version: "generated-live-run-v1";
  generation: GenerationMetadata;
  investigation: LiveInvestigationResult;
}

export interface ApiProblemResponse {
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
}

// Nordly Knowledge Room contracts. Runtime and business facts are supplied by
// the backend; the frontend only translates their presentation.
export interface DemoWorldCompany {
  id: string;
  display_name: string;
  storefront_name: string;
  description: string;
  description_en: string;
  markets: string[];
  markets_en: string[];
  industry: string;
  industry_en: string;
}

export interface DemoWorldService {
  id: string;
  display_name: string;
  display_name_en: string;
  role: string;
  role_en: string;
}

export type KnowledgeQuestionOutcome =
  | "answered"
  | "insufficient_evidence"
  | "refused";

export interface DemoWorldQuestion {
  id: string;
  prompt_sv: string;
  prompt_en: string;
  customer_context_sv: string;
  customer_context_en: string;
  outcome: KnowledgeQuestionOutcome;
  estimated_seconds: number;
  replay_available: boolean;
}

export interface DemoWorldCorpus {
  version: string;
  document_count: number;
  chunk_count: number;
  approved_documents: number;
  deprecated_documents: number;
  untrusted_documents: number;
}

export type KnowledgeRagExampleCategory =
  | "SAFE"
  | "PII"
  | "EMPLOYEE_COMPENSATION"
  | "SECRET"
  | "PROMPT_INJECTION"
  | "FINANCIAL_ACTION"
  | "NO_MATCH";

export type KnowledgeRagExpectedBoundary =
  | "rag"
  | "blocked_before_provider"
  | "insufficient_evidence";

export interface DemoWorldRagExample {
  id: string;
  category: KnowledgeRagExampleCategory;
  prompt_sv: string;
  prompt_en: string;
  expected_boundary: KnowledgeRagExpectedBoundary;
}

export interface DemoWorldResponse {
  contract_version: string;
  truth_label: string;
  truth_label_en: string;
  company: DemoWorldCompany;
  services: DemoWorldService[];
  questions: DemoWorldQuestion[];
  rag_examples: DemoWorldRagExample[];
  featured_scenario_id: string;
  corpus: DemoWorldCorpus;
}

export interface DemoOrder {
  order_id: string;
  market: string;
  item_count: number;
  created_at: string;
  updated_at: string;
  estimated_delivery_from: string;
  estimated_delivery_through: string;
  status_code: string;
  status_sv: string;
  status_en: string;
  payment_state: string;
  fulfilment_state: string;
  summary_sv: string;
  summary_en: string;
  next_step_sv: string;
  next_step_en: string;
  source_ref: string;
  evidence_id: string;
}

export interface DemoOrderReadReceipt {
  operation: "list_demo_orders" | "get_demo_order";
  read_operations: 1;
  records_returned: number;
  write_operations: 0;
  ai_calls: 0;
  write_tools_available: false;
  action_executed: false;
}

export interface DemoOrderCatalogResponse {
  contract_version: "nordly-demo-order-catalog-v1";
  mode: "read_only_synthetic_order_catalog";
  truth_label: string;
  truth_label_en: string;
  catalog_version: string;
  snapshot_at: string;
  synthetic_only: true;
  order_count: number;
  orders: DemoOrder[];
  action_receipt: DemoOrderReadReceipt;
  limitations: string[];
}

export interface DemoOrderLookupResponse {
  contract_version: "nordly-demo-order-lookup-v1";
  mode: "read_only_synthetic_order_lookup";
  truth_label: string;
  truth_label_en: string;
  catalog_version: string;
  snapshot_at: string;
  synthetic_only: true;
  order: DemoOrder;
  action_receipt: DemoOrderReadReceipt;
  limitations: string[];
}

export interface DemoCustomerChatTurnRequest {
  message: string;
  locale: KnowledgeRagLocale;
  confirm_live_ai: boolean;
  recent_conversation: DemoCustomerChatConversationTurn[];
}

export interface DemoCustomerChatConversationTurn {
  customer_message: string;
  assistant_message: string;
}

export type DemoCustomerChatOutcome =
  | "answered"
  | "outside_authority"
  | "clarification_required"
  | "confirmation_required"
  | "insufficient_evidence"
  | "refused"
  | "unsupported"
  | "unavailable";

export type DemoCustomerChatIntentName =
  | "order_status"
  | "cancellation_policy"
  | "cancel_order"
  | "return_policy"
  | "return_order"
  | "refund_policy"
  | "refund_order"
  | "change_delivery_address"
  | "purchase_item"
  | "company_knowledge"
  | "conversation"
  | "clarification_required"
  | "unsupported";

export interface DemoCustomerChatSubmittedMessage {
  text: string;
  locale: KnowledgeRagLocale;
  redacted: boolean;
}

export interface DemoCustomerChatContextReceipt {
  context_version: string;
  context_id: string;
  current_order_id: string;
  context_source_ref: string;
  order_source_ref: string;
  synthetic_only: true;
  persistent_memory: false;
  memory_scope: "request_scoped_bounded_history";
}

export interface DemoCustomerChatIntentDecision {
  name: DemoCustomerChatIntentName;
  classifier: string;
  action_requested: boolean;
}

export interface DemoCustomerChatSafetyDecision {
  decision: KnowledgeSafetyDecision;
  reason_code: KnowledgeSafetyReason;
  summary_sv: string;
  summary_en: string;
}

export interface DemoCustomerChatAssistantMessage {
  text_sv: string;
  text_en: string;
}

export type DemoCustomerChatToolEventType =
  | "safety"
  | "context_binding"
  | "backend_read"
  | "model_routing"
  | "verification"
  | "embedding"
  | "vector_search"
  | "context_build"
  | "generation"
  | "backend_step";

export type DemoCustomerChatToolEventStatus =
  | KnowledgeRagPhaseStatus
  | "clarification_required"
  | "unsupported";

export interface DemoCustomerChatToolEvent {
  sequence: number;
  type: DemoCustomerChatToolEventType;
  initiated_by:
    | "spring_orchestrator"
    | "knowledge_rag_service"
    | "gemini_customer_router"
    | "gemini_customer_answer";
  model_selected: boolean;
  name: string;
  status: DemoCustomerChatToolEventStatus;
  executed: boolean;
  summary_sv: string;
  summary_en: string;
  source_ref: string | null;
  evidence_ids: string[];
  latency_ms: number | null;
}

export interface DemoCustomerChatSource {
  kind: "customer_context" | "order_snapshot" | "company_policy";
  document_id: string | null;
  chunk_id: string | null;
  document_version: string | null;
  title: string;
  section_heading: string | null;
  source_ref: string;
  evidence_id: string;
  lifecycle: string | null;
  similarity: number | null;
  display_summary_sv: string;
  display_summary_en: string;
}

export interface DemoCustomerChatVerifiedClaim {
  text_sv: string;
  text_en: string;
  citation_ids: string[];
}

export interface DemoCustomerChatRagExecution {
  requested: boolean;
  outcome: KnowledgeRagOutcome | "not_run";
  backend: "pgvector_exact_cosine" | null;
  corpus_version: string | null;
  corpus_content_sha256: string | null;
  current_vector_search: boolean;
  embedding_executed: boolean;
  embedding_provider: string | null;
  embedding_model_id: string | null;
  embedding_dimensions: number | null;
  vector_match_count: number;
  verification_outcome: string;
  provider_route: GoogleGenAiProviderRoute | null;
  generation_model_id: string | null;
  provider_response_id: string | null;
  token_usage: ModelTokenUsage | null;
  error_code: string | null;
}

export interface DemoCustomerChatVerification {
  evaluation_status: "completed" | "not_run" | "not_applicable";
  fixed_customer_scope: boolean;
  order_source_verified: boolean;
  approved_policies_only: boolean;
  citations_within_returned_sources: boolean;
  semantic_claim_support_evaluated: boolean;
  no_business_write_capability: true;
  business_action_executed: false;
  overall_outcome: string;
}

export interface DemoCustomerChatReceipt {
  read_operations: number;
  provider_calls: number;
  embedding_calls: number;
  vector_searches: number;
  generation_calls: number;
  business_write_operations: 0;
  business_write_scope: "customer_order_and_refund_state";
  business_write_tools_available: false;
  business_action_executed: false;
  persistent_memory_used: false;
  total_latency_ms: number;
  model_id: string | null;
  estimated_cost_usd: number | null;
  cost_status: string;
  cost_basis: string;
}

export interface DemoCustomerChatErrorDetail {
  code: string;
  summary_sv: string;
  summary_en: string;
}

export interface DemoCustomerChatTurnResponse {
  contract_version: "nordly-demo-customer-chat-turn-v1";
  turn_id: string;
  mode: "controlled_synthetic_customer_chat";
  truth_label: string;
  truth_label_en: string;
  outcome: DemoCustomerChatOutcome;
  submitted_message: DemoCustomerChatSubmittedMessage;
  context: DemoCustomerChatContextReceipt;
  intent: DemoCustomerChatIntentDecision;
  safety: DemoCustomerChatSafetyDecision;
  assistant_message: DemoCustomerChatAssistantMessage;
  order: DemoOrder | null;
  tool_events: DemoCustomerChatToolEvent[];
  sources: DemoCustomerChatSource[];
  verified_claims: DemoCustomerChatVerifiedClaim[];
  rag: DemoCustomerChatRagExecution;
  verification: DemoCustomerChatVerification;
  receipt: DemoCustomerChatReceipt;
  error: DemoCustomerChatErrorDetail | null;
  limitations: string[];
}

export interface KnowledgeReplayQuestion {
  id: string;
  prompt_sv: string;
  prompt_en: string;
}

export interface KnowledgeEmbeddingProfile {
  provider: string;
  model_id: string;
  dimensions: number;
}

export interface KnowledgeQueryEmbedding {
  executed_in_this_run: boolean;
  latency_ms: number | null;
}

export type KnowledgeDocumentStatus = string;

export interface KnowledgeRankedMatch {
  rank: number;
  similarity: number | null;
  status: KnowledgeDocumentStatus;
  document_id: string;
  chunk_id: string;
  document_version: string;
  title: string;
  section_heading: string;
  owner_team: string;
  source_ref: string;
  evidence_id: string;
  display_summary_sv: string;
  display_summary_en: string;
  text: string;
}

export interface KnowledgeRagRankedMatch extends KnowledgeRankedMatch {
  content_sha256: string;
}

export interface KnowledgeRetrieval {
  backend: string;
  corpus_version: string;
  embedding_profile: KnowledgeEmbeddingProfile;
  query_embedding: KnowledgeQueryEmbedding;
  ranked_matches: KnowledgeRankedMatch[];
}

export interface KnowledgeClaim {
  text_sv: string;
  text_en: string;
  citation_ids: string[];
}

export interface KnowledgeAnswer {
  status: KnowledgeQuestionOutcome;
  summary_sv: string;
  summary_en: string;
  claims: KnowledgeClaim[];
}

export interface KnowledgeVerification {
  schema_pass: boolean;
  citations_seen: number;
  approved_documents_only: boolean;
  claim_support_pass: boolean;
  action_within_scope: boolean;
  overall_outcome: string;
}

export interface KnowledgeReceipt {
  tool_calls: string[];
  write_tools_available: boolean;
  action_executed: boolean;
  latency_ms: number | null;
  total_tokens: number | null;
  estimated_cost_usd: number | null;
  cost_status: string;
}

export interface KnowledgeReplayResponse {
  contract_version: string;
  run_id: string;
  mode: "recorded_replay";
  truth_label: string;
  model_backed: boolean;
  current_vector_search: boolean;
  question: KnowledgeReplayQuestion;
  retrieval: KnowledgeRetrieval;
  answer: KnowledgeAnswer;
  verification: KnowledgeVerification;
  receipt: KnowledgeReceipt;
  limitations: string[];
}

export type KnowledgeRagLocale = "sv" | "en";

export interface KnowledgeRagRequest {
  question: string;
  locale: KnowledgeRagLocale;
  confirm_live_ai: boolean;
}

export type KnowledgeRagOutcome =
  | KnowledgeQuestionOutcome
  | "confirmation_required"
  | "unavailable"
  | "rejected_output";

export type KnowledgeSafetyDecision = "ALLOW" | "BLOCK";

export type KnowledgeSafetyReason =
  | "NONE"
  | "PII_REQUEST"
  | "EMPLOYEE_COMPENSATION_REQUEST"
  | "SECRET_REQUEST"
  | "PROMPT_INJECTION"
  | "FINANCIAL_ACTION"
  | "WRITE_ACTION";

export interface KnowledgeSafetyResult {
  decision: KnowledgeSafetyDecision;
  reason_code: KnowledgeSafetyReason;
  summary_sv: string;
  summary_en: string;
}

export interface KnowledgeDocumentLibraryEligibilityRule {
  required_lifecycle: string;
  required_access_scope: string;
}

export interface KnowledgeDocumentLibraryEmbeddingProfile {
  provider: string;
  model_id: string;
  dimensions: number;
}

export type KnowledgeDocumentLibraryEligibilityReason =
  | "APPROVED_PUBLIC_DEMO"
  | "LIFECYCLE_NOT_APPROVED"
  | "PUBLIC_DEMO_SCOPE_MISSING";

export interface KnowledgeDocumentLibraryEligibility {
  eligible: boolean;
  reason_code: KnowledgeDocumentLibraryEligibilityReason;
  summary_sv: string;
  summary_en: string;
}

export interface KnowledgeDocumentLibraryChunk {
  id: string;
  section_heading: string;
  source_ref: string;
  evidence_id: string;
  display_summary_sv: string;
  display_summary_en: string;
  text: string | null;
  content_sha256: string | null;
}

export interface KnowledgeDocumentLibraryDocument {
  id: string;
  version: string;
  display_filename: string;
  document_type: string;
  classification: string;
  title: string;
  title_sv: string;
  summary_sv: string;
  summary_en: string;
  owner_team: string;
  lifecycle: string;
  effective_from: string;
  effective_until: string | null;
  access_scopes: string[];
  related_document_ids: string[];
  rag_eligibility: KnowledgeDocumentLibraryEligibility;
  content_visible: boolean;
  chunks: KnowledgeDocumentLibraryChunk[];
}

export interface KnowledgeDocumentLibraryResponse {
  contract_version: "nordly-knowledge-document-library-v2";
  mode: "read_only_corpus";
  truth_label: string;
  manifest_version: string;
  corpus_version: string;
  corpus_content_sha256: string;
  synthetic_only: boolean;
  current_vector_search: false;
  document_count: number;
  chunk_count: number;
  eligible_document_count: number;
  eligible_chunk_count: number;
  eligibility_rule: KnowledgeDocumentLibraryEligibilityRule;
  embedding_profile: KnowledgeDocumentLibraryEmbeddingProfile;
  documents: KnowledgeDocumentLibraryDocument[];
}

export type KnowledgeRagPhaseId =
  | "safety"
  | "eligibility_filter"
  | "query_embedding"
  | "vector_search"
  | "bounded_context"
  | "generation"
  | "java_verification";

export type KnowledgeRagPhaseStatus =
  | "completed"
  | "blocked"
  | "skipped"
  | "failed";

export interface KnowledgeRagPhase {
  id: KnowledgeRagPhaseId;
  status: KnowledgeRagPhaseStatus;
  executed: boolean;
  summary_sv: string;
  summary_en: string;
  latency_ms: number | null;
}

export interface KnowledgeRagQuestion {
  text: string;
  locale: KnowledgeRagLocale;
  redacted: boolean;
}

export interface KnowledgeRagQueryEmbedding {
  executed_in_this_run: boolean;
  provider: string | null;
  model_id: string | null;
  dimensions: number | null;
  latency_ms: number | null;
  input_characters: number | null;
  provider_billable_characters: number | null;
  provider_input_tokens: number | null;
}

export interface GoogleGenAiProviderRoute {
  transport: "developer_api" | "vertex_ai";
  authentication_mode: "api_key" | "adc";
  location: string | null;
}

export interface KnowledgeRagIndexSnapshot {
  status: "ready" | "not_ready";
  ready: boolean;
  indexed_chunks: number;
  current_chunks: number;
  expected_chunks: number;
}

export interface KnowledgeRagRetrieval {
  backend: "pgvector_exact_cosine";
  corpus_version: string;
  corpus_content_sha256: string;
  index_snapshot: KnowledgeRagIndexSnapshot | null;
  required_lifecycle: "APPROVED";
  required_access_scope: "public_demo";
  eligible_document_count: number;
  eligible_chunk_count: number;
  current_vector_search: boolean;
  top_k: number;
  minimum_similarity: number;
  query_embedding: KnowledgeRagQueryEmbedding;
  ranked_matches: KnowledgeRagRankedMatch[];
}

export interface KnowledgeRagVerification {
  evaluation_status: "completed" | "not_run" | "not_applicable";
  schema_pass: boolean;
  citations_within_retrieved_context: boolean;
  approved_documents_only: boolean;
  output_pii_scan_pass: boolean;
  output_policy_scan_pass: boolean;
  semantic_claim_support_evaluated: false;
  no_write_capability: boolean;
  overall_outcome: string;
}

export interface KnowledgeRagReceipt {
  provider_calls: number;
  embedding_calls: number;
  generation_calls: number;
  write_tools_available: false;
  action_executed: false;
  total_latency_ms: number;
  model_id: string | null;
  provider_response_id: string | null;
  token_usage: ModelTokenUsage | null;
  estimated_cost_usd: number | null;
  cost_status: string;
  cost_basis: string;
}

export interface KnowledgeRagError {
  code: string;
  summary_sv: string;
  summary_en: string;
}

export interface KnowledgeRagResponse {
  contract_version: "nordly-knowledge-rag-v3";
  run_id: string;
  mode: "live_rag";
  truth_label: string;
  truth_label_en: string;
  outcome: KnowledgeRagOutcome;
  provider_route: GoogleGenAiProviderRoute | null;
  question: KnowledgeRagQuestion;
  safety: KnowledgeSafetyResult;
  phases: KnowledgeRagPhase[];
  retrieval: KnowledgeRagRetrieval;
  answer: KnowledgeAnswer | null;
  verification: KnowledgeRagVerification;
  receipt: KnowledgeRagReceipt;
  error: KnowledgeRagError | null;
  limitations: string[];
}
