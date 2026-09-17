import { describe, expect, it } from "vitest";
import type { DemoCustomerChatTurnResponse } from "./api/generated";
import {
  buildCustomerChatConversation,
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

describe("customer chat session", () => {
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
