import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { BehindTheAnswer } from "./AgentIncidentThread";
import IncidentFollowUpChat from "./IncidentFollowUpChat";
import {
  createIncidentLabPlan,
  getCapabilities,
  getIncidentLabReplayAvailability,
  getLiveAiStatus,
  IncidentApiError,
  runIncidentLab,
  runIncidentLabReplay,
} from "./api/client";
import type {
  CapabilitiesResponse,
  Evidence,
  GeneratedIncidentFamily,
  IncidentLabPlanResponse,
  IncidentLabFollowUpCitation,
  IncidentLabReplayAvailabilityResponse,
  IncidentLabReplayResponse,
  IncidentLabRunResponse,
  KnowledgeRagLocale,
  LiveAiStatusResponse,
  LogEvidence,
  RunbookEvidence,
} from "./api/generated";
import { INCIDENT_LAB_PLAN_INSTRUCTION_MAX_LENGTH } from "./api/generated";

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
    eyebrow: "NORDLY · KONTROLLERAD AI I DRIFT",
    title: "När Nordly larmar får AI:n undersöka – aldrig ändra.",
    lead:
      "Se ett syntetiskt fel uppstå, en fast Java-regel väcka driftagenten och ett källbundet svar komma tillbaka.",
    stack: "Spring Boot · Google ADK · Gemini · RAG + pgvector · OpenTelemetry",
    agentLabel: "Nordly driftagent",
    sceneTitle: "Larmcentralen",
    boundary: "Läser bevis · ändrar inget",
    greeting:
      "Starta ett säkert fel i Nordlys testbutik. När backend är klar spelar jag upp de registrerade händelserna så att du kan följa hela utredningen.",
    inputLabel: "Vad ska hända i testbutiken?",
    placeholder: "Till exempel: Efter en deploy börjar betalningar misslyckas…",
    suggestion: "Prova ett fall",
    catalog: "Gamla priser efter deploy",
    payment: "Betalningar fastnar",
    private: "Testa säkerhetsgränsen",
    start: "Skapa ett säkert testfall",
    planning: "AI:n föreslår ett avgränsat testfall…",
    planningBody: "Java måste godkänna omfattningen innan ett larm får iscensättas.",
    slowPlanning: "Det tar lite längre än vanligt. Ingen andra körning har startats.",
    planReady: "TESTPLAN GODKÄND AV JAVA",
    planTitle: "Testfallet är avgränsat och redo.",
    planIntro: "Nordly kommer att iscensätta ett syntetiskt fel i",
    severity: "Allvarlighetsgrad",
    syntheticData: "Endast syntetisk data",
    noWriteTools: "Inga skrivverktyg",
    approvalRequired: "Människa godkänner nästa åtgärd",
    run: "Starta larmet",
    newPlan: "Ändra testfallet",
    running: "Nordlys backend kör utredningen…",
    runningBody:
      "När svaret kommer spelar vi upp registrerade loggar, larmbeslut, agentverktyg och Java-kontroller. Det här är inte livestreaming av modellens tankar.",
    slowRunning: "Körningen pågår fortfarande. Ingen automatisk omkörning har startats.",
    live: "Live AI redo",
    exhausted: "Dagens livekörningar är slut",
    disabled: "Live AI är avstängd",
    notConfigured: "Live AI är inte redo",
    checking: "Kontrollerar live-status…",
    statusUnknown: "Live-status kunde inte bekräftas",
    costGuardGlobal: "Gemensam kostnadsgräns aktiv",
    costGuardLocal: "Lokal kostnadsgräns aktiv",
    replay: "Se verifierad förinspelad körning",
    replayUnavailable: "Ingen verifierad repris finns ännu",
    replayRunning: "Hämtar verifierad förinspelning…",
    playbackKicker: "KÖRNINGEN ÄR KLAR",
    playbackTitle: "Följ vad som hände – i rätt ordning.",
    playbackLive:
      "Återspelning av registrerade backendhändelser · AI-körningen är redan avslutad",
    playbackRecorded: "Verifierad repris · ingen AI körs nu",
    sceneNames: ["Loggar", "Larm", "Agent + RAG", "Java", "Svar"],
    sceneLogsKicker: "01 · SYSTEMET SIGNALERAR",
    sceneLogsTitle: "Loggarna visar att något har förändrats.",
    sceneLogsLead:
      "Detta är backendens verkliga rader från testkörningen. Markerade rader följde med som bevis.",
    usedAsEvidence: "Använd som bevis",
    plainLanguage: "Enkelt förklarat",
    noLogs: "Backend returnerade inga loggrader för det här utfallet.",
    showAllLogs: "Visa alla registrerade loggar",
    showKeyLogs: "Visa bara utredningens nyckelloggar",
    sceneAlarmKicker: "02 · EN FAST REGEL REAGERAR",
    sceneAlarmTitle: "Larmregeln väckte agenten – inte AI:n själv.",
    sceneAlarmLead:
      "Java jämförde ett uppmätt värde med en förbestämd gräns. Först när regeln slog till fick ADK-agenten starta.",
    observed: "Observerat",
    threshold: "Larmgräns",
    window: "Mätfönster",
    seconds: "sekunder",
    rule: "Regel",
    noAlarmTitle: "Signalen passerade inte larmgränsen.",
    noAlarmLead:
      "Systemet stannade utan agent, Gemini, RAG eller åtgärd. Det är ett giltigt kontrollerat utfall.",
    sceneAgentKicker: "03 · AGENTEN UNDERSÖKER",
    sceneAgentTitle: "Agenten fick läsa ett avgränsat bevispaket.",
    sceneAgentLead:
      "ADK körde stegen i fast ordning. Verktygen kunde läsa, men inte skriva eller ändra något.",
    order: "Fast agentordning",
    readOperations: "Läsningar",
    toolCalls: "Verktygsanrop",
    embeddings: "Embeddings",
    actions: "Åtgärder",
    ragTitle: "RAG hittade relevant driftkunskap",
    ragBody:
      "Frågan blev en embedding. pgvector jämförde betydelsen med godkända dokument och returnerade de närmaste utdragen.",
    model: "Embeddingmodell",
    vectorDb: "Vectordatabas",
    matches: "Källträffar",
    source: "Källa",
    semanticMatch: "Semantisk träff",
    similarityNote: "Likheten rangordnar text – den är inte AI:ns säkerhet eller diagnosens sannolikhet.",
    noRag: "Inget RAG-anrop registrerades i körningen.",
    noAgent: "Larmet startade ingen agent i det här utfallet.",
    sceneJavaKicker: "04 · JAVA KONTROLLERAR",
    sceneJavaTitle: "Svaret måste passera kontrollgrinden.",
    sceneJavaLead:
      "Java granskar format, källhänvisningar, bevisstöd, agentordning och verktygsgräns innan något visas som verifierat.",
    responseFormat: "Tillåtet svarformat",
    citations: "Käll-ID:n finns",
    evidenceSupport: "Källorna stödjer påståendena",
    agentOrder: "Agentordningen följdes",
    toolBoundary: "Endast tillåtna verktyg",
    passed: "Godkänd",
    stopped: "Stoppad",
    skipped: "Ej körd",
    released: "Java släppte svaret",
    withheld: "Java höll tillbaka slutsatsen",
    verifiedClaims: "verifierade påståenden i svaret",
    noVerification: "Ingen AI-slutsats behövde verifieras i det här utfallet.",
    sceneAnswerKicker: "05 · MÄNSKLIGT SVAR",
    safeWithheldTitle: "Säkerhetskontrollen stoppade en osäker slutsats.",
    analysis: "Analys",
    location: "Var finns problemet?",
    cause: "Varför uppstod det?",
    signalFrom: "Larmsignalen kom från",
    causeUnavailable: "Källorna räcker inte för att fastställa en rotorsak.",
    impact: "Påverkan",
    known: "Vad källorna visar",
    unknown: "Vad som inte går att fastställa",
    nothingUnknown: "Inga ytterligare kunskapsluckor registrerades i testfallet.",
    recommendation: "Rekommendation · ingen åtgärd utförd",
    noAction: "Agenten ändrade ingenting",
    human: "En människa måste godkänna nästa åtgärd",
    explore: "Öppna källorna bakom rapporten",
    askLogs: "Visa avgörande loggar",
    askRag: "Visa RAG-källor",
    askBoundary: "Visa Java-kontrollen",
    technical: "Öppna tekniskt kvitto",
    hideTechnical: "Dölj tekniskt kvitto",
    followUpUnavailable: "Körningen saknar ett giltigt följdfrågekvitto. Rapporten är läsbar, men chatten öppnas inte.",
    previous: "Föregående",
    nextScene: "Nästa",
    again: "Skapa ett nytt fall",
    scene: "Scen",
    blocked: "Bra. Säkerhetsgränsen stoppade begäran.",
    rejected: "Java godkände inte AI:ns testplan.",
    failed: "Inget verifierat resultat kunde visas.",
    failedBody:
      "Backend stoppade flödet och gjorde ingen automatisk omkörning. Vi visar felet i stället för ett påhittat svar.",
    backendCode: "Backendkod",
    retry: "Nytt liveförsök kan göras efter",
    replayReceipt: "Replaykvitto",
    sourceContent: "Källinnehåll",
    runtimeUnverified: "runtime-build ej verifierad",
    checksumVerified: "checksumma verifierad vid start",
    checksumUnverified: "checksumma ej verifierad",
  },
  en: {
    eyebrow: "NORDLY · CONTROLLED AI OPERATIONS",
    title: "When Nordly alerts, AI may investigate – never change.",
    lead:
      "Watch a synthetic fault appear, a fixed Java rule wake the incident agent, and a source-grounded answer return.",
    stack: "Spring Boot · Google ADK · Gemini · RAG + pgvector · OpenTelemetry",
    agentLabel: "Nordly incident agent",
    sceneTitle: "Alert Centre",
    boundary: "Reads evidence · changes nothing",
    greeting:
      "Start a safe fault in Nordly’s test store. When the backend finishes, I replay the registered events so you can follow the complete investigation.",
    inputLabel: "What should happen in the test store?",
    placeholder: "For example: After a deploy, payments begin to fail…",
    suggestion: "Try a case",
    catalog: "Stale prices after deploy",
    payment: "Payments get stuck",
    private: "Test the safety boundary",
    start: "Create a safe test case",
    planning: "AI is proposing a bounded test case…",
    planningBody: "Java must approve the scope before an alert may be staged.",
    slowPlanning: "This is taking longer than usual. No second run has started.",
    planReady: "TEST PLAN APPROVED BY JAVA",
    planTitle: "The test case is bounded and ready.",
    planIntro: "Nordly will stage a synthetic fault in",
    severity: "Severity",
    syntheticData: "Synthetic data only",
    noWriteTools: "No write tools",
    approvalRequired: "A person approves the next action",
    run: "Start the alert",
    newPlan: "Change the test case",
    running: "Nordly’s backend is running the investigation…",
    runningBody:
      "When the response returns, we replay registered logs, the alert decision, agent tools and Java checks. This is not live streaming of model thoughts.",
    slowRunning: "The run is still active. No automatic rerun has started.",
    live: "Live AI ready",
    exhausted: "Today’s live runs are finished",
    disabled: "Live AI is disabled",
    notConfigured: "Live AI is not ready",
    checking: "Checking live status…",
    statusUnknown: "Live status could not be confirmed",
    costGuardGlobal: "Shared cost boundary active",
    costGuardLocal: "Local cost boundary active",
    replay: "Watch verified recorded run",
    replayUnavailable: "No verified replay is available yet",
    replayRunning: "Loading verified recording…",
    playbackKicker: "THE RUN IS COMPLETE",
    playbackTitle: "Follow what happened – in the right order.",
    playbackLive: "Replay of registered backend events · the AI run has already finished",
    playbackRecorded: "Verified recording · no AI is running now",
    sceneNames: ["Logs", "Alert", "Agent + RAG", "Java", "Answer"],
    sceneLogsKicker: "01 · THE SYSTEM SIGNALS",
    sceneLogsTitle: "The logs show that something changed.",
    sceneLogsLead:
      "These are the backend’s actual rows from the test run. Highlighted rows travelled with the answer as evidence.",
    usedAsEvidence: "Used as evidence",
    plainLanguage: "In plain language",
    noLogs: "The backend returned no log rows for this outcome.",
    showAllLogs: "Show all registered logs",
    showKeyLogs: "Show only the investigation’s key logs",
    sceneAlarmKicker: "02 · A FIXED RULE REACTS",
    sceneAlarmTitle: "The alert rule woke the agent – not the AI itself.",
    sceneAlarmLead:
      "Java compared a measured value with a predefined boundary. Only after the rule fired could the ADK agent start.",
    observed: "Observed",
    threshold: "Alert boundary",
    window: "Observation window",
    seconds: "seconds",
    rule: "Rule",
    noAlarmTitle: "The signal did not cross the alert boundary.",
    noAlarmLead:
      "The system stopped without an agent, Gemini, RAG or action. That is a valid controlled outcome.",
    sceneAgentKicker: "03 · THE AGENT INVESTIGATES",
    sceneAgentTitle: "The agent could read a bounded evidence package.",
    sceneAgentLead:
      "ADK ran each step in fixed order. The tools could read, but never write or change anything.",
    order: "Fixed agent order",
    readOperations: "Reads",
    toolCalls: "Tool calls",
    embeddings: "Embeddings",
    actions: "Actions",
    ragTitle: "RAG found relevant operational knowledge",
    ragBody:
      "The question became an embedding. pgvector compared its meaning with approved documents and returned the nearest passages.",
    model: "Embedding model",
    vectorDb: "Vector database",
    matches: "Source matches",
    source: "Source",
    semanticMatch: "Semantic match",
    similarityNote: "Similarity is used for ranking – not as certainty or AI confidence.",
    noRag: "No RAG call was registered in this run.",
    noAgent: "The alert did not start an agent in this outcome.",
    sceneJavaKicker: "04 · JAVA CHECKS",
    sceneJavaTitle: "The answer must pass the control gate.",
    sceneJavaLead:
      "Java checks format, citations, evidence support, agent order and the tool boundary before anything is presented as verified.",
    responseFormat: "Allowed response format",
    citations: "Citation IDs exist",
    evidenceSupport: "Sources support the claims",
    agentOrder: "Agent order was followed",
    toolBoundary: "Only allowed tools",
    passed: "Passed",
    stopped: "Stopped",
    skipped: "Not run",
    released: "Java released the answer",
    withheld: "Java withheld the conclusion",
    verifiedClaims: "verified claims in the answer",
    noVerification: "No AI conclusion needed verification in this outcome.",
    sceneAnswerKicker: "05 · HUMAN ANSWER",
    safeWithheldTitle: "The safety check stopped an uncertain conclusion.",
    analysis: "Analysis",
    location: "Where is the problem?",
    cause: "Why did it happen?",
    signalFrom: "The alert signal came from",
    causeUnavailable: "The sources are not sufficient to establish a root cause.",
    impact: "Impact",
    known: "What the sources show",
    unknown: "What cannot be established",
    nothingUnknown: "No additional knowledge gaps were registered in the test case.",
    recommendation: "Recommendation · no action executed",
    noAction: "The agent changed nothing",
    human: "A person must approve the next action",
    explore: "Open the sources behind the report",
    askLogs: "Show decisive logs",
    askRag: "Show RAG sources",
    askBoundary: "Show the Java verification",
    technical: "Open technical receipt",
    hideTechnical: "Hide technical receipt",
    followUpUnavailable: "This run has no valid follow-up receipt. The report remains readable, but chat is not opened.",
    previous: "Previous",
    nextScene: "Next",
    again: "Create another case",
    scene: "Scene",
    blocked: "Good. The safety boundary stopped the request.",
    rejected: "Java did not approve the AI test plan.",
    failed: "No verified result could be shown.",
    failedBody:
      "The backend stopped the flow and made no automatic rerun. We show the failure instead of a fabricated answer.",
    backendCode: "Backend code",
    retry: "A new live attempt is possible after",
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

function localeCode(locale: KnowledgeRagLocale) {
  return locale === "sv" ? "sv-SE" : "en-US";
}

function formatDate(value: string, locale: KnowledgeRagLocale) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(localeCode(locale), {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(parsed);
}

function formatPercent(value: number | null, locale: KnowledgeRagLocale) {
  if (value === null) return "–";
  return new Intl.NumberFormat(localeCode(locale), {
    style: "percent",
    maximumFractionDigits: 1,
  }).format(value);
}

function formatSignalValue(value: number, unit: string, locale: KnowledgeRagLocale) {
  const number = new Intl.NumberFormat(localeCode(locale), { maximumFractionDigits: 2 }).format(value);
  const localizedUnits: Record<KnowledgeRagLocale, Record<string, string>> = {
    sv: { count: "händelser", versions: "versioner", milliseconds: "ms", percent: "%" },
    en: { count: "events", versions: "versions", milliseconds: "ms", percent: "%" },
  };
  return `${number} ${localizedUnits[locale][unit.toLowerCase()] ?? unit}`;
}

function statusLabel(status: LiveAiStatusResponse | null, locale: KnowledgeRagLocale) {
  const labels = copy[locale];
  if (!status) return labels.statusUnknown;
  if (status.live_state === "available") return labels.live;
  if (status.live_state === "daily_budget_exhausted") return labels.exhausted;
  if (status.live_state === "disabled") return labels.disabled;
  return labels.notConfigured;
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

function familyLabel(
  capabilities: CapabilitiesResponse | null,
  family: GeneratedIncidentFamily,
  locale: KnowledgeRagLocale,
) {
  return capabilities?.incident_lab.incident_families.find((item) => item.id === family)?.label[locale]
    ?? humanise(family);
}

function workflowStepLabel(name: string, locale: KnowledgeRagLocale) {
  const normalized = name.toLowerCase();
  if (normalized.includes("evidence")) return locale === "sv" ? "Samlar och lämnar bevis" : "Collects and hands off evidence";
  if (normalized.includes("diagnosis")) return locale === "sv" ? "Formulerar källbunden slutsats" : "Builds a source-grounded conclusion";
  if (normalized.includes("verif")) return locale === "sv" ? "Verifierar slutsatsen" : "Verifies the conclusion";
  return humanise(name);
}

function agentEvidence(run: IncidentLabRunResponse) {
  return run.agent_turn?.tool_events.flatMap((event) => event.evidence) ?? [];
}

function evidenceByType<T extends Evidence["evidence_type"]>(
  evidence: Evidence[],
  type: T,
) {
  return evidence.filter((item): item is Extract<Evidence, { evidence_type: T }> => item.evidence_type === type);
}

function PlaybackLogs({
  run,
  locale,
  focusedEvidenceId,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  focusedEvidenceId: string | null;
}) {
  const labels = copy[locale];
  const [showAll, setShowAll] = useState(false);
  const presentation = run.localized_presentations[locale];
  const highlighted = new Set(presentation.developer_response.highlighted_log_evidence_ids);
  const compactIds = new Set([
    ...run.backend_logs.filter((log) => !highlighted.has(log.evidence_id)).slice(0, 1).map((log) => log.evidence_id),
    ...run.backend_logs.filter((log) => highlighted.has(log.evidence_id)).slice(0, 3).map((log) => log.evidence_id),
  ]);
  const compactLogs = run.backend_logs.filter((log) => compactIds.has(log.evidence_id));
  const visibleLogs = showAll || run.backend_logs.length <= 4 ? run.backend_logs : compactLogs;

  useEffect(() => {
    if (focusedEvidenceId && run.backend_logs.some((log) => log.evidence_id === focusedEvidenceId)) {
      setShowAll(true);
    }
  }, [focusedEvidenceId, run.backend_logs]);

  return (
    <div className="incident-playback-scene incident-playback-scene--logs" data-incident-scene="logs">
      <p className="incident-playback-scene__kicker">{labels.sceneLogsKicker}</p>
      <h3>{labels.sceneLogsTitle}</h3>
      <p className="incident-playback-scene__lead">{labels.sceneLogsLead}</p>
      {run.backend_logs.length > 0 ? (
        <ol className="incident-playback-logs">
          {visibleLogs.map((log: LogEvidence) => (
            <li
              key={log.evidence_id}
              data-highlighted={highlighted.has(log.evidence_id)}
              data-incident-source={log.evidence_id}
              data-citation-focus={focusedEvidenceId === log.evidence_id}
            >
              <header>
                <time>{formatDate(log.observed_at, locale)}</time>
                <strong data-level={log.content.level}>{log.content.level}</strong>
                <span translate="no">{log.content.service}</span>
                {highlighted.has(log.evidence_id) ? <em>{labels.usedAsEvidence}</em> : null}
              </header>
              <code>{log.content.message}</code>
              {log.display_summary && log.display_summary !== log.content.message ? (
                <p><span>{labels.plainLanguage}</span>{log.display_summary}</p>
              ) : null}
            </li>
          ))}
        </ol>
      ) : (
        <div className="incident-playback-empty">{labels.noLogs}</div>
      )}
      {run.backend_logs.length > 4 ? (
        <button className="incident-log-toggle" type="button" onClick={() => setShowAll((current) => !current)}>
          {showAll ? labels.showKeyLogs : `${labels.showAllLogs} (${run.backend_logs.length})`}
        </button>
      ) : null}
    </div>
  );
}

function PlaybackAlarm({
  run,
  locale,
  capabilities,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  capabilities: CapabilitiesResponse | null;
}) {
  const labels = copy[locale];
  const alarm = run.alarm_receipt;

  if (!alarm) {
    return (
      <div className="incident-playback-scene incident-playback-scene--alarm">
        <p className="incident-playback-scene__kicker">{labels.sceneAlarmKicker}</p>
        <h3>{labels.noAlarmTitle}</h3>
        <p className="incident-playback-scene__lead">{labels.noAlarmLead}</p>
        <div className="incident-playback-empty" data-tone="safe">
          <span aria-hidden="true">✓</span>
          <strong>0 Gemini · 0 ADK · 0 RAG · 0 actions</strong>
        </div>
      </div>
    );
  }

  const signalConcept = capabilities?.incident_lab.incident_families
    .find((family) => family.id === alarm.incident_family)
    ?.alarm_signal_concept[locale] ?? humanise(alarm.signal.name);

  return (
    <div className="incident-playback-scene incident-playback-scene--alarm">
      <p className="incident-playback-scene__kicker">{labels.sceneAlarmKicker}</p>
      <h3>{labels.sceneAlarmTitle}</h3>
      <p className="incident-playback-scene__lead">{labels.sceneAlarmLead}</p>
      <div className="incident-playback-alert">
        <span className="incident-playback-alert__pulse" aria-hidden="true" />
        <div>
          <small>{signalConcept}</small>
          <strong>{labels.observed}: {formatSignalValue(alarm.signal.observed_value, alarm.signal.unit, locale)}</strong>
        </div>
        <dl>
          <div>
            <dt>{labels.threshold}</dt>
            <dd>≥ {formatSignalValue(alarm.signal.threshold_value, alarm.signal.unit, locale)}</dd>
          </div>
          <div>
            <dt>{labels.window}</dt>
            <dd>{alarm.signal.lookback_seconds === null ? "–" : `${alarm.signal.lookback_seconds} ${labels.seconds}`}</dd>
          </div>
        </dl>
        <p><span>{labels.rule}</span><code translate="no">{alarm.rule_id}</code></p>
      </div>
    </div>
  );
}

function PlaybackAgent({
  run,
  locale,
  capabilities,
  focusedEvidenceId,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  capabilities: CapabilitiesResponse | null;
  focusedEvidenceId: string | null;
}) {
  const labels = copy[locale];
  const agent = run.agent_turn;

  if (!agent) {
    return (
      <div className="incident-playback-scene">
        <p className="incident-playback-scene__kicker">{labels.sceneAgentKicker}</p>
        <h3>{labels.noAgent}</h3>
        <p className="incident-playback-scene__lead">{labels.noAlarmLead}</p>
      </div>
    );
  }

  const evidence = agentEvidence(run);
  const runbooks = evidenceByType(evidence, "runbook");
  const retrieval = agent.tool_events
    .map((event) => event.runbook_retrieval)
    .find((metadata) => metadata !== null) ?? null;
  const order = agent.workflow?.observed_agent_order
    ?? agent.workflow?.expected_agent_order
    ?? capabilities?.incident_lab.expected_agent_order
    ?? [];

  return (
    <div className="incident-playback-scene incident-playback-scene--agent" data-incident-scene="agent_rag">
      <p className="incident-playback-scene__kicker">{labels.sceneAgentKicker}</p>
      <h3>{labels.sceneAgentTitle}</h3>
      <p className="incident-playback-scene__lead">{labels.sceneAgentLead}</p>

      <div className="incident-agent-order">
        <small>{labels.order}</small>
        <ol>
          {order.map((name, index) => (
            <li key={`${name}-${index}`}>
              <span aria-hidden="true">{index + 1}</span>
              <strong>{workflowStepLabel(name, locale)}</strong>
            </li>
          ))}
        </ol>
      </div>

      <dl className="incident-agent-receipt">
        <div><dt>{labels.readOperations}</dt><dd>{agent.receipt.read_operations}</dd></div>
        <div><dt>{labels.toolCalls}</dt><dd>{agent.receipt.adk_tool_calls}</dd></div>
        <div><dt>{labels.embeddings}</dt><dd>{agent.receipt.embedding_calls}</dd></div>
        <div><dt>{labels.actions}</dt><dd>{agent.receipt.action_executed ? 1 : 0}</dd></div>
      </dl>

      {retrieval ? (
        <div className="incident-rag-card">
          <header>
            <span aria-hidden="true">R</span>
            <div>
              <small>RAG · SEMANTIC SEARCH</small>
              <h4>{labels.ragTitle}</h4>
            </div>
          </header>
          <p>{labels.ragBody}</p>
          <dl>
            <div>
              <dt>{labels.model}</dt>
              <dd translate="no">{retrieval.embedding_profile?.model_id ?? "–"}</dd>
            </div>
            <div>
              <dt>{labels.vectorDb}</dt>
              <dd translate="no">{retrieval.backend === "pgvector_exact_cosine" ? "pgvector" : retrieval.backend}</dd>
            </div>
            <div>
              <dt>{labels.matches}</dt>
              <dd>{retrieval.matches.length}</dd>
            </div>
          </dl>
          <ol>
            {retrieval.matches.slice(0, 2).map((match) => {
              const source = runbooks.find((item: RunbookEvidence) => item.evidence_id === match.evidence_id);
              return (
                <li
                  key={match.evidence_id}
                  data-incident-source={match.evidence_id}
                  data-citation-focus={focusedEvidenceId === match.evidence_id}
                >
                  <span>{match.rank}</span>
                  <div>
                    <small>{labels.source}</small>
                    <strong translate="no">{source?.content.document_id ?? match.evidence_id}</strong>
                    {source?.content.text ? <p>{source.content.text}</p> : null}
                  </div>
                  <em>{labels.semanticMatch} {formatPercent(match.cosine_similarity, locale)}</em>
                </li>
              );
            })}
          </ol>
          <p className="incident-rag-similarity-note">{labels.similarityNote}</p>
        </div>
      ) : (
        <div className="incident-playback-empty">{labels.noRag}</div>
      )}
    </div>
  );
}

function PlaybackJava({
  run,
  locale,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
}) {
  const labels = copy[locale];
  const verification = run.agent_turn?.verification_event;
  const claims = run.localized_presentations[locale].developer_response.verified_claims;

  if (!verification) {
    return (
      <div className="incident-playback-scene">
        <p className="incident-playback-scene__kicker">{labels.sceneJavaKicker}</p>
        <h3>{labels.noVerification}</h3>
        <p className="incident-playback-scene__lead">{labels.noAlarmLead}</p>
      </div>
    );
  }

  const checks = [
    [labels.responseFormat, verification.schema_valid],
    [labels.citations, verification.citations_valid],
    [labels.evidenceSupport, verification.direct_evidence_support_valid],
    [labels.agentOrder, verification.agent_sequence_valid],
    [labels.toolBoundary, verification.tool_boundary_valid],
  ] as const;

  return (
    <div className="incident-playback-scene incident-playback-scene--java" data-incident-scene="java">
      <p className="incident-playback-scene__kicker">{labels.sceneJavaKicker}</p>
      <h3>{labels.sceneJavaTitle}</h3>
      <p className="incident-playback-scene__lead">{labels.sceneJavaLead}</p>
      <ul className="incident-java-checks">
        {checks.map(([label, passed]) => (
          <li key={label} data-passed={passed}>
            <span aria-hidden="true">{passed ? "✓" : "!"}</span>
            <strong>{label}</strong>
            <em>{passed ? labels.passed : labels.stopped}</em>
          </li>
        ))}
      </ul>
      <div className="incident-java-release" data-released={verification.answer_released}>
        <span aria-hidden="true">{verification.answer_released ? "✓" : "!"}</span>
        <div>
          <strong>{verification.answer_released ? labels.released : labels.withheld}</strong>
          <p>{claims.length} {labels.verifiedClaims}</p>
        </div>
      </div>
    </div>
  );
}

function PlaybackAnswer({
  run,
  locale,
  detailsOpen,
  onToggleDetails,
  onSelectScene,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  detailsOpen: boolean;
  onToggleDetails: () => void;
  onSelectScene: (scene: number) => void;
}) {
  const labels = copy[locale];
  const presentation = run.localized_presentations[locale];
  const developerResponse = presentation.developer_response;
  const problemLocation = developerResponse.affected_service
    ? humanise(developerResponse.affected_service)
    : run.alarm_receipt?.service
      ? `${labels.signalFrom} ${humanise(run.alarm_receipt.service)}`
      : "–";
  const cause = run.answer_state === "diagnosed" && developerResponse.root_cause_code
    ? humanise(developerResponse.root_cause_code)
    : presentation.business_response.what_remains_unknown[0] ?? labels.causeUnavailable;

  return (
    <div className="incident-playback-scene incident-playback-scene--answer">
      <p className="incident-playback-scene__kicker">{labels.sceneAnswerKicker}</p>
      <h3>{run.answer_state === "withheld" ? labels.safeWithheldTitle : presentation.business_response.headline}</h3>
      <div className="incident-answer-analysis">
        <span>{labels.analysis}</span>
        <p className="incident-answer-copy">{presentation.business_response.what_happened}</p>
      </div>
      <dl className="incident-answer-summary">
        <div>
          <dt>{labels.location}</dt>
          <dd>{problemLocation}</dd>
        </div>
        <div>
          <dt>{labels.cause}</dt>
          <dd>{cause}</dd>
        </div>
        <div>
          <dt>{labels.impact}</dt>
          <dd>{presentation.business_response.impact}</dd>
        </div>
      </dl>
      <div className="incident-answer-knowledge">
        <section>
          <h4>{labels.known}</h4>
          <ul>{presentation.business_response.what_is_known.map((item) => <li key={item}>{item}</li>)}</ul>
        </section>
        <section>
          <h4>{labels.unknown}</h4>
          {presentation.business_response.what_remains_unknown.length > 0 ? (
            <ul>{presentation.business_response.what_remains_unknown.map((item) => <li key={item}>{item}</li>)}</ul>
          ) : <p>{labels.nothingUnknown}</p>}
        </section>
      </div>
      <div className="incident-answer-boundary" data-state={run.answer_state}>
        <span aria-hidden="true">{run.answer_state === "diagnosed" ? "✓" : "!"}</span>
        <p><strong>{presentation.action_receipt.summary}</strong></p>
      </div>
      <div className="incident-answer-explore">
        <span>{labels.explore}</span>
        <div>
          <button type="button" onClick={() => onSelectScene(0)}>{labels.askLogs}</button>
          <button type="button" onClick={() => onSelectScene(2)}>{labels.askRag}</button>
          <button type="button" onClick={() => onSelectScene(3)}>{labels.askBoundary}</button>
        </div>
      </div>
      <button
        className="incident-technical-toggle"
        type="button"
        aria-expanded={detailsOpen}
        aria-controls="incident-investigation-details"
        onClick={onToggleDetails}
      >
        <span>{detailsOpen ? labels.hideTechnical : labels.technical}</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d={detailsOpen ? "m6 14 6-6 6 6" : "m6 10 6 6 6-6"} /></svg>
      </button>
    </div>
  );
}

function IncidentPlayback({
  run,
  locale,
  replay,
  capabilities,
  detailsOpen,
  onToggleDetails,
  onReset,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  replay: IncidentLabReplayResponse | null;
  capabilities: CapabilitiesResponse | null;
  detailsOpen: boolean;
  onToggleDetails: () => void;
  onReset: () => void;
}) {
  const labels = copy[locale];
  const [scene, setScene] = useState(0);
  const [focusedEvidenceId, setFocusedEvidenceId] = useState<string | null>(null);
  const [focusedScene, setFocusedScene] = useState<IncidentLabFollowUpCitation["target_scene"] | null>(null);
  const playbackRef = useRef<HTMLElement | null>(null);
  const scenes = labels.sceneNames;
  const candidateFollowUpReference = replay?.run_reference ?? run.run_reference;
  const followUpReference = candidateFollowUpReference
    && /^ilr_[A-Za-z0-9_-]{20,72}$/.test(candidateFollowUpReference)
    ? candidateFollowUpReference
    : null;

  useEffect(() => {
    playbackRef.current?.scrollIntoView?.({
      behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
      block: "start",
    });
  }, []);

  useEffect(() => {
    if (!focusedEvidenceId && !focusedScene) return;
    const frame = window.requestAnimationFrame(() => {
      const evidenceTarget = Array.from(
        playbackRef.current?.querySelectorAll<HTMLElement>("[data-incident-source]") ?? [],
      ).find((element) => element.dataset.incidentSource === focusedEvidenceId);
      const sceneTarget = focusedScene
        ? playbackRef.current?.querySelector<HTMLElement>(`[data-incident-scene="${focusedScene}"]`)
        : null;
      (evidenceTarget ?? sceneTarget)?.scrollIntoView?.({
        block: "center",
        behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [focusedEvidenceId, focusedScene, scene]);

  function selectScene(nextScene: number) {
    setFocusedEvidenceId(null);
    setFocusedScene(null);
    setScene(nextScene);
  }

  function openCitation(citation: IncidentLabFollowUpCitation) {
    const sceneByTarget: Record<IncidentLabFollowUpCitation["target_scene"], number> = {
      logs: 0,
      agent_rag: 2,
      java: 3,
    };
    setFocusedEvidenceId(citation.evidence_id);
    setFocusedScene(citation.target_scene);
    setScene(sceneByTarget[citation.target_scene]);
  }

  return (
    <article ref={playbackRef} className="incident-playback" aria-labelledby="incident-playback-title">
      <header className="incident-playback__header">
        <div>
          <p>{labels.playbackKicker}</p>
          <h3 id="incident-playback-title">{labels.playbackTitle}</h3>
        </div>
        <span data-mode={replay ? "recorded" : "live"}>
          <i aria-hidden="true" />
          {replay ? labels.playbackRecorded : labels.playbackLive}
        </span>
      </header>

      <nav className="incident-playback-nav" aria-label={labels.playbackTitle}>
        <ol>
          {scenes.map((name, index) => (
            <li key={name} data-state={index < scene ? "complete" : index === scene ? "current" : "upcoming"}>
              <button
                type="button"
                aria-current={index === scene ? "step" : undefined}
                onClick={() => selectScene(index)}
              >
                <span aria-hidden="true">{index < scene ? "✓" : index + 1}</span>
                <strong>{name}</strong>
              </button>
            </li>
          ))}
        </ol>
      </nav>

      <div className="incident-playback__viewport">
        {scene === 0 ? <PlaybackLogs run={run} locale={locale} focusedEvidenceId={focusedEvidenceId} /> : null}
        {scene === 1 ? <PlaybackAlarm run={run} locale={locale} capabilities={capabilities} /> : null}
        {scene === 2 ? (
          <PlaybackAgent
            run={run}
            locale={locale}
            capabilities={capabilities}
            focusedEvidenceId={focusedEvidenceId}
          />
        ) : null}
        {scene === 3 ? <PlaybackJava run={run} locale={locale} /> : null}
        {scene === 4 ? (
          <PlaybackAnswer
            run={run}
            locale={locale}
            detailsOpen={detailsOpen}
            onToggleDetails={onToggleDetails}
            onSelectScene={selectScene}
          />
        ) : null}
        {followUpReference ? (
          <div hidden={scene !== 4}>
            <IncidentFollowUpChat
              locale={locale}
              runReference={followUpReference}
              mode={replay ? "recorded_replay" : "live_ai"}
              onOpenCitation={openCitation}
            />
          </div>
        ) : scene === 4 ? (
          <p className="incident-followup-unavailable">{labels.followUpUnavailable}</p>
        ) : null}
      </div>

      <footer className="incident-playback__controls">
        <button type="button" onClick={() => selectScene(Math.max(0, scene - 1))} disabled={scene === 0}>
          {labels.previous}
        </button>
        <span>{labels.scene} {scene + 1} / {scenes.length}</span>
        {scene < scenes.length - 1 ? (
          <button className="incident-playback__next" type="button" onClick={() => selectScene(Math.min(scenes.length - 1, scene + 1))}>
            {labels.nextScene}: {scenes[scene + 1]}
            <span aria-hidden="true">→</span>
          </button>
        ) : (
          <button className="incident-playback__next" type="button" onClick={onReset}>{labels.again}</button>
        )}
      </footer>
    </article>
  );
}

export default function IncidentLabPortal({ locale, active }: IncidentLabPortalProps) {
  const labels = copy[locale];
  const [stage, setStage] = useState<Stage>("checking");
  const [status, setStatus] = useState<LiveAiStatusResponse | null>(null);
  const [replayAvailability, setReplayAvailability] = useState<IncidentLabReplayAvailabilityResponse | null>(null);
  const [capabilities, setCapabilities] = useState<CapabilitiesResponse | null>(null);
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
  const capabilityReady = capabilities?.contract_version === "capabilities-v5"
    && capabilities.incident_lab.enabled;
  const liveReady = !statusFailed
    && status?.live_state === "available"
    && capabilityReady;
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
      getCapabilities(controller.signal),
    ]).then(([statusResult, replayResult, capabilitiesResult]) => {
      if (controller.signal.aborted) return;
      if (statusResult.status === "fulfilled" && capabilitiesResult.status === "fulfilled") {
        setStatus(statusResult.value);
        setStatusFailed(false);
      } else {
        setStatusFailed(true);
      }
      if (replayResult.status === "fulfilled") setReplayAvailability(replayResult.value);
      if (capabilitiesResult.status === "fulfilled") setCapabilities(capabilitiesResult.value);
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
    <div className="agent-portal incident-portal" hidden={!active}>
      <section className="hero agent-hero incident-hero" aria-labelledby="incident-page-title">
        <p className="hero__eyebrow">{labels.eyebrow}</p>
        <h1 id="incident-page-title">{labels.title}</h1>
        <p className="hero__lead">{labels.lead}</p>
        <p className="hero__stack">{labels.stack}</p>
      </section>

      <section className="product-stage agent-product-stage incident-product-stage" aria-labelledby="incident-scene-title">
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
            <strong>
              {stage === "checking"
                ? labels.checking
                : statusFailed
                  ? labels.statusUnknown
                  : capabilityReady
                    ? statusLabel(status, locale)
                    : labels.notConfigured}
            </strong>
            {status?.daily_cost_guard_active ? (
              <small>{status.quota_scope === "database_global" ? labels.costGuardGlobal : labels.costGuardLocal}</small>
            ) : null}
          </div>

          <div className="conversation agent-conversation incident-conversation">
            {!submittedInstruction && (stage === "checking" || stage === "ready") ? (
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
                    autoComplete="off"
                    maxLength={INCIDENT_LAB_PLAN_INSTRUCTION_MAX_LENGTH}
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

                {!liveReady && stage !== "checking" ? (
                  <div className="incident-fallback">
                    {replayAvailable ? (
                      <button type="button" onClick={startReplay}>{labels.replay}</button>
                    ) : (
                      <span>{labels.replayUnavailable}</span>
                    )}
                  </div>
                ) : null}
                {liveReady && replayAvailable ? (
                  <button className="incident-replay-link" type="button" onClick={startReplay}>{labels.replay}</button>
                ) : null}
              </>
            ) : null}

            {submittedInstruction ? (
              <div className="message-row message-row--user">
                <div className="message-bubble message-bubble--user">{submittedInstruction}</div>
              </div>
            ) : null}

            {stage === "planning" ? (
              <div className="waiting-state agent-waiting" role="status" tabIndex={-1}>
                <span className="waiting-orbit" aria-hidden="true"><span /></span>
                <div>
                  <strong>{labels.planning}</strong>
                  <p>{waitIsLong ? labels.slowPlanning : labels.planningBody}</p>
                </div>
              </div>
            ) : null}

            {stage === "plan_ready" && approvedPlan && plan ? (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant incident-plan-card incident-chat-plan">
                  <p>{labels.planReady}</p>
                  <h3>{labels.planTitle}</h3>
                  <div className="incident-plan-summary">
                    <span>{labels.planIntro}</span>
                    <strong>{familyLabel(capabilities, approvedPlan.incident_family, locale)}</strong>
                    <small>{approvedPlan.affected_services.map(humanise).join(", ")} · {labels.severity}: {humanise(approvedPlan.severity)}</small>
                  </div>
                  <ul className="incident-plan-boundaries">
                    {[
                      approvedPlan.synthetic_only ? labels.syntheticData : null,
                      !approvedPlan.write_actions_allowed ? labels.noWriteTools : null,
                      approvedPlan.human_approval_required ? labels.approvalRequired : null,
                    ].filter((item) => item !== null).map((item) => (
                      <li key={item}><span aria-hidden="true">✓</span>{item}</li>
                    ))}
                  </ul>
                  <div className="incident-plan-actions">
                    <button className="incident-primary" type="button" onClick={startRun}>{labels.run}<span aria-hidden="true">→</span></button>
                    <button type="button" onClick={reset}>{labels.newPlan}</button>
                  </div>
                </article>
              </div>
            ) : null}

            {stage === "running" ? (
              <div className="waiting-state agent-waiting" role="status" tabIndex={-1}>
                <span className="waiting-orbit" aria-hidden="true"><span /></span>
                <div>
                  <strong>{replayRequested ? labels.replayRunning : labels.running}</strong>
                  <p>{waitIsLong ? labels.slowRunning : labels.runningBody}</p>
                </div>
              </div>
            ) : null}

            {stage === "result" && (planBlocked || planRejected) && plan ? (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant agent-answer agent-answer--blocked incident-chat-answer">
                  <p className="answer-card__kicker">{labels.backendCode}</p>
                  <h3>{planBlocked ? labels.blocked : labels.rejected}</h3>
                  <p>{locale === "sv" ? plan.safety.summary_sv : plan.safety.summary_en}</p>
                </article>
              </div>
            ) : null}

            {stage === "failed" ? (
              <div className="message-row message-row--assistant incident-chat-row">
                <span className="message-avatar" aria-hidden="true">N</span>
                <article className="message-bubble message-bubble--assistant agent-answer agent-answer--withheld incident-chat-answer">
                  <p className="answer-card__kicker">{labels.backendCode}</p>
                  <h3>{labels.failed}</h3>
                  <p>{labels.failedBody}</p>
                  {failure?.detail ? <p className="agent-failure-detail"><strong>{labels.backendCode}</strong>{failure.detail}</p> : null}
                  <p className="agent-run-facts">
                    {failure?.code ?? (failure?.status ? `HTTP_${failure.status}` : "NOT_REPORTED")}
                    {failure?.retryAfterSeconds !== undefined ? ` · ${labels.retry} ${failure.retryAfterSeconds} ${labels.seconds}` : ""}
                  </p>
                </article>
              </div>
            ) : null}

            {stage === "result" && run ? (
              <IncidentPlayback
                key={`${run.scenario.scenario_id}-${replay?.playback_id ?? "live"}`}
                run={run}
                locale={locale}
                replay={replay}
                capabilities={capabilities}
                detailsOpen={detailsOpen}
                onToggleDetails={() => setDetailsOpen((open) => !open)}
                onReset={reset}
              />
            ) : null}

            {(stage === "failed" || (stage === "result" && !run)) ? (
              <div className="agent-result-actions">
                <button className="agent-reset" type="button" onClick={reset}>{labels.again}</button>
                {replayAvailable ? <button className="agent-reset" type="button" onClick={startReplay}>{labels.replay}</button> : null}
              </div>
            ) : null}
          </div>

          {stage === "result" && run && detailsOpen ? (
            <section id="incident-investigation-details" className="incident-investigation-details">
              {replay ? (
                <div className="incident-replay-receipt">
                  <strong>{labels.replayReceipt}</strong>
                  <span>{replayReceiptText(replay, locale)}</span>
                  <small>{replayProvenanceText(replay, locale)} · {replay.playback_id}</small>
                </div>
              ) : null}
              <div className="incident-operator-note">
                <span>{labels.recommendation}</span>
                <p>{run.localized_presentations[locale].business_response.safe_next_step}</p>
              </div>
              {run.agent_turn ? <BehindTheAnswer result={run.agent_turn} locale={locale} /> : null}
            </section>
          ) : null}
        </div>
      </section>
    </div>
  );
}
