import type {
  InvestigationResult,
  LiveInvestigationResult,
  ModelTokenUsage,
  RunbookRetrievalMetadata,
} from "../api/types";
import {
  formatCost,
  formatDuration,
  formatSequence,
  humanizeCode,
  trimSentenceEnd,
  toolPresentation,
} from "../lib/presentation";

interface EngineeringViewProps {
  result: InvestigationResult | null;
  onOpenEvidence: (evidenceId: string) => void;
  onSwitchToStory: () => void;
}

export function EngineeringView({
  result,
  onOpenEvidence,
  onSwitchToStory,
}: EngineeringViewProps) {
  if (!result) {
    return <EngineeringEmpty onSwitchToStory={onSwitchToStory} />;
  }

  const isLive = result.mode === "live_ai";
  const rootCauseCorrect = result.comparison.root_cause_correct;
  const affectedServiceCorrect = result.comparison.affected_service_correct;
  const seenEvidenceIds = new Set(
    result.tool_events.flatMap((event) =>
      event.evidence.map((evidence) => evidence.evidence_id),
    ),
  );

  return (
    <div className="engineering-view">
      <section className="engineering-hero panel">
        <div>
          <p className="section-kicker">Inspect this run</p>
          <h2>From tool call to verified verdict</h2>
          <p>{result.truth_label}</p>
        </div>
        <div className="run-id-block">
          <span>Run ID</span>
          <code>{result.run_id}</code>
        </div>
      </section>

      <section className="pipeline-panel panel" aria-labelledby="pipeline-title">
        <div className="section-heading">
          <div>
            <p className="section-kicker">Bounded state machine</p>
            <h2 id="pipeline-title">Collect → Synthesize → Verify</h2>
          </div>
          <span className="case-count">45 s hard limit</span>
        </div>
        <ol className="pipeline-rail">
          <li className="complete">
            <span>01</span>
            <div>
              <strong>Collect</strong>
              <small>
                {isLive
                  ? `${result.tool_events.length} read-only tool calls returned evidence`
                  : `${result.tool_events.length} recorded trace ${result.tool_events.length === 1 ? "event" : "events"}; no tool was called now`}
              </small>
            </div>
          </li>
          <li className="complete">
            <span>02</span>
            <div>
              <strong>Synthesize</strong>
              <small>
                {isLive
                  ? "Structured model output, no tools"
                  : "Recorded structured diagnosis"}
              </small>
            </div>
          </li>
          <li className={result.status === "verification_failed" ? "failed" : "complete"}>
            <span>03</span>
            <div>
              <strong>Verify</strong>
              <small>Deterministic Java checks against hidden GroundTruth</small>
            </div>
          </li>
        </ol>
        <p className="pipeline-boundaries">
          Max 2 collection rounds · Max 8 tool calls · No remediation endpoint
        </p>
      </section>

      <div className="engineering-grid">
        <section className="tool-calls-panel panel" aria-labelledby="tool-calls-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Tool trace</p>
              <h2 id="tool-calls-title">
                {isLive ? "Read-only calls" : "Recorded trace events"}
              </h2>
            </div>
            <span className="case-count">
              {result.tool_events.length} {isLive ? "calls" : "events"}
            </span>
          </div>

          <div className="engineering-tool-list">
            {result.tool_events.map((event, index) => {
              const tool = toolPresentation[event.tool_name];
              return (
                <article key={event.event_id}>
                  <div className="engineering-tool-heading">
                    <span className="tool-index-small">{formatSequence(index)}</span>
                    <div>
                      <code>{event.tool_name}</code>
                      <strong>{tool.label}</strong>
                    </div>
                    {"collection_round" in event ? (
                      <span className="round-label">Round {event.collection_round}</span>
                    ) : (
                      <span className="round-label">Recorded</span>
                    )}
                  </div>
                  <p>{event.safe_summary}</p>
                  {"arguments" in event ? (
                    <details className="argument-detail">
                      <summary>Validated arguments</summary>
                      <pre>{JSON.stringify(event.arguments, null, 2)}</pre>
                    </details>
                  ) : null}
                  {"runbook_retrieval" in event && event.runbook_retrieval ? (
                    <RunbookRetrievalDetails metadata={event.runbook_retrieval} />
                  ) : null}
                  <div className="engineering-evidence-row">
                    {event.evidence.map((evidence) => (
                      <button
                        type="button"
                        key={evidence.evidence_id}
                        onClick={() => onOpenEvidence(evidence.evidence_id)}
                      >
                        <span>{evidence.evidence_type}</span>
                        {evidence.evidence_id}
                      </button>
                    ))}
                  </div>
                </article>
              );
            })}
          </div>
        </section>

        <section className="verification-panel panel" aria-labelledby="verification-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Deterministic checks</p>
              <h2 id="verification-title">Verification</h2>
            </div>
            <span
              className={`verification-state ${result.status === "verification_failed" ? "failed" : "passed"}`}
            >
              {result.status === "verification_failed" ? "Rejected" : "Completed"}
            </span>
          </div>

          <div className="verification-grid">
            <VerificationCard
              label="Diagnosis schema"
              value={result.verification.diagnosis_schema_pass ? "Pass" : "Fail"}
              passed={result.verification.diagnosis_schema_pass}
              detail="Structured output matched the contract"
            />
            <VerificationCard
              label="Citation validity"
              value={result.verification.citation_validity.valid ? "100% valid" : "Invalid IDs"}
              passed={result.verification.citation_validity.valid}
              detail="Every cited ID existed in seen evidence"
            />
            <VerificationCard
              label="Evidence precision"
              value={
                result.verification.evidence_precision.applicable &&
                result.verification.evidence_precision.score !== null
                  ? `${Math.round(result.verification.evidence_precision.score * 100)}% this run`
                  : "Not applicable"
              }
              passed={
                !result.verification.evidence_precision.applicable ||
                result.verification.evidence_precision.score === 1
              }
              detail={`${result.verification.evidence_precision.supported_triples}/${result.verification.evidence_precision.total_triples} supported claim-evidence links`}
            />
            <VerificationCard
              label="Claim coverage"
              value={
                result.verification.claim_coverage.applicable &&
                result.verification.claim_coverage.score !== null
                  ? `${Math.round(result.verification.claim_coverage.score * 100)}% this run`
                  : "Not applicable"
              }
              passed={
                !result.verification.claim_coverage.applicable ||
                result.verification.claim_coverage.score === 1
              }
              detail={
                result.verification.claim_coverage.applicable
                  ? `${result.verification.claim_coverage.matched_claim_count}/${result.verification.claim_coverage.reference_claim_count} hidden-reference claims matched`
                  : "No reference claims apply to this result"
              }
            />
            <VerificationCard
              label="Root cause"
              value={
                result.diagnosis.status === "insufficient_evidence"
                  ? result.comparison.abstention_correct
                    ? "Correct abstention"
                    : "Wrong abstention"
                  : rootCauseCorrect
                    ? "Matched"
                    : "Did not match"
              }
              passed={
                result.diagnosis.status === "insufficient_evidence"
                  ? result.comparison.abstention_correct
                  : rootCauseCorrect
              }
              detail="Compared after synthesis against hidden synthetic truth"
            />
            <VerificationCard
              label="Affected service"
              value={
                result.diagnosis.status === "insufficient_evidence"
                  ? "Not applicable"
                  : affectedServiceCorrect
                    ? "Matched"
                    : "Did not match"
              }
              passed={
                result.diagnosis.status === "insufficient_evidence" ||
                affectedServiceCorrect
              }
              detail="Scored separately from root-cause correctness"
            />
          </div>

          <div className="actual-expected">
            <div>
              <span>Returned</span>
              <strong>{humanizeCode(result.diagnosis.root_cause_code)}</strong>
              <small>{humanizeCode(result.diagnosis.affected_service)}</small>
            </div>
            <span aria-hidden="true">→</span>
            <div>
              <span>Hidden answer</span>
              <strong>{humanizeCode(result.comparison.expected_root_cause_code)}</strong>
              <small>{humanizeCode(result.comparison.expected_affected_service)}</small>
            </div>
          </div>
          <p className="this-run-note">
            These checks describe one completed synthetic run. They are not an
            eval-set accuracy claim.
          </p>

          {result.verification.evidence_precision.citation_support.length > 0 ? (
            <details className="technical-detail">
              <summary>Inspect claim-evidence support</summary>
              <div className="engineering-tool-list">
                {result.verification.evidence_precision.citation_support.map(
                  (support) => (
                    <article
                      key={`${support.claim_code}-${support.claim_value_code}-${support.evidence_id}`}
                    >
                      <div className="engineering-tool-heading">
                        <div>
                          <code>{support.claim_code}</code>
                          <strong>{humanizeCode(support.claim_value_code)}</strong>
                        </div>
                        <span className="round-label">
                          {support.supported ? "Supported" : "Not supported"}
                        </span>
                      </div>
                      <div className="engineering-evidence-row">
                        <button
                          type="button"
                          disabled={!seenEvidenceIds.has(support.evidence_id)}
                          onClick={() => onOpenEvidence(support.evidence_id)}
                        >
                          <span>
                            {!seenEvidenceIds.has(support.evidence_id)
                              ? "Unavailable"
                              : support.supported
                                ? "Direct"
                                : "Weak link"}
                          </span>
                          {support.evidence_id}
                        </button>
                      </div>
                    </article>
                  ),
                )}
              </div>
            </details>
          ) : null}
        </section>
      </div>

      <section className="metadata-panel panel" aria-labelledby="metadata-title">
        <div className="section-heading">
          <div>
            <p className="section-kicker">Reproducibility</p>
            <h2 id="metadata-title">Run metadata</h2>
          </div>
        </div>
        <dl className="metadata-grid">
          <Metadata label="Mode" value={isLive ? "Live AI" : "Recorded replay"} />
          <Metadata label="Latency" value={formatDuration(result.latency_ms)} />
          <Metadata label="Model" value={result.model_id ?? "No model called"} />
          <Metadata label="Prompt" value={result.prompt_version ?? "No prompt used"} />
          <Metadata
            label="Tokens"
            value={formatRunTokens(result.token_usage, isLive)}
          />
          <Metadata
            label="Investigation model estimate"
            value={
              result.estimated_cost_usd === null
                ? isLive
                  ? "Estimate unavailable"
                  : "No model called"
                : formatCost(result.estimated_cost_usd)
            }
          />
          <Metadata
            label="Model calls"
            value={isLive ? `${result.model_call_count} / 3 max` : "0"}
          />
          <Metadata
            label={isLive ? "Tool calls" : "Recorded events"}
            value={
              isLive
                ? `${result.tool_call_count} / 8 max`
                : String(result.tool_events.length)
            }
          />
          {isLive ? (
            <Metadata
              label="Prompt cache"
              value={formatPromptCache(result)}
            />
          ) : null}
        </dl>

        {isLive ? (
          <>
            {result.estimated_cost_usd === null ? (
              <p className="cost-basis">
                A cost estimate was unavailable for this live run. This does not mean the run cost
                $0. Cost basis: {trimSentenceEnd(result.estimated_cost_basis)}.
              </p>
            ) : (
              <p className="cost-basis">
                Cost basis: {trimSentenceEnd(result.estimated_cost_basis)}. This is an estimate,
                not a provider invoice. Embedding retrieval cost is not included unless the
                provider reports enough usage data.
              </p>
            )}
            <p className="cost-basis">
              {cacheExplanation(result)}
            </p>
            {result.model_cost_breakdown ? (
              <div className="model-call-list">
                <h3>Paid-list cost breakdown</h3>
                <CostLine
                  label="Uncached input"
                  value={result.model_cost_breakdown.uncached_input_usd}
                />
                <CostLine
                  label="Provider-reported cached input"
                  value={result.model_cost_breakdown.cached_input_usd}
                />
                <CostLine
                  label="Output + thinking"
                  value={result.model_cost_breakdown.output_usd}
                />
                <CostLine
                  label="Observed cache saving"
                  value={result.model_cost_breakdown.observed_cache_savings_usd}
                />
              </div>
            ) : null}
            <div className="model-call-list">
              <h3>Model calls</h3>
              {result.model_calls.map((call) => (
                <div key={`${call.phase}-${call.round}`}>
                  <span>{humanizeCode(call.phase)} · round {call.round}</span>
                  <strong>{formatDuration(call.latency_ms)}</strong>
                  <small>{formatModelCallTokens(call.token_usage)}</small>
                </div>
              ))}
            </div>
            {result.limitations.length > 0 ? (
              <details className="limitations">
                <summary>Known limitations for this run</summary>
                <ul>
                  {result.limitations.map((limitation) => (
                    <li key={limitation}>{limitation}</li>
                  ))}
                </ul>
              </details>
            ) : null}
          </>
        ) : (
          <p className="cost-basis">
            The recorded replay did not call a model, consume tokens or create an
            API cost estimate.
          </p>
        )}
      </section>
    </div>
  );
}

