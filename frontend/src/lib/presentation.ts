import type { Evidence, ToolName } from "../api/types";

export const toolPresentation: Record<
  ToolName,
  { label: string; shortLabel: string }
> = {
  get_metrics: {
    label: "Checked service health",
    shortLabel: "Metrics",
  },
  search_logs: {
    label: "Searched releases and errors",
    shortLabel: "Logs",
  },
  get_trace: {
    label: "Followed a failed checkout",
    shortLabel: "Trace",
  },
  retrieve_runbooks: {
    label: "Consulted operational guidance",
    shortLabel: "Runbook",
  },
};

const TRAILING_FULL_STOPS = /\.+$/;

export function formatSequence(index: number): string {
  return String(index + 1).padStart(2, "0");
}

export function trimSentenceEnd(value: string): string {
  return value.trim().replace(TRAILING_FULL_STOPS, "");
}

export function humanizeCode(value: string | null): string {
  if (!value) {
    return "Not determined";
  }

  return value
    .toLowerCase()
    .split("_")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}

export function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZone: "UTC",
    timeZoneName: "short",
  }).format(new Date(value));
}

export function formatDuration(milliseconds: number): string {
  if (milliseconds < 1000) {
    return `${milliseconds} ms`;
  }

  return `${(milliseconds / 1000).toFixed(2)} s`;
}

export function formatCost(value: number | null): string {
  if (value === null) {
    return "No model called";
  }

  return `$${value.toFixed(4)}`;
}

export function evidenceTypeLabel(evidence: Evidence): string {
  switch (evidence.evidence_type) {
    case "metric":
      return "Metric";
    case "log":
      return "Log event";
    case "trace":
      return "Request trace";
    case "runbook":
      return "Runbook excerpt";
  }
}
