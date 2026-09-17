import { afterEach, describe, expect, it } from "vitest";
import type { DemoCustomerChatTurnResponse } from "./api/generated";
import {
  buildCustomerChatConversation,
  clearCustomerChatSession,
  customerChatSessionConfig,
  loadCustomerChatSession,
  saveCustomerChatSession,
} from "./customerChatSession";
import type { CustomerChatTurn } from "./customerChatSession";

function response(
  text = "Var är min order?",
  overrides: Record<string, unknown> = {},
): DemoCustomerChatTurnResponse {
  return {
    contract_version: "nordly-demo-customer-chat-turn-v1",
    mode: "controlled_synthetic_customer_chat",
    outcome: "answered",
    submitted_message: { text, locale: "sv", redacted: false },
    assistant_message: {
      text_sv: "Din order är skickad.",
      text_en: "Your order has shipped.",
    },
    context: {
      context_version: "nordly-demo-customer-context-v1",
      context_id: "fixed-demo-customer",
      current_order_id: "NORD-2051",
      context_source_ref: "demo/context",
      order_source_ref: "demo/order",
      synthetic_only: true,
      persistent_memory: false,
      memory_scope: "request_scoped_bounded_history",
    },
    receipt: {
      business_write_operations: 0,
      business_write_tools_available: false,
      business_action_executed: false,
      persistent_memory_used: false,
    },
    ...overrides,
  } as DemoCustomerChatTurnResponse;
}

function turn(text = "Var är min order?"): CustomerChatTurn {
  return {
    clientId: "turn-1",
    question: text,
    locale: "sv",
    response: response(text),
    errorCode: null,
  };
}

afterEach(() => clearCustomerChatSession());

describe("customer chat session", () => {
  it("restores only a completed backend-verified public turn", () => {
    saveCustomerChatSession([turn()]);

    expect(loadCustomerChatSession()).toEqual([turn()]);
  });

  it("stores the backend-redacted message instead of the raw question", () => {
    const redacted = "[STOPPAD OCH MASKERAD AV SÄKERHETSGRINDEN]";
    const protectedTurn = turn("en rå lönefråga");
    protectedTurn.response = response(redacted, {
      submitted_message: { text: redacted, locale: "sv", redacted: true },
    });

    saveCustomerChatSession([protectedTurn]);

    const stored = window.sessionStorage.getItem(customerChatSessionConfig.storageKey) ?? "";
    expect(stored).toContain(redacted);
    expect(stored).not.toContain("en rå lönefråga");
    expect(loadCustomerChatSession()[0]?.question).toBe(redacted);
  });

  it("does not persist pending or failed turns", () => {
    saveCustomerChatSession([
      { ...turn(), response: null },
      { ...turn(), clientId: "turn-2", response: null, errorCode: "HTTP_503" },
    ]);

    expect(window.sessionStorage.getItem(customerChatSessionConfig.storageKey)).toBeNull();
  });

  it("fails closed for stale or write-enabled snapshots", () => {
    const unsafe = turn();
    unsafe.response = response(unsafe.question, {
      receipt: {
        business_write_operations: 1,
        business_write_tools_available: true,
        business_action_executed: true,
        persistent_memory_used: false,
      },
    });
    window.sessionStorage.setItem(customerChatSessionConfig.storageKey, JSON.stringify({
      version: 1,
      savedAt: new Date().toISOString(),
      turns: [unsafe],
    }));

    expect(loadCustomerChatSession()).toEqual([]);
    expect(window.sessionStorage.getItem(customerChatSessionConfig.storageKey)).toBeNull();
  });

  it("expires the local transcript after two hours", () => {
    saveCustomerChatSession([turn()]);
    const future = Date.now() + customerChatSessionConfig.maxAgeMs + 1;

    expect(loadCustomerChatSession(future)).toEqual([]);
  });

  it("builds at most six safe context turns without using blocked input", () => {
    const allowed = Array.from({ length: 7 }, (_, index) => ({
      ...turn(`Fråga ${index}`),
      clientId: `turn-${index}`,
      response: response(`Fråga ${index}`),
    }));
    const blocked = {
      ...turn("hemlig fråga"),
      clientId: "blocked",
      response: response("[MASKERAD]", {
        outcome: "refused",
        submitted_message: {
          text: "[MASKERAD]",
          locale: "sv",
          redacted: true,
        },
      }),
    };

    const history = buildCustomerChatConversation([...allowed, blocked]);

    expect(history).toHaveLength(6);
    expect(history[0]?.customer_message).toBe("Fråga 1");
    expect(history[5]).toEqual({
      customer_message: "Fråga 6",
      assistant_message: "Din order är skickad.",
    });
    expect(JSON.stringify(history)).not.toContain("hemlig fråga");
  });
});
