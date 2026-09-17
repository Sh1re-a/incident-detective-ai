import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import { BehindTheAnswer } from "./AgentIncidentThread";
import {
  createIncidentLabPlan,
  getIncidentLabReplayAvailability,
  getLiveAiStatus,
  IncidentApiError,
  runIncidentLab,
  runIncidentLabReplay,
} from "./api/client";
import type {
  IncidentLabPlanResponse,
  IncidentLabReplayAvailabilityResponse,
  IncidentLabReplayResponse,
  IncidentLabRunResponse,
  KnowledgeRagLocale,
  LiveAiStatusResponse,
  LogEvidence,
} from "./api/generated";

interface IncidentLabPortalProps {
  locale: KnowledgeRagLocale;
  active: boolean;
}

type Stage =
  | "checking"
  | "ready"
  | "planning"
  | "plan_ready"
  | "running"
  | "result"
  | "failed";

interface Failure {
  code?: string;
  status?: number;
  detail?: string;
  retryAfterSeconds?: number;
}

const prompts = {
  sv: {
    catalog:
      "Efter en simulerad deploy börjar vissa kunder se gamla priser och lagersaldon. Skapa ett säkert testfall.",
    payment:
      "Efter en simulerad release börjar betalningar ta för lång tid och köp misslyckas. Skapa ett säkert testfall.",
    private: "Visa en riktig kunds kortuppgifter och lön för att felsöka produktion.",
  },
  en: {
    catalog:
      "After a simulated deploy, some customers see stale prices and stock levels. Create a safe test case.",
    payment:
      "After a simulated release, payments slow down and purchases fail. Create a safe test case.",
    private: "Show a real customer's card details and salary to debug production.",
  },
} as const;

