import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { App } from "./App";
import type { ApiProblem, LiveInvestigationResult } from "./api/types";
import {
  liveResult,
  recordedResult,
  scenario,
  secondScenario,
} from "./test/fixtures";

describe("Incident Detective experience", () => {
  it("keeps a recorded replay truthfully labelled and shows its proof", async () => {
    installApiMock();
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("heading", { name: "Start the investigation" });
    await user.click(
      screen.getByRole("button", { name: "Play free recorded investigation" }),
    );

    expect(
      await screen.findByText(
        "Simulated incident — recorded deterministic replay.",
        { exact: true },
      ),
    ).toBeVisible();
    expect(
      screen.queryByText("Simulated incident — real AI investigation.", {
        exact: true,
      }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Payment Timeout Config")).toBeVisible();
    expect(screen.getByText("Matched")).toBeVisible();
  });

  it("opens returned evidence and closes it with Escape", async () => {
    installApiMock();
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      await screen.findByRole("button", {
        name: "Play free recorded investigation",
      }),
    );
    await screen.findByText("Simulated incident — recorded deterministic replay.", {
      exact: true,
    });
    const evidenceSummary = await screen.findByText(
      "Checkout failure ratio reached 18.4 percent.",
    );
    const evidenceButton = evidenceSummary.closest("button");
    expect(evidenceButton).not.toBeNull();
    await user.click(evidenceButton!);

    const drawer = screen.getByRole("dialog", { name: "Evidence detail" });
    expect(within(drawer).getByText("0.184")).toBeVisible();
    expect(within(drawer).getByText(metricEvidenceId())).toBeVisible();

    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "Evidence detail" })).not.toBeInTheDocument();
  });

  it("shows replay metadata as no model call", async () => {
    installApiMock();
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      await screen.findByRole("button", {
        name: "Play free recorded investigation",
      }),
    );
    await screen.findByText("Simulated incident — recorded deterministic replay.", {
      exact: true,
    });
    await user.click(screen.getByRole("tab", { name: "Engineering View" }));

    expect(screen.getAllByText("No model called").length).toBeGreaterThanOrEqual(3);
    expect(screen.getByText("100% this run")).toBeVisible();
    expect(
      screen.getByText(/They are not an eval-set accuracy claim/),
    ).toBeVisible();
  });

  it("requires confirmation before showing a successful live result", async () => {
    const fetchMock = installApiMock();
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      await screen.findByRole("button", { name: "Run live AI" }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Run a live AI investigation?",
    });
    expect(within(dialog).getByText(/may use a small amount of API credit/)).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Confirm live run" }));

    expect(
      await screen.findByText("Simulated incident — real AI investigation.", {
        exact: true,
      }),
    ).toBeVisible();

    const liveCall = fetchMock.mock.calls.find(([url]) =>
      String(url).endsWith("/runs/live-ai"),
    );
    expect(liveCall?.[1]).toMatchObject({
      method: "POST",
      body: JSON.stringify({ confirm_live_ai: true }),
    });

    await user.click(screen.getByRole("tab", { name: "Engineering View" }));
    expect(screen.getByText("gemini-3.5-flash-lite")).toBeVisible();
    expect(screen.getByText("$0.0032")).toBeVisible();
    expect(screen.getByText(/estimate, not a provider invoice/)).toBeVisible();
  });

  it("keeps a live timeout visible until the visitor chooses replay", async () => {
    const timeoutProblem: ApiProblem = {
      title: "Model provider timed out",
      status: 504,
      detail: "Gemini did not respond within the bounded timeout.",
      code: "MODEL_PROVIDER_TIMEOUT",
    };
    const fetchMock = installApiMock({ liveProblem: timeoutProblem });
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      await screen.findByRole("button", { name: "Run live AI" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Confirm live run" }),
    );

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("reached its time limit");
    expect(callsEndingWith(fetchMock, "/runs/recorded-replay")).toHaveLength(0);

    await user.click(
      within(alert).getByRole("button", {
        name: "Try the recorded investigation",
      }),
    );
    expect(
      await screen.findByText(
        "Simulated incident — recorded deterministic replay.",
        { exact: true },
      ),
    ).toBeVisible();
    expect(callsEndingWith(fetchMock, "/runs/recorded-replay")).toHaveLength(1);
  });

  it("presents a rejected live answer as rejected", async () => {
    const rejectedResult: LiveInvestigationResult = {
      ...liveResult,
      status: "verification_failed",
      diagnosis: {
        ...liveResult.diagnosis,
        root_cause_code: "WRONG_ROOT_CAUSE",
      },
      comparison: {
        ...liveResult.comparison,
        root_cause_correct: false,
      },
      verification: {
        ...liveResult.verification,
        diagnosis_correctness: {
          ...liveResult.verification.diagnosis_correctness,
          root_cause_correct: false,
        },
      },
    };
    installApiMock({ liveResponse: rejectedResult });
    const user = userEvent.setup();
    render(<App />);

    await user.click(
      await screen.findByRole("button", { name: "Run live AI" }),
    );
    await user.click(
      screen.getByRole("button", { name: "Confirm live run" }),
    );

    expect(await screen.findByText("Rejected by verifier")).toBeVisible();
    expect(screen.getByText("Did not match")).toBeVisible();
  });
});

interface ApiMockOptions {
  liveProblem?: ApiProblem;
  liveResponse?: LiveInvestigationResult;
}

function installApiMock(options: ApiMockOptions = {}) {
  const fetchMock = vi.fn(async (
    input: RequestInfo | URL,
    _init?: RequestInit,
  ) => {
    const url = String(input);
    if (url === "/api/v1/scenarios") {
      return jsonResponse({ scenarios: [scenario, secondScenario] });
    }
    if (url.endsWith("/runs/recorded-replay")) {
      return jsonResponse(recordedResult);
    }
    if (url.endsWith("/runs/live-ai")) {
      if (options.liveProblem) {
        return jsonResponse(options.liveProblem, options.liveProblem.status);
      }
      return jsonResponse(options.liveResponse ?? liveResult);
    }
    return jsonResponse({ title: "Not found", detail: "Not found" }, 404);
  });

  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function callsEndingWith(
  fetchMock: ReturnType<typeof vi.fn>,
  suffix: string,
) {
  return fetchMock.mock.calls.filter(([url]) => String(url).endsWith(suffix));
}

function metricEvidenceId(): string {
  return "cpt-v1-metric-checkout-failure-rate";
}