function formatRunTokens(
  usage: ModelTokenUsage | null,
  isLive: boolean,
): string {
  if (!usage) {
    return isLive ? "Provider did not report" : "No model called";
  }
  if (usage.total_tokens === null) {
    return "Total not reported";
  }
  return `${usage.total_tokens.toLocaleString("en-US")} total`;
}

function formatPromptCache(result: LiveInvestigationResult): string {
  const cache = result.prompt_cache;
  if (!cache.cache_hit_observed || cache.cached_input_tokens === null) {
    return "No provider-reported hit";
  }
  return `${cache.cached_input_tokens.toLocaleString("en-US")} cached tokens`;
}

function cacheExplanation(result: LiveInvestigationResult): string {
  const cache = result.prompt_cache;
  const reportCoverage = `${cache.provider_reported_model_calls}/${cache.model_call_count} model calls`;
  if (!cache.cache_hit_observed || cache.cached_input_tokens === null) {
    return `Gemini implicit caching is provider-managed. No cache hit was reported for this run (${reportCoverage}); missing cache data is not shown as zero.`;
  }
  const ratio = result.token_usage?.input_tokens
    ? ` (${((cache.cached_input_tokens / result.token_usage.input_tokens) * 100).toFixed(1)}% of prompt input)`
    : "";
  const saving = result.model_cost_breakdown?.observed_cache_savings_usd;
  const savingText = saving === null || saving === undefined
    ? ""
    : ` Estimated paid-list saving: ${formatCost(saving)}.`;
  return `Gemini reported ${cache.cached_input_tokens.toLocaleString("en-US")} cached input tokens across ${reportCoverage}${ratio}.${savingText}`;
}

