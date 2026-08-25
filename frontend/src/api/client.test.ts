import { describe, expect, it } from "vitest";

import { ApiError, toPublicErrorMessage } from "./client";

describe("public API errors", () => {
  it("explains the live admission limit without exposing internals", () => {
    const message = toPublicErrorMessage(
      new ApiError({
        title: "Live AI is busy",
        status: 429,
        detail: "The public live investigation limit has been reached.",
        code: "LIVE_AI_RATE_LIMITED",
      }),
    );

    expect(message).toBe(
      "Live AI is busy or has reached its public demo limit. Wait a moment or use the recorded investigation.",
    );
  });
});
