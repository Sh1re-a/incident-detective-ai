import type {
  AdkAgentTurnRequest,
  AdkAgentTurnResponse,
  ApiProblemResponse,
  CapabilitiesResponse,
  DemoCustomerChatTurnRequest,
  DemoCustomerChatTurnResponse,
  DemoOrderCatalogResponse,
  DemoOrderLookupResponse,
  GeneratedCaseLiveRequest,
  GeneratedCaseRunResult,
  DemoWorldResponse,
  IncidentLabPlanRequest,
  IncidentLabPlanResponse,
  IncidentLabFollowUpRequest,
  IncidentLabFollowUpResponse,
  IncidentLabReplayAvailabilityResponse,
  IncidentLabReplayResponse,
  IncidentLabRunRequest,
  IncidentLabRunResponse,
  KnowledgeRagRequest,
  KnowledgeRagResponse,
  KnowledgeDocumentLibraryResponse,
  KnowledgeReplayResponse,
  LiveInvestigationRequest,
  LiveInvestigationResult,
  LiveAiStatusResponse,
  RecordedReplayResult,
  RetrievalEvalProofResponse,
  ScenarioCatalogResponse,
} from "./generated";

export class IncidentApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(message);
    this.name = "IncidentApiError";
  }
}

async function getJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  const response = await fetch(path, {
    ...init,
    headers,
  });

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as
      | ApiProblemResponse
      | null;

    throw new IncidentApiError(
      problem?.detail ?? `Request failed with status ${response.status}.`,
      response.status,
      problem?.code,
      parseRetryAfter(response.headers?.get?.("Retry-After") ?? null),
    );
  }

  return response.json() as Promise<T>;
}

function parseRetryAfter(value: string | null) {
  if (value === null) return undefined;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined;
}

export function getCapabilities(signal?: AbortSignal) {
  return getJson<CapabilitiesResponse>("/api/v1/capabilities", { signal });
}

export function getLiveAiStatus(signal?: AbortSignal) {
  return getJson<LiveAiStatusResponse>("/api/v1/live-ai/status", { signal });
}

export function getIncidentLabReplayAvailability(signal?: AbortSignal) {
  return getJson<IncidentLabReplayAvailabilityResponse>(
    "/api/v1/incident-lab/recorded-replay",
    { signal },
  );
}

export function createIncidentLabPlan(
  request: IncidentLabPlanRequest,
  signal?: AbortSignal,
) {
  return getJson<IncidentLabPlanResponse>("/api/v1/incident-lab/plans", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
}

export function runIncidentLab(
  request: IncidentLabRunRequest,
  signal?: AbortSignal,
) {
  return getJson<IncidentLabRunResponse>("/api/v1/incident-lab/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
}

export function runIncidentLabFollowUp(
  request: IncidentLabFollowUpRequest,
  signal?: AbortSignal,
) {
  return getJson<IncidentLabFollowUpResponse>("/api/v1/incident-lab/follow-ups", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
}

export function runIncidentLabReplay(signal?: AbortSignal) {
  return getJson<IncidentLabReplayResponse>(
    "/api/v1/incident-lab/runs/recorded-replay",
    { method: "POST", signal },
  );
}

export function getScenarios(signal?: AbortSignal) {
  return getJson<ScenarioCatalogResponse>("/api/v1/scenarios", { signal });
}

export function getRetrievalEvalProof(signal?: AbortSignal) {
  return getJson<RetrievalEvalProofResponse>(
    "/api/v1/proof/evals/retrieval",
    { signal },
  );
}

export function getDemoWorld(signal?: AbortSignal) {
  return getJson<DemoWorldResponse>("/api/v1/demo-world", { signal });
}

export function getDemoOrders(signal?: AbortSignal) {
  return getJson<DemoOrderCatalogResponse>("/api/v1/demo-orders", { signal });
}

export function getDemoOrder(orderId: string, signal?: AbortSignal) {
  return getJson<DemoOrderLookupResponse>(
    `/api/v1/demo-orders/${encodeURIComponent(orderId)}`,
    { signal },
  );
}

export function runDemoCustomerChatTurn(
  request: DemoCustomerChatTurnRequest,
  signal?: AbortSignal,
) {
  return getJson<DemoCustomerChatTurnResponse>(
    "/api/v1/demo-customer/chat/turns",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      signal,
    },
  );
}

export function getKnowledgeDocuments(signal?: AbortSignal) {
  return getJson<KnowledgeDocumentLibraryResponse>(
    "/api/v1/knowledge/documents",
    { signal },
  );
}

export function runKnowledgeReplay(
  questionId: string,
  signal?: AbortSignal,
) {
  return getJson<KnowledgeReplayResponse>(
    `/api/v1/knowledge/questions/${encodeURIComponent(questionId)}/runs/recorded-replay`,
    { method: "POST", signal },
  );
}

export function runKnowledgeRag(
  request: KnowledgeRagRequest,
  signal?: AbortSignal,
) {
  return getJson<KnowledgeRagResponse>(
    "/api/v1/knowledge/questions/runs/rag",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      signal,
    },
  );
}

export function runRecordedReplay(
  scenarioId: string,
  signal?: AbortSignal,
) {
  return getJson<RecordedReplayResult>(
    `/api/v1/scenarios/${encodeURIComponent(scenarioId)}/runs/recorded-replay`,
    { method: "POST", signal },
  );
}

export function runLiveInvestigation(
  scenarioId: string,
  signal?: AbortSignal,
) {
  const body: LiveInvestigationRequest = { confirm_live_ai: true };

  return getJson<LiveInvestigationResult>(
    `/api/v1/scenarios/${encodeURIComponent(scenarioId)}/runs/live-ai`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    },
  );
}

export function runGeneratedCase(
  request: Omit<GeneratedCaseLiveRequest, "confirm_live_ai">,
  signal?: AbortSignal,
) {
  const body: GeneratedCaseLiveRequest = {
    ...request,
    confirm_live_ai: true,
  };

  return getJson<GeneratedCaseRunResult>(
    "/api/v1/generated-cases/runs/live-ai",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    },
  );
}

export function runAgentTurn(
  request: AdkAgentTurnRequest,
  signal?: AbortSignal,
) {
  return getJson<AdkAgentTurnResponse>("/api/v1/agent/turns", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
    signal,
  });
}