function formatModelCallTokens(usage: ModelTokenUsage | null): string {
  if (!usage) {
    return "Provider usage not reported";
  }
  const input = formatTokenPart(usage.input_tokens, "input");
  const cached = usage.cached_input_tokens === null
    ? "cache not reported"
    : `${usage.cached_input_tokens.toLocaleString("en-US")} cached`;
  const output = formatTokenPart(usage.output_tokens, "output");
  return `${input} · ${cached} · ${output}`;
}

function formatTokenPart(value: number | null, label: string): string {
  return value === null
    ? `${label} not reported`
    : `${value.toLocaleString("en-US")} ${label}`;
}

function CostLine({ label, value }: { label: string; value: number | null }) {
  return (
    <div>
      <span>{label}</span>
      <strong>{value === null ? "Not reported" : formatCost(value)}</strong>
      <small>USD estimate</small>
    </div>
  );
}

function RunbookRetrievalDetails({
  metadata,
}: {
  metadata: RunbookRetrievalMetadata;
}) {
  const profile = metadata.embedding_profile;
  const usage = metadata.query_embedding;

  return (
    <details className="argument-detail">
      <summary>Retrieval metadata</summary>
      <dl className="metadata-grid">
        <Metadata label="Backend" value={metadata.backend} />
        <Metadata label="Corpus" value={metadata.corpus_version ?? "Fixture-scoped"} />
        <Metadata
          label="Embedding model"
          value={profile?.model_id ?? "Not used in fixture mode"}
        />
        <Metadata
          label="Vector profile"
          value={
            profile
              ? `${profile.dimensions} dimensions · ${profile.format_version}`
              : "Not applicable"
          }
        />
        <Metadata
          label="Minimum similarity"
          value={profile ? profile.minimum_similarity.toFixed(6) : "Not applicable"}
        />
        <Metadata
          label="Query latency"
          value={usage ? formatDuration(usage.latency_ms) : "Not applicable"}
        />
        <Metadata
          label="Local input characters"
          value={
            usage
              ? usage.local_input_characters.toLocaleString("en-US")
              : "Not applicable"
          }
        />
        <Metadata
          label="Provider input tokens"
          value={
            usage?.provider_input_tokens == null
              ? "Not reported"
              : usage.provider_input_tokens.toLocaleString("en-US")
          }
        />
      </dl>
      <div className="model-call-list">
        <h3>Ranked matches</h3>
        {metadata.matches.length === 0 ? <p>No match passed the threshold.</p> : null}
        {metadata.matches.map((match) => (
          <div key={`${match.rank}-${match.evidence_id}`}>
            <span>
              Rank {match.rank} ·{" "}
              {match.cosine_similarity == null
                ? "score not applicable"
                : `cosine ${match.cosine_similarity.toFixed(4)}`}
            </span>
            <strong>{match.evidence_id}</strong>
            <small>
              {match.content_sha256
                ? `Content SHA-256 ${match.content_sha256}`
                : "Content hash not reported in fixture mode"}
            </small>
          </div>
        ))}
      </div>
    </details>
  );
}

