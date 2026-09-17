import type {
  DemoCustomerChatConversationTurn,
  DemoCustomerChatTurnResponse,
  KnowledgeRagLocale,
} from "./api/generated";

export type CustomerChatTurn = {
  clientId: string;
  question: string;
  locale: KnowledgeRagLocale;
  response: DemoCustomerChatTurnResponse | null;
  errorCode: string | null;
};

type CustomerChatSessionV1 = {
  version: 1;
  savedAt: string;
  turns: CustomerChatTurn[];
};

const STORAGE_KEY = "nordly-demo-customer-chat:v1";
const MAX_TURNS = 12;
const MAX_AGE_MS = 2 * 60 * 60 * 1000;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isSafeResponse(value: unknown): value is DemoCustomerChatTurnResponse {
  if (!isRecord(value) || !isRecord(value.context) || !isRecord(value.receipt)) {
    return false;
  }
  return value.contract_version === "nordly-demo-customer-chat-turn-v1"
    && value.mode === "controlled_synthetic_customer_chat"
    && value.context.synthetic_only === true
    && value.context.persistent_memory === false
    && [
      "request_scoped_bounded_history",
      "request_only_fixed_context",
    ].includes(String(value.context.memory_scope))
    && value.receipt.business_write_operations === 0
    && value.receipt.business_write_tools_available === false
    && value.receipt.business_action_executed === false
    && value.receipt.persistent_memory_used === false
    && isRecord(value.submitted_message)
    && typeof value.submitted_message.text === "string"
    && (value.submitted_message.locale === "sv" || value.submitted_message.locale === "en");
}

function isSafeTurn(value: unknown): value is CustomerChatTurn {
  if (!isRecord(value) || !isSafeResponse(value.response)) return false;
  return typeof value.clientId === "string"
    && value.clientId.length > 0
    && typeof value.question === "string"
    && value.question === value.response.submitted_message.text
    && (value.locale === "sv" || value.locale === "en")
    && value.errorCode === null;
}

function storage(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

export function loadCustomerChatSession(now = Date.now()): CustomerChatTurn[] {
  const sessionStorage = storage();
  if (!sessionStorage) return [];
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!isRecord(parsed) || parsed.version !== 1 || !Array.isArray(parsed.turns)) {
      sessionStorage.removeItem(STORAGE_KEY);
      return [];
    }
    const savedAt = Date.parse(String(parsed.savedAt ?? ""));
    if (!Number.isFinite(savedAt) || now - savedAt > MAX_AGE_MS) {
      sessionStorage.removeItem(STORAGE_KEY);
      return [];
    }
    if (parsed.turns.length > MAX_TURNS || !parsed.turns.every(isSafeTurn)) {
      sessionStorage.removeItem(STORAGE_KEY);
      return [];
    }
    const turns = parsed.turns as CustomerChatTurn[];
    const [first] = turns;
    if (first && turns.some((turn) =>
      turn.response?.context.context_id !== first.response?.context.context_id
      || turn.response?.context.context_version !== first.response?.context.context_version
      || turn.response?.context.current_order_id !== first.response?.context.current_order_id
    )) {
      sessionStorage.removeItem(STORAGE_KEY);
      return [];
    }
    return turns;
  } catch {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage can be unavailable in privacy modes. Live chat still works.
    }
    return [];
  }
}

export function saveCustomerChatSession(turns: CustomerChatTurn[]): void {
  const sessionStorage = storage();
  if (!sessionStorage) return;
  const completed = turns
    .filter((turn): turn is CustomerChatTurn & { response: DemoCustomerChatTurnResponse } =>
      turn.response !== null && turn.errorCode === null,
    )
    .map((turn) => ({
      ...turn,
      question: turn.response.submitted_message.text,
      errorCode: null,
    }))
    .slice(-MAX_TURNS);
  try {
    if (completed.length === 0) {
      sessionStorage.removeItem(STORAGE_KEY);
      return;
    }
    const session: CustomerChatSessionV1 = {
      version: 1,
      savedAt: new Date().toISOString(),
      turns: completed,
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  } catch {
    // Persistence is an enhancement, never a requirement for a live answer.
  }
}

export function clearCustomerChatSession(): void {
  try {
    storage()?.removeItem(STORAGE_KEY);
  } catch {
    // The in-memory reset still succeeds.
  }
}

export function buildCustomerChatConversation(
  turns: CustomerChatTurn[],
): DemoCustomerChatConversationTurn[] {
  return turns
    .filter((turn): turn is CustomerChatTurn & { response: DemoCustomerChatTurnResponse } =>
      turn.response !== null
      && turn.errorCode === null
      && !turn.response.submitted_message.redacted
      && !["refused", "unavailable", "confirmation_required"].includes(
        turn.response.outcome,
      ),
    )
    .slice(-6)
    .map((turn) => ({
      customer_message: turn.response.submitted_message.text,
      assistant_message: turn.locale === "sv"
        ? turn.response.assistant_message.text_sv
        : turn.response.assistant_message.text_en,
    }));
}

export const customerChatSessionConfig = {
  storageKey: STORAGE_KEY,
  maxTurns: MAX_TURNS,
  maxAgeMs: MAX_AGE_MS,
};
