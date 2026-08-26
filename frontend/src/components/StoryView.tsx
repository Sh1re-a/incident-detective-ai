import type {
  InvestigationResult,
  RunMode,
  Scenario,
} from "../api/types";
import {
  formatSequence,
  formatTimestamp,
  humanizeCode,
  toolPresentation,
} from "../lib/presentation";

export type RunPhase =
  | "idle"
  | "running"
  | "revealing"
  | "success"
  | "error";

interface StoryViewProps {
  scenario: Scenario;
  phase: RunPhase;
  pendingMode: RunMode | null;
  result: InvestigationResult | null;
  visibleToolEventCount: number;
  errorMessage: string | null;
  onRunReplay: () => void;
  onRequestLive: () => void;
  onSkipReplay: () => void;
  onOpenEvidence: (evidenceId: string) => void;
}

export function StoryView({
  scenario,
  phase,
  pendingMode,
  result,
  visibleToolEventCount,
  errorMessage,
  onRunReplay,
  onRequestLive,
  onSkipReplay,
  onOpenEvidence,
}: StoryViewProps) {
  const busy = phase === "running" || phase === "revealing";
  const visibleEvents = result
    ? result.tool_events.slice(0, visibleToolEventCount)
    : [];
  const resultReady = result !== null && phase === "success";

  return (
    <div className="story-layout">
      <div className="story-main-column">
        <section className="incident-card panel" aria-labelledby="incident-title">
          <div className="incident-card-heading">
            <div>
              <p className="section-kicker">Incident opened</p>
              <h2 id="incident-title">{scenario.title}</h2>
            </div>
            <span className="incident-time">
              {formatTimestamp(scenario.incident_started_at)}
            </span>
          </div>

          <p className="incident-description">{scenario.description}</p>

          <div className="impact-callout">
            <span>Estimated business impact</span>
            <strong>{scenario.business_impact_summary}</strong>
            <small>Synthetic estimate for this case</small>
          </div>

          <div className="symptom-grid">
            {scenario.initial_symptoms.map((symptom) => (
              <article className="symptom" key={symptom.symptom_code}>
                <span className="pulse-dot" aria-hidden="true" />
                <div>
                  <strong>{symptom.summary}</strong>
                  <small>{formatTimestamp(symptom.observed_at)}</small>
                </div>
              </article>
            ))}
          </div>

          <div className="service-row" aria-label="Affected services">
            {scenario.affected_services.map((service) => (
              <span key={service}>{humanizeCode(service)}</span>
            ))}
          </div>
        </section>

        <section className="launcher panel" aria-labelledby="launcher-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Your turn</p>
              <h2 id="launcher-title">Start the investigation</h2>
            </div>
            <span className="read-only-badge">Read-only</span>
          </div>

          <p className="launcher-copy">
            Watch the system collect evidence, explain its diagnosis and prove
            every cited ID against a hidden synthetic answer.
          </p>

          <div className="launcher-actions">
            <button
              className="button primary-button"
              type="button"
              disabled={busy}
              onClick={onRunReplay}
            >
              <span aria-hidden="true">▶</span>
              Play free recorded investigation
            </button>
            <button
              className="button secondary-button"
              type="button"
              disabled={busy}
              onClick={onRequestLive}
            >
              <span className="live-spark" aria-hidden="true" />
              Run live AI
            </button>
          </div>

          <div className="mode-explainer">
            <span>Replay: deterministic, instant and $0</span>
            <span>Live: Gemini chooses the tools</span>
          </div>

          <RunStatus
            phase={phase}
            pendingMode={pendingMode}
            result={result}
            errorMessage={errorMessage}
            onRunReplay={onRunReplay}
          />
        </section>

        <section className="timeline-panel panel" aria-labelledby="timeline-title">
          <div className="section-heading">
            <div>
              <p className="section-kicker">Evidence trail</p>
              <h2 id="timeline-title">Investigation timeline</h2>
            </div>
            {phase === "revealing" && result ? (
              <button className="text-button" type="button" onClick={onSkipReplay}>
                Skip playback
              </button>
            ) : null}
          </div>

          {visibleEvents.length === 0 ? (
            <EmptyTimeline phase={phase} pendingMode={pendingMode} />
          ) : (
            <ol className="tool-timeline">
              {visibleEvents.map((event, index) => {
                const tool = toolPresentation[event.tool_name];
                return (
                  <li className="tool-step" key={event.event_id}>
                    <div className="tool-index" aria-hidden="true">
                      {formatSequence(index)}
                    </div>
                    <div className="tool-step-content">
                      <div className="tool-step-heading">
                        <div>
                          <span>{tool.shortLabel}</span>
                          <h3>{tool.label}</h3>
                        </div>
                        <span className="step-status">Complete</span>
                      </div>
                      <p>{event.safe_summary}</p>
                      <div className="evidence-chips" aria-label="Returned evidence">
                        {event.evidence.map((evidence) => (
                          <button
                            type="button"
                            key={evidence.evidence_id}
                            onClick={() => onOpenEvidence(evidence.evidence_id)}
                          >
                            <span>{evidence.evidence_type}</span>
                            {evidence.display_summary}
                          </button>
                        ))}
                      </div>
                    </div>
                    {index < visibleEvents.length - 1 ? (
                      <span className="timeline-connector" aria-hidden="true" />
                    ) : null}
                  </li>
                );
              })}
            </ol>
          )}
        </section>
      </div>

      <aside className="story-result-column">
        {resultReady ? (
          <DiagnosisPanel result={result} onOpenEvidence={onOpenEvidence} />
        ) : (
          <ResultPlaceholder phase={phase} />
        )}
      </aside>
    </div>
  );
}

