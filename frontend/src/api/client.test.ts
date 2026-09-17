import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getCapabilities,
  getDemoOrder,
  getDemoOrders,
  getIncidentLabReplayAvailability,
  runAgentTurn,
  runDemoCustomerChatTurn,
  runIncidentLabReplay,
} from "./client";

describe("Incident API client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("preserves the backend problem code without exposing another payload", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            title: "Live AI is busy",
            status: 429,
            detail: "The public live investigation limit has been reached.",
            code: "LIVE_AI_RATE_LIMITED",
          }),
          {
            status: 429,
            headers: {
              "Content-Type": "application/problem+json",
              "Retry-After": "37",
            },
          },
        ),
      ),
    );

    await expect(getCapabilities()).rejects.toMatchObject({
      name: "IncidentApiError",
      message: "The public live investigation limit has been reached.",
      status: 429,
      code: "LIVE_AI_RATE_LIMITED",
      retryAfterSeconds: 37,
    });
  });

  it("sends the explicit live confirmation in the ADK turn body", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ outcome: "completed" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await runAgentTurn({
      message: "Undersök det syntetiska kataloglarmet",
      confirm_live_ai: true,
      incident_family: "catalog_cache_invalidation",
      evidence_mode: "diagnostic",
      noise_level: "none",
      seed: 42,
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/agent/turns",
      expect.objectContaining({
        method: "POST",
        body: expect.stringContaining('"confirm_live_ai":true'),
      }),
    );
  });

  it("reads the synthetic demo-order catalog from the backend", async () => {
    const catalog = {
      contract_version: "nordly-demo-order-catalog-v1",
      mode: "read_only_synthetic_order_catalog",
      truth_label: "SYNTETISKA DEMOORDER",
      truth_label_en: "SYNTHETIC DEMO ORDERS",
      catalog_version: "nordly-demo-orders-v1",
      snapshot_at: "2026-09-15T10:00:00Z",
      synthetic_only: true,
      order_count: 1,
      orders: [],
      action_receipt: {
        operation: "list_demo_orders",
        read_operations: 1,
        records_returned: 1,
        write_operations: 0,
        ai_calls: 0,
        write_tools_available: false,
        action_executed: false,
      },
      limitations: [],
    };
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(catalog), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(getDemoOrders()).resolves.toEqual(catalog);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/demo-orders",
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    );
  });

  it("encodes the order id before reading one synthetic order", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ order: { order_id: "NORD/2048" } }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await getDemoOrder("NORD/2048");

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/demo-orders/NORD%2F2048",
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    );
  });

  it.each([false, true])(
    "posts one bounded customer-chat turn with live confirmation %s",
    async (confirmLiveAi) => {
      const response = {
        contract_version: "nordly-demo-customer-chat-turn-v1",
        turn_id: "nordly-customer-turn-test",
        mode: "controlled_synthetic_customer_chat",
      };
      const fetchMock = vi.fn().mockResolvedValue(
        new Response(JSON.stringify(response), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      );
      vi.stubGlobal("fetch", fetchMock);

      await expect(runDemoCustomerChatTurn({
        message: "Var är min beställning?",
        locale: "sv",
        confirm_live_ai: confirmLiveAi,
        recent_conversation: [],
      })).resolves.toEqual(response);

      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/v1/demo-customer/chat/turns",
        expect.objectContaining({
          method: "POST",
          headers: expect.any(Headers),
          body: JSON.stringify({
            message: "Var är min beställning?",
            locale: "sv",
            confirm_live_ai: confirmLiveAi,
            recent_conversation: [],
          }),
        }),
      );
      const headers = fetchMock.mock.calls[0]?.[1]?.headers as Headers;
      expect(headers.get("Accept")).toBe("application/json");
      expect(headers.get("Content-Type")).toBe("application/json");
    },
  );

  it("checks replay availability with a read-only GET", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ available: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await getIncidentLabReplayAvailability();

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/incident-lab/recorded-replay",
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
    expect(fetchMock.mock.calls[0]?.[1]).not.toHaveProperty("method");
  });

  it("starts recorded playback with one explicit bodyless POST", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ mode: "recorded_replay" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    await runIncidentLabReplay();

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/incident-lab/runs/recorded-replay",
      expect.objectContaining({ method: "POST", headers: expect.any(Headers) }),
    );
    expect(fetchMock.mock.calls[0]?.[1]).not.toHaveProperty("body");
  });
});