function EngineeringEmpty({ onSwitchToStory }: { onSwitchToStory: () => void }) {
  return (
    <section className="engineering-empty panel">
      <p className="section-kicker">Engineering view</p>
      <h2>Run a case before inspecting its trace</h2>
      <p>
        This view exposes tool events, evidence IDs, deterministic checks, model
        metadata, latency, tokens and the investigation model estimate. Retrieval
        calls also expose their measured backend and embedding metadata when available.
      </p>
      <div className="empty-architecture">
        <div>
          <span>01</span>
          <strong>Collect</strong>
          <small>Typed read-only tools</small>
        </div>
        <i aria-hidden="true" />
        <div>
          <span>02</span>
          <strong>Synthesize</strong>
          <small>Structured diagnosis</small>
        </div>
        <i aria-hidden="true" />
        <div>
          <span>03</span>
          <strong>Verify</strong>
          <small>Hidden GroundTruth</small>
        </div>
      </div>
      <button className="button primary-button" type="button" onClick={onSwitchToStory}>
        Open Story View
      </button>
    </section>
  );
}

function VerificationCard({
  label,
  value,
  detail,
  passed,
}: {
  label: string;
  value: string;
  detail: string;
  passed: boolean;
}) {
  return (
    <article className={passed ? "passed" : "failed"}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function Metadata({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