function RunStatus({
  phase,
  pendingMode,
  result,
  errorMessage,
  onRunReplay,
}: {
  phase: RunPhase;
  pendingMode: RunMode | null;
  result: InvestigationResult | null;
  errorMessage: string | null;
  onRunReplay: () => void;
}) {
  if (phase === "idle") {
    return null;
  }

  if (phase === "running") {
    const live = pendingMode === "live_ai";
    return (
      <div className="run-status running" role="status" aria-live="polite">
        <span className="status-spinner" aria-hidden="true" />
        <div>
          <strong>{live ? "Live investigation running" : "Loading recorded run"}</strong>
          <p>
            {live
              ? "Tool events will appear only after Gemini returns. No progress is simulated."
              : "Preparing the deterministic evidence playback."}
          </p>
        </div>
      </div>
    );
  }

  if (phase === "revealing") {
    return (
      <div className="run-status replaying" role="status" aria-live="polite">
        <span className="status-spinner" aria-hidden="true" />
        <div>
          <strong>Playing a recorded investigation</strong>
          <p>The finished deterministic evidence trail is being revealed.</p>
        </div>
      </div>
    );
  }

  if (phase === "error") {
    return (
      <div className="run-status failed" role="alert">
        <span aria-hidden="true">!</span>
        <div>
          <strong>
            {pendingMode === "live_ai"
              ? "Live investigation did not complete"
              : "Recorded investigation did not load"}
          </strong>
          <p>{errorMessage}</p>
          {pendingMode === "live_ai" ? (
            <button className="text-button" type="button" onClick={onRunReplay}>
              Try the recorded investigation
            </button>
          ) : null}
        </div>
      </div>
    );
  }

  return result ? (
    <div className={`truth-strip ${result.mode}`} role="status" aria-live="polite">
      <span aria-hidden="true">●</span>
      <strong>{result.truth_label}</strong>
    </div>
  ) : null;
}

function EmptyTimeline({
  phase,
  pendingMode,
}: {
  phase: RunPhase;
  pendingMode: RunMode | null;
}) {
  if (phase === "running" && pendingMode === "live_ai") {
    return (
      <div className="timeline-empty active">
        <span className="radar-mark" aria-hidden="true" />
        <h3>Waiting for the real model response</h3>
        <p>Nothing shown here is invented while the request is in flight.</p>
      </div>
    );
  }

  return (
    <div className="timeline-empty">
      <div className="mini-pipeline" aria-hidden="true">
        <span>Collect</span>
        <i />
        <span>Synthesize</span>
        <i />
        <span>Verify</span>
      </div>
      <h3>The evidence trail will appear here</h3>
      <p>Every conclusion must link back to evidence returned by a read-only tool.</p>
    </div>
  );
}

function ResultPlaceholder({ phase }: { phase: RunPhase }) {
  const busy = phase === "running" || phase === "revealing";
  return (
    <section className={`result-placeholder panel${busy ? " active" : ""}`}>
      <span className="verdict-orbit" aria-hidden="true">
        <i />
      </span>
      <p className="section-kicker">Verdict</p>
      <h2>{busy ? "Following the evidence…" : "Diagnosis not opened"}</h2>
      <p>
        {busy
          ? "The final answer stays hidden until collection and verification finish."
          : "Run the case to reveal the root cause, proof and safest next step."}
      </p>
      <div className="placeholder-checks" aria-hidden="true">
        <span>Schema</span>
        <span>Citations</span>
        <span>Hidden answer</span>
      </div>
    </section>
  );
}

