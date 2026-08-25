import type { Evidence, ToolName } from "../api/types";

export const toolPresentation: Record<
  ToolName,
  { label: string; shortLabel: string; index: string }
> = {
  get_metrics: {
    label: "Checked service health",
    shortLabel: "Metrics",
    index: "01",
  },
  search_logs: {
    label: "Searched releases and errors",
    shortLabel: "Logs",
    index: "02",
  },
  get_trace: {
    label: "Followed a failed checkout",
    shortLabel: "Trace",
    index: "03",
  },
  retrieve_runbooks: {
    label: "Consulted operational guidance",
    shortLabel: "Runbook",
    index: "04",
  },
};

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
