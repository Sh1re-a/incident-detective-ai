import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import {
  getScenarios,
  runLiveInvestigation,
  runRecordedReplay,
  toPublicErrorMessage,
} from "./api/client";
import type {
  Evidence,
  InvestigationResult,
  RunMode,
  Scenario,
} from "./api/types";
import { EngineeringView } from "./components/EngineeringView";
import { EvidenceDrawer } from "./components/EvidenceDrawer";
import { LiveConfirmDialog } from "./components/LiveConfirmDialog";
import { ScenarioPicker } from "./components/ScenarioPicker";
import { StoryView, type RunPhase } from "./components/StoryView";

type AppView = "story" | "engineering";

export function App() {
  const [view, setView] = useState<AppView>("story");
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [selectedScenarioId, setSelectedScenarioId] = useState("");
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [catalogLoading, setCatalogLoading] = useState(true);
  const [catalogReload, setCatalogReload] = useState(0);
  const [phase, setPhase] = useState<RunPhase>("idle");
  const [pendingMode, setPendingMode] = useState<RunMode | null>(null);
  const [result, setResult] = useState<InvestigationResult | null>(null);
  const [visibleToolEventCount, setVisibleToolEventCount] = useState(0);
  const [runError, setRunError] = useState<string | null>(null);
  const [liveDialogOpen, setLiveDialogOpen] = useState(false);
  const [selectedEvidenceId, setSelectedEvidenceId] = useState<string | null>(null);
  const activeRunRef = useRef<AbortController | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const reduceMotion = usePrefersReducedMotion();

  useEffect(() => {
    const controller = new AbortController();
    setCatalogLoading(true);
    setCatalogError(null);

    void getScenarios(controller.signal)
      .then(({ scenarios: loadedScenarios }) => {
        setScenarios(loadedScenarios);
        setSelectedScenarioId((current) => {
          if (loadedScenarios.some((scenario) => scenario.scenario_id === current)) {
            return current;
          }
          return loadedScenarios[0]?.scenario_id ?? "";
        });
      })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === "AbortError")) {
          setCatalogError(toPublicErrorMessage(error));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setCatalogLoading(false);
        }
      });

    return () => controller.abort();
  }, [catalogReload]);

  useEffect(() => {
    return () => activeRunRef.current?.abort();
  }, []);

  useEffect(() => {
    if (phase !== "revealing" || !result) {
      return;
    }

    if (visibleToolEventCount >= result.tool_events.length) {
      setPhase("success");
      return;
    }

    const delay = visibleToolEventCount === 0 ? 260 : 720;
    const timeout = window.setTimeout(() => {
      setVisibleToolEventCount((count) => count + 1);
    }, delay);
    return () => window.clearTimeout(timeout);
  }, [phase, result, visibleToolEventCount]);

  const selectedScenario = useMemo(
    () =>
      scenarios.find((scenario) => scenario.scenario_id === selectedScenarioId) ??
      null,
    [scenarios, selectedScenarioId],
  );

  const evidenceById = useMemo(() => {
    const evidence = new Map<string, Evidence>();
    result?.tool_events.forEach((event) => {
      event.evidence.forEach((item) => evidence.set(item.evidence_id, item));
    });
    return evidence;
  }, [result]);

  const selectedEvidence = selectedEvidenceId
    ? evidenceById.get(selectedEvidenceId) ?? null
    : null;

  const resetRun = useCallback(() => {
    activeRunRef.current?.abort();
    activeRunRef.current = null;
    setResult(null);
    setPhase("idle");
    setPendingMode(null);
    setVisibleToolEventCount(0);
    setRunError(null);
    setSelectedEvidenceId(null);
  }, []);

  const selectScenario = useCallback(
    (scenarioId: string) => {
      resetRun();
      setSelectedScenarioId(scenarioId);
    },
    [resetRun],
  );

  const runReplay = useCallback(async () => {
    if (!selectedScenarioId) {
      return;
    }

    activeRunRef.current?.abort();
    const controller = new AbortController();
    activeRunRef.current = controller;
    setLiveDialogOpen(false);
    setResult(null);
    setRunError(null);
    setPendingMode("recorded_replay");
    setVisibleToolEventCount(0);
    setPhase("running");

    try {
      const replay = await runRecordedReplay(selectedScenarioId, controller.signal);
      if (controller.signal.aborted) {
        return;
      }
      setResult(replay);
      if (reduceMotion || replay.tool_events.length === 0) {
        setVisibleToolEventCount(replay.tool_events.length);
        setPhase("success");
      } else {
        setPhase("revealing");
      }
    } catch (error: unknown) {
      if (!controller.signal.aborted) {
        setRunError(toPublicErrorMessage(error));
        setPhase("error");
      }
    } finally {
      if (activeRunRef.current === controller) {
        activeRunRef.current = null;
      }
    }
  }, [reduceMotion, selectedScenarioId]);

  const runLive = useCallback(async () => {
    if (!selectedScenarioId) {
      return;
    }

    activeRunRef.current?.abort();
    const controller = new AbortController();
    activeRunRef.current = controller;
    setLiveDialogOpen(false);
    setResult(null);
    setRunError(null);
    setPendingMode("live_ai");
    setVisibleToolEventCount(0);
    setPhase("running");

    try {
      const liveResult = await runLiveInvestigation(
        selectedScenarioId,
        controller.signal,
      );
      if (controller.signal.aborted) {
        return;
      }
      setResult(liveResult);
      setVisibleToolEventCount(liveResult.tool_events.length);
      setPhase("success");
    } catch (error: unknown) {
      if (!controller.signal.aborted) {
        setRunError(toPublicErrorMessage(error));
        setPhase("error");
      }
    } finally {
      if (activeRunRef.current === controller) {
        activeRunRef.current = null;
      }
    }
  }, [selectedScenarioId]);

  const requestLive = useCallback(() => {
    returnFocusRef.current = document.activeElement as HTMLElement | null;
    setLiveDialogOpen(true);
  }, []);

  const closeLiveDialog = useCallback(() => {
    setLiveDialogOpen(false);
    window.requestAnimationFrame(() => returnFocusRef.current?.focus());
  }, []);

  const openEvidence = useCallback((evidenceId: string) => {
    returnFocusRef.current = document.activeElement as HTMLElement | null;
    setSelectedEvidenceId(evidenceId);
  }, []);

  const closeEvidence = useCallback(() => {
    setSelectedEvidenceId(null);
    window.requestAnimationFrame(() => returnFocusRef.current?.focus());
  }, []);

  const skipReplay = useCallback(() => {
    if (!result) {
      return;
    }
    setVisibleToolEventCount(result.tool_events.length);
    setPhase("success");
  }, [result]);

  return (
    <div className="site-shell" id="top">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="Incident Detective home">
          <span className="brand-mark" aria-hidden="true">
            <i />
          </span>
          <span>
            <strong>Incident Detective</strong>
            <small>Applied AI case lab</small>
          </span>
        </a>

        <nav className="view-tabs" aria-label="Demo view" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={view === "story"}
            className={view === "story" ? "active" : ""}
            onClick={() => setView("story")}
          >
            Story View
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === "engineering"}
            className={view === "engineering" ? "active" : ""}
            onClick={() => setView("engineering")}
          >
            Engineering View
          </button>
        </nav>

        <a
          className="source-link"
          href="https://github.com/Sh1re-a/incident-detective-ai"
          target="_blank"
          rel="noreferrer"
        >
          Source <span aria-hidden="true">↗</span>
        </a>
      </header>

      <main>
        <section className="hero">
          <div className="hero-copy">
            <p className="eyebrow">Simulated incident · inspectable proof</p>
            <h1>
              When checkout breaks,
              <span> follow the evidence.</span>
            </h1>
            <p className="hero-intro">
              An AI investigator uses bounded read-only tools, cites what it saw
              and submits its diagnosis to a deterministic verifier.
            </p>
          </div>
          <div className="hero-proof" aria-label="Project principles">
            <span><i aria-hidden="true">01</i>Synthetic data</span>
            <span><i aria-hidden="true">02</i>Real tool calling</span>
            <span><i aria-hidden="true">03</i>Human approval</span>
          </div>
        </section>

        {catalogLoading ? (
          <section className="catalog-state panel" role="status">
            <span className="status-spinner" aria-hidden="true" />
            <h2>Opening synthetic case files…</h2>
          </section>
        ) : catalogError ? (
          <section className="catalog-state panel" role="alert">
            <span className="catalog-error-mark" aria-hidden="true">!</span>
            <h2>The local API is not ready</h2>
            <p>{catalogError}</p>
            <button
              className="button primary-button"
              type="button"
              onClick={() => setCatalogReload((value) => value + 1)}
            >
              Try again
            </button>
          </section>
        ) : selectedScenario ? (
          <>
            <ScenarioPicker
              scenarios={scenarios}
              selectedId={selectedScenarioId}
              disabled={phase === "running" || phase === "revealing"}
              onSelect={selectScenario}
            />

            <div
              className="view-content"
              role="tabpanel"
              aria-label={view === "story" ? "Story View" : "Engineering View"}
            >
              {view === "story" ? (
                <StoryView
                  scenario={selectedScenario}
                  phase={phase}
                  pendingMode={pendingMode}
                  result={result}
                  visibleToolEventCount={visibleToolEventCount}
                  errorMessage={runError}
                  onRunReplay={() => void runReplay()}
                  onRequestLive={requestLive}
                  onSkipReplay={skipReplay}
                  onOpenEvidence={openEvidence}
                />
              ) : (
                <EngineeringView
                  result={result}
                  onOpenEvidence={openEvidence}
                  onSwitchToStory={() => setView("story")}
                />
              )}
            </div>
          </>
        ) : (
          <section className="catalog-state panel" role="status">
            <h2>No synthetic scenarios are available.</h2>
          </section>
        )}
      </main>

      <footer className="site-footer">
        <div>
          <strong>Incident Detective</strong>
          <p>Simulated incidents. Real engineering decisions.</p>
        </div>
        <p>
          No real company logs · No automatic remediation · Built for a four-week
          SALT passion project
        </p>
      </footer>

      <LiveConfirmDialog
        open={liveDialogOpen}
        onCancel={closeLiveDialog}
        onConfirm={() => void runLive()}
      />
      <EvidenceDrawer evidence={selectedEvidence} onClose={closeEvidence} />
    </div>
  );
}

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    if (typeof window.matchMedia !== "function") {
      return;
    }

    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  return reduced;
}
