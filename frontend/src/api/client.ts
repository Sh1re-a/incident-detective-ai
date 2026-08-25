import type {
  ApiProblem,
  LiveInvestigationResult,
  RecordedReplayResult,
  ScenarioCatalogResponse,
} from "./types";

export class ApiError extends Error {
  readonly problem: ApiProblem;

  constructor(problem: ApiProblem) {
    super(problem.detail || problem.title);
    this.name = "ApiError";
    this.problem = problem;
  }
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (response.ok) {
    return (await response.json()) as T;
  }

  const fallback: ApiProblem = {
    title: "Request failed",
    status: response.status,
    detail: "The server could not complete this request.",
  };

  try {
    const problem = (await response.json()) as Partial<ApiProblem>;
    throw new ApiError({
      ...fallback,
      ...problem,
      status: response.status,
    });
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    throw new ApiError(fallback);
  }
}

export async function getScenarios(
  signal?: AbortSignal,
): Promise<ScenarioCatalogResponse> {
  return requestJson<ScenarioCatalogResponse>("/api/v1/scenarios", { signal });
}

export async function runRecordedReplay(
  scenarioId: string,
  signal?: AbortSignal,
): Promise<RecordedReplayResult> {
  return requestJson<RecordedReplayResult>(
    `/api/v1/scenarios/${encodeURIComponent(scenarioId)}/runs/recorded-replay`,
    { method: "POST", signal },
  );
}

export async function runLiveInvestigation(
  scenarioId: string,
  signal?: AbortSignal,
): Promise<LiveInvestigationResult> {
  return requestJson<LiveInvestigationResult>(
    `/api/v1/scenarios/${encodeURIComponent(scenarioId)}/runs/live-ai`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirm_live_ai: true }),
      signal,
    },
  );
}

export function toPublicErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.problem.code) {
      case "LIVE_AI_DISABLED":
      case "LIVE_AI_NOT_CONFIGURED":
        return "Live AI is not available right now. The recorded investigation is still ready.";
      case "LIVE_INVESTIGATION_TIMEOUT":
      case "MODEL_PROVIDER_TIMEOUT":
        return "The live investigation reached its time limit. No automatic retry was made.";
      case "MALFORMED_MODEL_RESPONSE":
      case "INVALID_MODEL_TOOL_ARGUMENTS":
        return "The model response was rejected because it did not match the safe contract.";
      case "MODEL_PROVIDER_ERROR":
        return "The model provider could not complete this run. No automatic retry was made.";
      default:
        return error.problem.detail;
    }
  }

  if (error instanceof DOMException && error.name === "AbortError") {
    return "The request was cancelled.";
  }

  return "The application could not reach the investigation API.";
}