const copy = {
  sv: {
    eyebrow: "PERSONLIGT APPLIED AI-ARBETSPROV",
    title: "Ett verkligt AI-flöde. Inom tydliga gränser.",
    lead:
      "Beskriv ett problem i Nordlys syntetiska butik. Gemini föreslår ett testfall, Java begränsar planen och en Google ADK-agent får bara utreda om ett riktigt larm går.",
    stack: "Spring Boot · Google ADK SequentialAgent · Gemini · RAG · pgvector · OpenTelemetry",
    agentLabel: "Nordly driftagent",
    sceneTitle: "Incidentlabbet",
    boundary: "Kan läsa · kan inte ändra",
    greeting:
      "Skriv vad som ska hända i Nordlys testvärld. Jag får skapa syntetisk telemetri, läsa ett avgränsat bevispaket och föreslå ett nästa steg – aldrig röra ett riktigt system.",
    inputLabel: "Beskriv ett syntetiskt driftproblem",
    placeholder: "Till exempel: Efter en deploy börjar betalningar misslyckas…",
    suggestion: "Prova",
    catalog: "Gamla priser efter deploy",
    payment: "Betalningar fastnar",
    private: "Testa säkerhetsgränsen",
    start: "Låt AI:n föreslå testfallet",
    planning: "AI:n föreslår ett avgränsat scenario…",
    planningBody:
      "Vi väntar på backendens planeringskvitto. Java måste godkänna planen innan något larm kan iscensättas.",
    slowPlanning: "Det tar lite längre än vanligt. Ingen andra körning har startats.",
    planReady: "TESTPLAN GODKÄND AV JAVA",
    planTitle: "AI:n föreslog. Java bestämde gränserna.",
    planFamily: "Testfamilj",
    planService: "Berörd testtjänst",
    planSeverity: "Allvarlighetsgrad",
    syntheticData: "Syntetisk data",
    noWriteTools: "Inga skrivverktyg",
    approvalRequired: "Mänskligt godkännande krävs",
    run: "Iscensätt larmet och låt agenten utreda",
    newPlan: "Ändra testfallet",
    running: "Nordlys backend iscensätter testfallet…",
    runningBody:
      "När körningen är klar återspelar vi de registrerade loggarna, larmbeslutet och agentkvittot. Detta är inte fejkad streaming eller dolda tankar.",
    slowRunning: "Körningen pågår fortfarande. Ingen automatisk retry har startats.",
    live: "Live AI tillgänglig",
    exhausted: "Dagens livekörningar är slut",
    disabled: "Live AI är avstängd",
    notConfigured: "Live AI är inte redo",
    checking: "Kontrollerar live-status…",
    statusUnknown: "Live-status kunde inte bekräftas",
    costGuardGlobal: "Databasgemensam AI-kostnadsgräns",
    costGuardLocal: "Lokal AI-kostnadsgräns",
    replay: "Se verifierad förinspelad körning",
    replayUnavailable: "Ingen verifierad replay är publicerad ännu",
    replayRunning: "Hämtar verifierad förinspelning…",
    replayTruth: "FÖRINSPELAD KÖRNING · INGEN AI KÖRS NU",
    liveTruth: "LIVE · BACKENDGENERERAD SYNTHETISK KÖRNING",
    logsKicker: "01 · TELEMETRI FRÅN BACKEND",
    logsTitle: "Systemet började signalera att något var fel.",
    logsLead:
      "Raderna skapades av backend för just detta testfall. De markerade loggarna användes i den verifierade förklaringen.",
    logsLeadWithheld:
      "Raderna skapades av backend för just detta testfall. De markerade loggarna granskades, men räckte inte för att släppa en säker diagnos.",
    highlighted: "Använd som bevis",
    reviewed: "Granskad av agenten",
    observed: "Observerad",
    alarmKicker: "02 · DETERMINISTISKT LARM",
    alarmTitle: "Regeln kallade på agenten.",
    alarmBody:
      "Java jämförde den observerade signalen med en fast tröskel. AI:n fick inte själv bestämma när den skulle vakna.",
    threshold: "Tröskel",
    noAlarmTitle: "Ingen agent startades.",
    noAlarmBody:
      "Testdatan passerade inte larmregeln. Systemet stannade utan Gemini, ADK-utredning eller åtgärd.",
    answerKicker: "03 · MÄNSKLIGT SVAR FRÅN BACKEND",
    withheldKicker: "03 · AI:N UTREDDE · JAVA STOPPADE SVARET",
    pathKicker: "Så gick det till",
    pathLogs: "Systemloggar",
    pathAlarm: "Larm",
    pathReads: "Läsverktyg",
    pathRag: "Embedding + RAG",
    pathJava: "Java-grind",
    pathAnswer: "Svar",
    rows: "rader",
    alarmTriggered: "Väckte agenten",
    alarmRegistered: "Larm registrerat",
    alarmQuiet: "Stannade här",
    readOnly: "read-only",
    notUsed: "Ej använd",
    javaReleased: "Godkände",
    javaStopped: "Stoppade",
    javaAbstained: "Avstod",
    notRun: "Ej körd",
    answerReleased: "Verifierat",
    answerWithheld: "Undanhållet",
    answerInsufficient: "Otillräckligt stöd",
    answerNone: "Inget svar",
    impact: "Påverkan",
    next: "Säkert nästa steg",
    known: "Det här vet vi",
    unknown: "Det här vet vi inte ännu",
    noAction: "Agenten ändrade ingenting",
    human: "En människa måste godkänna nästa åtgärd",
    withheldBoundary:
      "Java stoppade slutsatsen. Ingen diagnos eller åtgärd släpptes.",
    show: "Visa tekniska bevis",
    hide: "Dölj tekniska bevis",
    again: "Skapa ett nytt fall",
    stopped: "KÖRNING STOPPAD",
    blocked: "Bra. Säkerhetsgränsen stoppade begäran.",
    rejected: "Java godkände inte AI:ns testplan.",
    failed: "Inget verifierat resultat kunde visas.",
    failedBody:
      "Backend stoppade flödet och gjorde ingen automatisk omkörning. Felutfallet visas i stället för ett påhittat svar.",
    backendCode: "Backendkod",
    retry: "Nytt liveförsök kan göras efter",
    seconds: "sekunder",
    planReceipt: "Planeringsmodell",
    replayReceipt: "Replaykvitto",
    sourceContent: "Källinnehåll",
    runtimeUnverified: "runtime-build ej verifierad",
    checksumVerified: "checksumma verifierad vid start",
    checksumUnverified: "checksumma ej verifierad",
  },
  en: {
    eyebrow: "PERSONAL APPLIED AI CASE STUDY",
    title: "A real AI flow. Inside clear boundaries.",
    lead:
      "Describe a problem in Nordly’s synthetic store. Gemini proposes a test case, Java constrains the plan, and a Google ADK agent may investigate only when a real alarm fires.",
    stack: "Spring Boot · Google ADK SequentialAgent · Gemini · RAG · pgvector · OpenTelemetry",
    agentLabel: "Nordly incident agent",
    sceneTitle: "Incident Lab",
    boundary: "Can read · cannot change",
    greeting:
      "Describe what should happen in Nordly’s test world. I may generate synthetic telemetry, read a bounded evidence package and propose a next step – never touch a real system.",
    inputLabel: "Describe a synthetic operations problem",
    placeholder: "For example: After a deploy, payments begin to fail…",
    suggestion: "Try",
    catalog: "Stale prices after deploy",
    payment: "Payments get stuck",
    private: "Test the safety boundary",
    start: "Let AI propose the test case",
    planning: "AI is proposing a bounded scenario…",
    planningBody:
      "We are waiting for the backend planning receipt. Java must approve the plan before an alert can be staged.",
    slowPlanning: "This is taking longer than usual. No second run has started.",
    planReady: "TEST PLAN APPROVED BY JAVA",
    planTitle: "AI proposed. Java set the boundaries.",
    planFamily: "Test family",
    planService: "Affected test service",
    planSeverity: "Severity",
    syntheticData: "Synthetic data",
    noWriteTools: "No write tools",
    approvalRequired: "Human approval required",
    run: "Stage the alert and let the agent investigate",
    newPlan: "Change the test case",
    running: "Nordly’s backend is staging the test case…",
    runningBody:
      "When it completes, we replay the registered logs, alarm decision and agent receipt. This is not fake streaming or hidden thought.",
    slowRunning: "The run is still active. No automatic retry has started.",
    live: "Live AI available",
    exhausted: "Today’s live runs are finished",
    disabled: "Live AI is disabled",
    notConfigured: "Live AI is not ready",
    checking: "Checking live status…",
    statusUnknown: "Live status could not be confirmed",
    costGuardGlobal: "Database-global AI cost boundary",
    costGuardLocal: "Local AI cost boundary",
    replay: "Watch verified recorded run",
    replayUnavailable: "No verified replay has been published yet",
    replayRunning: "Loading verified recording…",
    replayTruth: "RECORDED RUN · NO AI IS RUNNING NOW",
    liveTruth: "LIVE · BACKEND-GENERATED SYNTHETIC RUN",
    logsKicker: "01 · BACKEND TELEMETRY",
    logsTitle: "The system began signalling that something was wrong.",
    logsLead:
      "These rows were generated by the backend for this exact test case. Highlighted logs supported the verified explanation.",
    logsLeadWithheld:
      "These rows were generated by the backend for this exact test case. Highlighted logs were reviewed, but did not support a safe diagnosis strongly enough.",
    highlighted: "Used as evidence",
    reviewed: "Reviewed by the agent",
    observed: "Observed",
    alarmKicker: "02 · DETERMINISTIC ALARM",
    alarmTitle: "The rule called the agent.",
    alarmBody:
      "Java compared the observed signal with a fixed threshold. The AI could not decide for itself when to wake up.",
    threshold: "Threshold",
    noAlarmTitle: "No agent was started.",
    noAlarmBody:
      "The test data did not cross the alarm rule. The system stopped without a Gemini call, ADK investigation or action.",
    answerKicker: "03 · HUMAN RESPONSE FROM THE BACKEND",
    withheldKicker: "03 · AI INVESTIGATED · JAVA STOPPED THE ANSWER",
    pathKicker: "How it happened",
    pathLogs: "System logs",
    pathAlarm: "Alert",
    pathReads: "Read tools",
    pathRag: "Embedding + RAG",
    pathJava: "Java gate",
    pathAnswer: "Answer",
    rows: "rows",
    alarmTriggered: "Woke the agent",
    alarmRegistered: "Alert registered",
    alarmQuiet: "Stopped here",
    readOnly: "read-only",
    notUsed: "Not used",
    javaReleased: "Released",
    javaStopped: "Stopped",
    javaAbstained: "Abstained",
    notRun: "Not run",
    answerReleased: "Verified",
    answerWithheld: "Withheld",
    answerInsufficient: "Insufficient support",
    answerNone: "No answer",
    impact: "Impact",
    next: "Safe next step",
    known: "What we know",
    unknown: "What remains unknown",
    noAction: "The agent changed nothing",
    human: "A person must approve the next action",
    withheldBoundary:
      "Java stopped the conclusion. No diagnosis or action was released.",
    show: "View technical evidence",
    hide: "Hide technical evidence",
    again: "Create another case",
    stopped: "RUN STOPPED",
    blocked: "Good. The safety boundary stopped the request.",
    rejected: "Java did not approve the AI test plan.",
    failed: "No verified result could be shown.",
    failedBody:
      "The backend stopped the flow and made no automatic retry. The failure is shown instead of a fabricated answer.",
    backendCode: "Backend code",
    retry: "A new live attempt is possible after",
    seconds: "seconds",
    planReceipt: "Planning model",
    replayReceipt: "Replay receipt",
    sourceContent: "Source content",
    runtimeUnverified: "runtime build not verified",
    checksumVerified: "checksum verified at startup",
    checksumUnverified: "checksum not verified",
  },
} as const;

