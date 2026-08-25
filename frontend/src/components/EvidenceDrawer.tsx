import { useEffect, useRef } from "react";

import type { Evidence } from "../api/types";
import {
  evidenceTypeLabel,
  formatTimestamp,
  humanizeCode,
} from "../lib/presentation";

interface EvidenceDrawerProps {
  evidence: Evidence | null;
  onClose: () => void;
}

export function EvidenceDrawer({ evidence, onClose }: EvidenceDrawerProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!evidence) {
      return;
    }

    closeButtonRef.current?.focus();
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [evidence, onClose]);

  if (!evidence) {
    return null;
  }

  return (
    <div className="drawer-backdrop" role="presentation" onMouseDown={onClose}>
      <aside
        className="evidence-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="evidence-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="drawer-header">
          <div>
            <span className={`evidence-kind ${evidence.evidence_type}`}>
              {evidenceTypeLabel(evidence)}
            </span>
            <h2 id="evidence-title">Evidence detail</h2>
          </div>
          <button
            ref={closeButtonRef}
            className="icon-button"
            type="button"
            aria-label="Close evidence detail"
            onClick={onClose}
          >
            ×
          </button>
        </header>

        <p className="evidence-summary">{evidence.display_summary}</p>

        <dl className="evidence-meta">
          <div>
            <dt>Evidence ID</dt>
            <dd>{evidence.evidence_id}</dd>
          </div>
          <div>
            <dt>Synthetic source</dt>
            <dd>{evidence.source_ref}</dd>
          </div>
          {"observed_at" in evidence ? (
            <div>
              <dt>Observed</dt>
              <dd>{formatTimestamp(evidence.observed_at)}</dd>
            </div>
          ) : null}
        </dl>

        <div className="evidence-content">
          <EvidenceContent evidence={evidence} />
        </div>

        <p className="synthetic-note">
          Synthetic evidence created for this portfolio scenario. It is not from
          a real company system.
        </p>
      </aside>
    </div>
  );
}

function EvidenceContent({ evidence }: { evidence: Evidence }) {
  switch (evidence.evidence_type) {
    case "metric":
      return (
        <>
          <p className="metric-value">
            <strong>{evidence.content.value}</strong>
            <span>{evidence.content.unit}</span>
          </p>
          <p className="content-label">{humanizeCode(evidence.content.metric_name)}</p>
          <KeyValueList values={evidence.content.labels} />
        </>
      );
    case "log":
      return (
        <>
          <div className="log-line">
            <span className={`log-level ${evidence.content.level.toLowerCase()}`}>
              {evidence.content.level}
            </span>
            <code>{evidence.content.message}</code>
          </div>
          <KeyValueList values={evidence.content.attributes} />
        </>
      );
    case "trace": {
      const longestSpan = Math.max(
        ...evidence.content.spans.map((span) => span.duration_ms),
        1,
      );
      return (
        <div className="trace-list">
          {evidence.content.spans.map((span) => (
            <div className="trace-span" key={span.span_id}>
              <div className="trace-span-heading">
                <strong>{humanizeCode(span.service)}</strong>
                <span>{span.duration_ms} ms</span>
              </div>
              <div className="trace-track" aria-hidden="true">
                <span
                  className={span.status === "OK" ? "ok" : "error"}
                  style={{ width: `${Math.max(4, (span.duration_ms / longestSpan) * 100)}%` }}
                />
              </div>
              <small>
                {span.operation} · {span.status}
              </small>
            </div>
          ))}
        </div>
      );
    }
    case "runbook":
      return (
        <>
          <p className="runbook-ref">
            {evidence.content.document_id} · v{evidence.content.document_version} · {" "}
            {evidence.content.chunk_id}
          </p>
          <blockquote>{evidence.content.text}</blockquote>
        </>
      );
  }
}

function KeyValueList({ values }: { values: Record<string, string> }) {
  return (
    <dl className="key-value-list">
      {Object.entries(values).map(([key, value]) => (
        <div key={key}>
          <dt>{humanizeCode(key)}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}