function DiagnosisPanel({
  result,
  onOpenEvidence,
}: {
  result: InvestigationResult;
  onOpenEvidence: (evidenceId: string) => void;
}) {
  const diagnosis = result.diagnosis;
  const rejected = result.status === "verification_failed";
  const abstained = diagnosis.status === "insufficient_evidence";
  const correct = diagnosisResultCorrect(result);
  const precision = result.verification.evidence_precision;
  const coverage = result.verification.claim_coverage;
  const coveragePassed = !coverage.applicable || coverage.score === 1;
  const evidenceProofPassed =
    result.verification.citation_validity.valid &&
    (!precision.applicable || precision.score === 1);
  const verificationProofPassed = evidenceProofPassed && coveragePassed;
  const unknownEvidenceIds = new Set(
    result.verification.citation_validity.unknown_evidence_ids,
  );
  const supportByCitation = new Map(
    precision.citation_support.map((support) => [
      citationKey(
        support.claim_code,
        support.claim_value_code,
        support.evidence_id,
      ),
      support.supported,
    ]),
  );
  const verdictTone = rejected
    ? "rejected"
    : abstained || (correct && !verificationProofPassed)
      ? "safe"
      : "verified";

  return (
    <section className={`diagnosis-panel panel${rejected ? " rejected" : ""}`}>
      <div className="diagnosis-topline">
        <span className={`verdict-badge ${verdictTone}`}>
          {rejected
            ? "Rejected by verifier"
            : abstained
              ? "Safe abstention"
              : correct && verificationProofPassed
                ? "Verified for this run"
                : correct
                  ? "Diagnosis matched · verification incomplete"
                  : "Verification finding"}
        </span>
        <span className="mode-label">
          {result.mode === "live_ai" ? "Live AI" : "Recorded replay"}
        </span>
      </div>

      <p className="section-kicker">What happened</p>
      <h2>{diagnosis.business_summary}</h2>

      <div className="root-cause-grid">
        <div>
          <span>Root cause</span>
          <strong>{humanizeCode(diagnosis.root_cause_code)}</strong>
        </div>
        <div>
          <span>Affected service</span>
          <strong>{humanizeCode(diagnosis.affected_service)}</strong>
        </div>
      </div>

      <details className="technical-detail">
        <summary>Technical detail</summary>
        <p>{diagnosis.technical_summary}</p>
      </details>

      <div className="claim-list">
        <h3>Claims and proof</h3>
        {diagnosis.claims.map((claim) => (
          <article key={`${claim.claim_code}-${claim.claim_value_code}`}>
            <span>{humanizeCode(claim.claim_code)}</span>
            <p>{claim.display_text}</p>
            <div className="claim-citations">
              {claim.evidence_ids.map((evidenceId) => {
                const unavailable = unknownEvidenceIds.has(evidenceId);
                const supported = supportByCitation.get(
                  citationKey(
                    claim.claim_code,
                    claim.claim_value_code,
                    evidenceId,
                  ),
                );
                const supportLabel = unavailable
                  ? "unavailable"
                  : supported === true
                    ? "direct support"
                    : supported === false
                      ? "not direct support"
                      : "not scored";

                return (
                  <button
                    type="button"
                    key={evidenceId}
                    disabled={unavailable}
                    onClick={() => onOpenEvidence(evidenceId)}
                  >
                    {unavailable ? "Evidence unavailable" : "Open evidence"}
                    <small>{evidenceId} · {supportLabel}</small>
                  </button>
                );
              })}
            </div>
          </article>
        ))}
      </div>

      <div className="safe-step">
        <span aria-hidden="true">↳</span>
        <div>
          <p className="section-kicker">Safe next step</p>
          <strong>{diagnosis.safe_next_step.summary}</strong>
          <small>
            Human approval required · No action was executed
          </small>
        </div>
      </div>

      <div className="proof-strip">
        <ProofItem
          label="Schema"
          value={result.verification.diagnosis_schema_pass ? "Valid" : "Failed"}
          passed={result.verification.diagnosis_schema_pass}
        />
        <ProofItem
          label="Evidence proof"
          value={
            !result.verification.citation_validity.valid
              ? "Invalid IDs"
              : precision.applicable
                ? `${precision.supported_triples}/${precision.total_triples} direct`
                : "Not applicable"
          }
          passed={evidenceProofPassed}
        />
        <ProofItem
          label="Answer coverage"
          value={
            coverage.applicable
              ? `${coverage.matched_claim_count}/${coverage.reference_claim_count} key facts`
              : "Not applicable"
          }
          passed={coveragePassed}
        />
        <ProofItem
          label="Hidden answer"
          value={correct ? "Matched" : "Did not match"}
          passed={correct}
        />
      </div>
      <p className="this-run-note">Verification applies to this run, not overall accuracy.</p>
    </section>
  );
}

function ProofItem({
  label,
  value,
  passed,
}: {
  label: string;
  value: string;
  passed: boolean;
}) {
  return (
    <div>
      <span>{label}</span>
      <strong>
        <i className={passed ? "passed" : "failed"} aria-hidden="true">
          {passed ? "✓" : "×"}
        </i>
        {value}
      </strong>
    </div>
  );
}

function diagnosisResultCorrect(result: InvestigationResult): boolean {
  if (result.diagnosis.status === "insufficient_evidence") {
    return result.comparison.abstention_correct;
  }

  return (
    result.comparison.root_cause_correct &&
    result.comparison.affected_service_correct
  );
}

function citationKey(
  claimCode: string,
  claimValueCode: string,
  evidenceId: string,
): string {
  return `${claimCode}\u0000${claimValueCode}\u0000${evidenceId}`;
}