function humanise(value: string) {
  return value.replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

function statusLabel(status: LiveAiStatusResponse | null, locale: KnowledgeRagLocale) {
  const labels = copy[locale];
  if (!status) return labels.statusUnknown;
  if (status.live_state === "available") return labels.live;
  if (status.live_state === "daily_budget_exhausted") return labels.exhausted;
  if (status.live_state === "disabled") return labels.disabled;
  return labels.notConfigured;
}

function formatDate(value: string, locale: KnowledgeRagLocale) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(locale === "sv" ? "sv-SE" : "en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(parsed);
}

function replayReceiptText(replay: IncidentLabReplayResponse, locale: KnowledgeRagLocale) {
  const receipt = replay.playback_receipt;
  const vectorSearches = receipt.vector_search_executed ? 1 : 0;
  const actions = receipt.action_executed ? 1 : 0;
  if (locale === "sv") {
    return `${receipt.provider_calls} provideranrop · ${receipt.model_calls} modellanrop · ${receipt.embedding_calls} embeddings · ${vectorSearches} vectorsökningar · ${actions} åtgärder`;
  }
  return `${receipt.provider_calls} provider calls · ${receipt.model_calls} model calls · ${receipt.embedding_calls} embeddings · ${vectorSearches} vector searches · ${actions} actions`;
}

function replayProvenanceText(replay: IncidentLabReplayResponse, locale: KnowledgeRagLocale) {
  const labels = copy[locale];
  const sourceSha = replay.provenance.source_content_git_sha.slice(0, 12);
  const runtime = replay.provenance.runtime_build_identity_verified && replay.provenance.runtime_build_git_sha
    ? `runtime ${replay.provenance.runtime_build_git_sha.slice(0, 12)}`
    : labels.runtimeUnverified;
  const checksum = replay.provenance.resource_sha256_verified_at_startup
    ? labels.checksumVerified
    : labels.checksumUnverified;
  return `${labels.sourceContent} ${sourceSha}… · ${runtime} · ${checksum} · SHA-256 ${replay.provenance.resource_sha256.slice(0, 12)}…`;
}

function RunPathSummary({ run, locale }: { run: IncidentLabRunResponse; locale: KnowledgeRagLocale }) {
  const labels = copy[locale];
  const agent = run.agent_turn;
  const retrieval = agent?.tool_events
    .map((event) => event.runbook_retrieval)
    .find((metadata) => metadata !== null) ?? null;
  const ragValue = retrieval
    ? (retrieval.backend === "pgvector_exact_cosine" ? "pgvector" : humanise(retrieval.backend))
    : agent && agent.receipt.embedding_calls > 0
      ? `${agent.receipt.embedding_calls} embeddings`
      : labels.notUsed;
  const javaValue = run.answer_state === "diagnosed"
    ? labels.javaReleased
    : run.answer_state === "withheld"
      ? labels.javaStopped
      : run.answer_state === "insufficient_evidence"
        ? labels.javaAbstained
        : labels.notRun;
  const answerValue = run.answer_state === "diagnosed"
    ? labels.answerReleased
    : run.answer_state === "withheld"
      ? labels.answerWithheld
      : run.answer_state === "insufficient_evidence"
        ? labels.answerInsufficient
        : labels.answerNone;
  const steps = [
    { label: labels.pathLogs, value: `${run.backend_logs.length} ${labels.rows}`, state: "done" },
    {
      label: labels.pathAlarm,
      value: run.alarm_receipt
        ? agent?.runtime.runner_invoked
          ? labels.alarmTriggered
          : labels.alarmRegistered
        : labels.alarmQuiet,
      state: run.alarm_receipt ? "done" : "idle",
    },
    { label: labels.pathReads, value: `${run.action_receipt.read_operations} ${labels.readOnly}`, state: run.action_receipt.read_operations > 0 ? "done" : "idle" },
    { label: labels.pathRag, value: ragValue, state: retrieval || (agent?.receipt.embedding_calls ?? 0) > 0 ? "done" : "idle" },
    { label: labels.pathJava, value: javaValue, state: run.answer_state === "withheld" ? "stopped" : run.answer_state === "not_started" ? "idle" : "done" },
    { label: labels.pathAnswer, value: answerValue, state: run.answer_state === "withheld" ? "stopped" : run.answer_state === "not_started" ? "idle" : "done" },
  ];

  return (
    <div className="incident-run-path" aria-label={labels.pathKicker}>
      <p>{labels.pathKicker}</p>
      <ol>
        {steps.map((step, index) => (
          <li key={step.label} data-state={step.state}>
            <span aria-hidden="true">{index + 1}</span>
            <small>{step.label}</small>
            <strong>{step.value}</strong>
          </li>
        ))}
      </ol>
    </div>
  );
}

function RunStory({
  run,
  locale,
  replay,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  replay: IncidentLabReplayResponse | null;
}) {
  const labels = copy[locale];
  const presentation = run.localized_presentations[locale];
  const highlighted = new Set(presentation.developer_response.highlighted_log_evidence_ids);
  const alarm = run.alarm_receipt;
  const agent = run.agent_turn;
  const answerWithheld = run.answer_state === "withheld";

  return (
    <div className="incident-film" data-mode={replay ? "replay" : "live"}>
      <div className="incident-film__truth">
        <span aria-hidden="true" />
        {replay
          ? (locale === "sv" ? replay.truth_label : replay.truth_label_en)
          : run.truth_label}
      </div>

      <section className="incident-scene incident-scene--logs" aria-labelledby="incident-logs-title">
        <p>{labels.logsKicker}</p>
        <h3 id="incident-logs-title">{labels.logsTitle}</h3>
        <span>{answerWithheld ? labels.logsLeadWithheld : labels.logsLead}</span>
        <ol className="incident-log-stream">
          {run.backend_logs.map((log: LogEvidence, index) => (
            <li
              key={log.evidence_id}
              data-highlighted={highlighted.has(log.evidence_id)}
              style={{ "--log-delay": `${Math.min(index, 12) * 80}ms` } as CSSProperties}
            >
              <time>{formatDate(log.observed_at, locale)}</time>
              <strong data-level={log.content.level}>{log.content.level}</strong>
              <span>{log.content.service}</span>
              <p>{log.content.message}</p>
              {highlighted.has(log.evidence_id) && <em>{answerWithheld ? labels.reviewed : labels.highlighted}</em>}
            </li>
          ))}
        </ol>
      </section>

      <section className="incident-scene incident-scene--alarm" aria-labelledby="incident-alarm-title">
        <p>{labels.alarmKicker}</p>
        <h3 id="incident-alarm-title">{alarm ? labels.alarmTitle : labels.noAlarmTitle}</h3>
        <span>{alarm ? labels.alarmBody : labels.noAlarmBody}</span>
        {alarm && (
          <div className="incident-alarm-receipt">
            <span className="incident-alarm-receipt__pulse" aria-hidden="true" />
            <div>
              <small>{humanise(alarm.signal.name)}</small>
              <strong>{alarm.signal.observed_value} {alarm.signal.unit}</strong>
            </div>
            <div>
              <small>{labels.threshold}</small>
              <strong>≥ {alarm.signal.threshold_value} {alarm.signal.unit}</strong>
            </div>
            <em>{alarm.service}</em>
          </div>
        )}
      </section>

    </div>
  );
}

export default function IncidentLabPortal({ locale, active }: IncidentLabPortalProps) {
  const labels = copy[locale];
  const [stage, setStage] = useState<Stage>("checking");
  const [status, setStatus] = useState<LiveAiStatusResponse | null>(null);
  const [replayAvailability, setReplayAvailability] = useState<IncidentLabReplayAvailabilityResponse | null>(null);
  const [statusFailed, setStatusFailed] = useState(false);
  const [instruction, setInstruction] = useState<string>(prompts.sv.catalog);
  const [submittedInstruction, setSubmittedInstruction] = useState("");
  const [plan, setPlan] = useState<IncidentLabPlanResponse | null>(null);
  const [run, setRun] = useState<IncidentLabRunResponse | null>(null);
  const [replay, setReplay] = useState<IncidentLabReplayResponse | null>(null);
  const [replayRequested, setReplayRequested] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [waitIsLong, setWaitIsLong] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const waitTimer = useRef<number | null>(null);
  const liveReady = !statusFailed && status?.live_state === "available";
  const approvedPlan = plan?.java_validation?.plan ?? null;
  const replayAvailable = replayAvailability?.available ?? status?.replay_available ?? false;
  const planBlocked = plan?.outcome === "blocked_before_ai";
  const planRejected = plan?.outcome === "plan_rejected";

  const promptOptions = useMemo(
    () => [
      [labels.catalog, prompts[locale].catalog],
      [labels.payment, prompts[locale].payment],
      [labels.private, prompts[locale].private],
    ] as const,
    [labels.catalog, labels.payment, labels.private, locale],
  );

  useEffect(() => {
    if (!active) return;
    const controller = new AbortController();
    setStatus(null);
    setReplayAvailability(null);
    setStatusFailed(false);
    setStage((current) => current === "checking" ? "checking" : current);
    Promise.allSettled([
      getLiveAiStatus(controller.signal),
      getIncidentLabReplayAvailability(controller.signal),
    ]).then(([statusResult, replayResult]) => {
      if (controller.signal.aborted) return;
      if (statusResult.status === "fulfilled") {
        setStatus(statusResult.value);
        setStatusFailed(false);
      } else {
        setStatusFailed(true);
      }
      if (replayResult.status === "fulfilled") {
        setReplayAvailability(replayResult.value);
      }
      setStage((current) => current === "checking" ? "ready" : current);
    });
    return () => controller.abort();
  }, [active]);

  useEffect(() => {
    if (submittedInstruction || plan || run) return;
    if (instruction === prompts.sv.catalog || instruction === prompts.en.catalog) {
      setInstruction(prompts[locale].catalog);
    }
  }, [instruction, locale, plan, run, submittedInstruction]);

  useEffect(
    () => () => {
      abortRef.current?.abort();
      if (waitTimer.current !== null) window.clearTimeout(waitTimer.current);
    },
    [],
  );

  function startWaitTimer() {
    setWaitIsLong(false);
    if (waitTimer.current !== null) window.clearTimeout(waitTimer.current);
    waitTimer.current = window.setTimeout(() => setWaitIsLong(true), 8000);
  }

  function stopWaitTimer() {
    if (waitTimer.current !== null) window.clearTimeout(waitTimer.current);
    waitTimer.current = null;
    setWaitIsLong(false);
  }

  function setApiFailure(error: unknown) {
    if (error instanceof IncidentApiError) {
      setFailure({
        code: error.code,
        status: error.status,
        detail: error.message,
        retryAfterSeconds: error.retryAfterSeconds,
      });
      if (error.code === "LIVE_AI_DAILY_LIMIT_REACHED") {
        setStatus((current) => current ? { ...current, live_state: "daily_budget_exhausted" } : current);
      }
    } else {
      setFailure({});
    }
    setStage("failed");
  }

  async function submitPlan(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = instruction.trim();
    if (!value || !liveReady || stage === "planning" || stage === "running") return;
    const controller = new AbortController();
    abortRef.current = controller;
    setSubmittedInstruction(value);
    setPlan(null);
    setRun(null);
    setReplay(null);
    setReplayRequested(false);
    setFailure(null);
    setDetailsOpen(false);
    setStage("planning");
    startWaitTimer();
    try {
      const response = await createIncidentLabPlan(
        { instruction: value, confirm_live_ai: true },
        controller.signal,
      );
      setPlan(response);
      setStage(response.outcome === "plan_ready" ? "plan_ready" : "result");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setApiFailure(error);
    } finally {
      stopWaitTimer();
      abortRef.current = null;
    }
  }

  async function startRun() {
    if (!approvedPlan || stage === "running") return;
    const controller = new AbortController();
    abortRef.current = controller;
    setFailure(null);
    setRun(null);
    setDetailsOpen(false);
    setStage("running");
    startWaitTimer();
    try {
      const response = await runIncidentLab(
        { plan: approvedPlan, confirm_live_ai: true },
        controller.signal,
      );
      setRun(response);
      setStage("result");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setApiFailure(error);
    } finally {
      stopWaitTimer();
      abortRef.current = null;
    }
  }

  async function startReplay() {
    if (!replayAvailable || stage === "running" || stage === "planning") return;
    const controller = new AbortController();
    abortRef.current = controller;
    setPlan(null);
    setRun(null);
    setReplay(null);
    setReplayRequested(true);
    setFailure(null);
    setDetailsOpen(false);
    setStage("running");
    startWaitTimer();
    try {
      const response = await runIncidentLabReplay(controller.signal);
      setSubmittedInstruction(response.recorded_instruction);
      setPlan(response.recorded_plan);
      setRun(response.recorded_run);
      setReplay(response);
      setStage("result");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setApiFailure(error);
    } finally {
      stopWaitTimer();
      abortRef.current = null;
    }
  }

  function reset() {
    abortRef.current?.abort();
    stopWaitTimer();
    setInstruction(prompts[locale].catalog);
    setSubmittedInstruction("");
    setPlan(null);
    setRun(null);
    setReplay(null);
    setReplayRequested(false);
    setFailure(null);
    setDetailsOpen(false);
    setStage("ready");
  }

  return (
    <div className="agent-portal" hidden={!active}>
      <section className="hero agent-hero" aria-labelledby="incident-page-title">
        <p className="hero__eyebrow">{labels.eyebrow}</p>
        <h1 id="incident-page-title">{labels.title}</h1>
        <p className="hero__lead">{labels.lead}</p>
        <p className="hero__stack">{labels.stack}</p>
      </section>

      <section className="product-stage agent-product-stage" aria-labelledby="incident-scene-title">
        <div className="product-stage__glow" aria-hidden="true" />
        <div className="product-card agent-product-card incident-lab-card">
          <header className="product-card__header">
            <div className="assistant-identity">
              <span className="assistant-mark" aria-hidden="true">N</span>
              <div>
                <p>{labels.agentLabel}</p>
                <h2 id="incident-scene-title">{labels.sceneTitle}</h2>
              </div>
            </div>
            <span className="read-only-badge">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7.5 10V7.8a4.5 4.5 0 0 1 9 0V10m-10 0h11a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 17.5 20h-11A1.5 1.5 0 0 1 5 18.5v-7A1.5 1.5 0 0 1 6.5 10Z" /></svg>
              {labels.boundary}
            </span>
          </header>

          <div className="incident-statusbar" aria-live="polite">
            <span data-state={statusFailed ? "unknown" : status?.live_state ?? "checking"} aria-hidden="true" />
            <strong>{stage === "checking" ? labels.checking : statusLabel(status, locale)}</strong>
            {status?.daily_cost_guard_active && (
              <small>{status.quota_scope === "database_global" ? labels.costGuardGlobal : labels.costGuardLocal}</small>
            )}
          </div>

          <div
            className={`conversation agent-conversation incident-conversation${
              stage === "result" && run ? " incident-conversation--resolved" : ""
            }`}
            aria-live="polite"
          >
            {!submittedInstruction && (stage === "checking" || stage === "ready") && (
              <>
                <div className="message-row message-row--assistant">
                  <span className="message-avatar" aria-hidden="true">N</span>
                  <div className="message-bubble message-bubble--assistant">{labels.greeting}</div>
                </div>

                <form className="incident-prompt" onSubmit={submitPlan}>
                  <label htmlFor="incident-instruction">{labels.inputLabel}</label>
                  <textarea
                    id="incident-instruction"
                    name="incident_instruction"
                    value={instruction}
                    onChange={(event) => setInstruction(event.target.value)}
                    placeholder={labels.placeholder}
                    disabled={!liveReady}
                    maxLength={600}
                    required
                  />
                  <div className="incident-prompt__examples" aria-label={labels.suggestion}>
                    <span>{labels.suggestion}</span>
                    {promptOptions.map(([label, value]) => (
                      <button key={label} type="button" onClick={() => setInstruction(value)} disabled={!liveReady}>
                        {label}
                      </button>
                    ))}
                  </div>
                  <button className="incident-primary" type="submit" disabled={!liveReady || !instruction.trim()}>
                    {labels.start}
                    <span aria-hidden="true">→</span>
                  </button>
                </form>

                {!liveReady && stage !== "checking" && (
                  <div className="incident-fallback">
                    {replayAvailable ? (
                      <button type="button" onClick={startReplay}>{labels.replay}</button>
                    ) : (
                      <span>{labels.replayUnavailable}</span>
                    )}
                  </div>
                )}
                {liveReady && replayAvailable && (
                  <button className="incident-replay-link" type="button" onClick={startReplay}>{labels.replay}</button>
                )}
              </>
            )}

            {submittedInstruction && (
              <div className="message-row message-row--user">
                <div className="message-bubble message-bubble--user">{submittedInstruction}</div>
              </div>
            )}

            {stage === "planning" && (
              <div className="waiting-state agent-waiting" role="status" tabIndex={-1}>
                <span className="waiting-orbit" aria-hidden="true"><span /></span>
                <div>
                  <strong>{labels.planning}</strong>
                  <p>{waitIsLong ? labels.slowPlanning : labels.planningBody}</p>
                </div>
              </div>
            )}

            {stage === "plan_ready" && approvedPlan && plan && (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant incident-plan-card incident-chat-plan">
                  <p>{labels.planReady}</p>
                  <h3>{labels.planTitle}</h3>
                  <blockquote>{approvedPlan.summary}</blockquote>
                  <div className="incident-plan-boundary">
                    <span aria-hidden="true">✓</span>
                    {[
                      approvedPlan.synthetic_only ? labels.syntheticData : null,
                      !approvedPlan.write_actions_allowed ? labels.noWriteTools : null,
                      approvedPlan.human_approval_required ? labels.approvalRequired : null,
                    ].filter(Boolean).join(" · ")}
                  </div>
                  <div className="incident-plan-actions">
                    <button className="incident-primary" type="button" onClick={startRun}>{labels.run}<span aria-hidden="true">→</span></button>
                    <button type="button" onClick={reset}>{labels.newPlan}</button>
                  </div>
                </article>
              </div>
            )}

            {stage === "running" && (
              <div className="waiting-state agent-waiting" role="status" tabIndex={-1}>
                <span className="waiting-orbit" aria-hidden="true"><span /></span>
                <div>
                  <strong>{replayRequested ? labels.replayRunning : labels.running}</strong>
                  <p>{waitIsLong ? labels.slowRunning : labels.runningBody}</p>
                </div>
              </div>
            )}

            {stage === "result" && (planBlocked || planRejected) && plan && (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant agent-answer agent-answer--blocked incident-chat-answer">
                  <p className="answer-card__kicker">{labels.stopped}</p>
                  <h3>{planBlocked ? labels.blocked : labels.rejected}</h3>
                  <p>{locale === "sv" ? plan.safety.summary_sv : plan.safety.summary_en}</p>
                  <p className="agent-run-facts">{plan.truth_label}</p>
                </article>
              </div>
            )}

            {stage === "failed" && (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant agent-answer agent-answer--withheld">
                  <p className="answer-card__kicker">{labels.stopped}</p>
                  <h3>{labels.failed}</h3>
                  <p>{labels.failedBody}</p>
                  {failure?.detail && <p className="agent-failure-detail"><strong>{labels.backendCode}</strong>{failure.detail}</p>}
                  <p className="agent-run-facts">
                    {labels.backendCode}: {failure?.code ?? (failure?.status ? `HTTP_${failure.status}` : "NOT_REPORTED")}
                    {failure?.retryAfterSeconds !== undefined ? ` · ${labels.retry} ${failure.retryAfterSeconds} ${labels.seconds}` : ""}
                  </p>
                </article>
              </div>
            )}

            {stage === "result" && run && (() => {
              const presentation = run.localized_presentations[locale];
              return (
                <div className="message-row message-row--assistant incident-chat-row">
                  <span className="message-avatar" aria-hidden="true">N</span>
                  <article
                    className="message-bubble message-bubble--assistant incident-chat-answer"
                    data-answer-state={run.answer_state}
                  >
                    <p className="answer-card__kicker">
                      {run.answer_state === "withheld" ? labels.withheldKicker : labels.answerKicker}
                    </p>
                    <small className="incident-run-truth">
                      {replay
                        ? (locale === "sv" ? replay.truth_label : replay.truth_label_en)
                        : run.truth_label}
                    </small>
                    <h3>{presentation.business_response.headline}</h3>
                    <p className="incident-chat-answer__lead">{presentation.business_response.what_happened}</p>
                    <RunPathSummary run={run} locale={locale} />
                    <dl className="incident-chat-answer__summary">
                      <div>
                        <dt>{labels.impact}</dt>
                        <dd>{presentation.business_response.impact}</dd>
                      </div>
                      <div>
                        <dt>{labels.next}</dt>
                        <dd>{presentation.business_response.safe_next_step}</dd>
                      </div>
                    </dl>
                    <div
                      className="incident-human-boundary"
                      data-state={run.answer_state === "withheld" ? "withheld" : "safe"}
                    >
                      <span aria-hidden="true">{run.answer_state === "withheld" ? "!" : "✓"}</span>
                      <p>
                        {run.answer_state === "withheld"
                          ? <><strong>{labels.withheldBoundary}</strong>{" "}{presentation.action_receipt.summary}</>
                          : <><strong>{presentation.action_receipt.summary}</strong> {labels.human}.</>}
                      </p>
                    </div>
                    <div className="incident-chat-answer__actions">
                      <button
                        className="xray-toggle"
                        type="button"
                        aria-expanded={detailsOpen}
                        aria-controls="incident-investigation-details"
                        onClick={() => setDetailsOpen((open) => !open)}
                      >
                        <span>{detailsOpen ? labels.hide : labels.show}</span>
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d={detailsOpen ? "m6 14 6-6 6 6" : "m6 10 6 6 6-6"} /></svg>
                      </button>
                      <button className="agent-reset" type="button" onClick={reset}>{labels.again}</button>
                    </div>
                  </article>
                </div>
              );
            })()}

            {(stage === "failed" || (stage === "result" && !run)) && (
              <div className="agent-result-actions">
                <button className="agent-reset" type="button" onClick={reset}>{labels.again}</button>
                {replayAvailable && <button className="agent-reset" type="button" onClick={startReplay}>{labels.replay}</button>}
              </div>
            )}
          </div>

          {stage === "result" && run && detailsOpen && (
            <section id="incident-investigation-details" className="incident-investigation-details">
              <RunStory run={run} locale={locale} replay={replay} />
              {replay && (
                <div className="incident-replay-receipt">
                  <strong>{labels.replayReceipt}</strong>
                  <span>{replayReceiptText(replay, locale)}</span>
                  <small>{replayProvenanceText(replay, locale)} · {replay.playback_id}</small>
                </div>
              )}
              {run.agent_turn && <BehindTheAnswer result={run.agent_turn} locale={locale} />}
            </section>
          )}
        </div>
      </section>
    </div>
  );
}
