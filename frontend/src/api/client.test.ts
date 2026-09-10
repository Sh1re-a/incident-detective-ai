import { afterEach, describe, expect, it, vi } from "vitest";

import {
  getCapabilities,
  runAgentTurn,
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
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    await expect(getCapabilities()).rejects.toMatchObject({
      name: "IncidentApiError",
      message: "The public live investigation limit has been reached.",
      status: 429,
      code: "LIVE_AI_RATE_LIMITED",
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
});
