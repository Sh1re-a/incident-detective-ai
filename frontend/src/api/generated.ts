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
  credentials_configured: boolean;
  request_configured: boolean;
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
  model_id: string;
  dimensions: number;
  format_version: string;
  minimum_similarity: number;
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

export interface CapabilitiesResponse {
  contract_version: "capabilities-v3";
  synthetic_only: boolean;
  remediation_enabled: boolean;
  modes: ModeCapability[];
  tools: ToolCapability[];
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
  factual_result_matches_ground_truth: boolean;
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
  contract_version: "nordly-adk-turn-v1";
  run_id: string;
  session_id: string | null;
  turn_id: string;
  mode: "adk_live_ai";
  truth_label: string;
  outcome: AdkAgentOutcome;
  scenario: Scenario | null;
  safety: AdkSafetyDecision;
  runtime: AdkRuntimeProvenance;
  events: AdkRuntimeEvent[];
  tool_events: LiveToolEvent[];
  diagnosis: Diagnosis | null;
  verification: VerificationReport | null;
  comparison: ReplayComparison | null;
  verification_event: AdkVerificationEvent | null;
  receipt: AdkControlReceipt;
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
  text: string;
}

export interface KnowledgeDocumentLibraryDocument {
  id: string;
  version: string;
  title: string;
  owner_team: string;
  lifecycle: string;
  effective_from: string;
  effective_until: string | null;
  access_scopes: string[];
  rag_eligibility: KnowledgeDocumentLibraryEligibility;
  chunks: KnowledgeDocumentLibraryChunk[];
}

export interface KnowledgeDocumentLibraryResponse {
  contract_version: "nordly-knowledge-document-library-v1";
  mode: "read_only_corpus";
  truth_label: string;
  corpus_version: string;
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

export interface KnowledgeRagRetrieval {
  backend: "pgvector_exact_cosine";
  corpus_version: string;
  required_lifecycle: "APPROVED";
  required_access_scope: "public_demo";
  eligible_document_count: number;
  eligible_chunk_count: number;
  current_vector_search: boolean;
  top_k: number;
  minimum_similarity: number;
  query_embedding: KnowledgeRagQueryEmbedding;
  ranked_matches: KnowledgeRankedMatch[];
}

export interface KnowledgeRagVerification {
  schema_pass: boolean;
  citations_within_retrieved_context: boolean;
  approved_documents_only: boolean;
  output_pii_scan_pass: boolean;
  output_policy_scan_pass: boolean;
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
  contract_version: "nordly-knowledge-rag-v1";
  run_id: string;
  mode: "live_rag";
  truth_label: string;
  truth_label_en: string;
  outcome: KnowledgeRagOutcome;
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
