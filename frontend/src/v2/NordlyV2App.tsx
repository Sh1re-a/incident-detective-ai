import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import type { FormEvent, ReactNode } from "react";
import {
  AnimatePresence,
  LayoutGroup,
  MotionConfig,
  motion,
} from "motion/react";
import {
  createIncidentLabPlan,
  getDemoOrder,
  getCapabilities,
  getKnowledgeDocuments,
  getLiveAiStatus,
  IncidentApiError,
  runDemoCustomerChatTurn,
  runIncidentLab,
  runIncidentLabFollowUp,
  runIncidentLabReplay,
  runKnowledgeReplay,
} from "../api/client";
import type {
  CapabilitiesResponse,
  DemoCustomerChatSource,
  DemoCustomerChatTurnResponse,
  DemoOrder,
  GeneratedIncidentFamily,
  IncidentFamilyCapability,
  IncidentLabFollowUpCitation,
  IncidentLabFollowUpResponse,
  IncidentLabFollowUpSuggestionId,
  IncidentLabPlanResponse,
  IncidentLabReplayResponse,
  IncidentLabRunResponse,
  KnowledgeDocumentLibraryDocument,
  KnowledgeDocumentLibraryResponse,
  KnowledgeRagLocale,
  KnowledgeReplayResponse,
  LiveAiStatusResponse,
  LogEvidence,
  RunbookEvidence,
} from "../api/generated";
import {
  buildCustomerChatConversation,
} from "../customerChatSession";
import type { CustomerChatTurn } from "../customerChatSession";
import {
  Siren as AlertIcon,
  ArrowLeft as ArrowLeftIcon,
  Package as BoxIcon,
  MessageCircle as ChatIcon,
  Check as CheckIcon,
  ChevronRight as ChevronRightIcon,
  X as CloseIcon,
  FileText as DocumentIcon,
  LockKeyhole as LockIcon,
  AudioWaveform as NordlySignal,
  RefreshCw as RefreshIcon,
  Search as SearchIcon,
  Send as SendIcon,
  ShieldCheck as ShieldIcon,
  Wrench as ToolIcon,
} from "lucide-react";
import "./nordly-v2.css";

type Mode = "drift" | "support" | "documents";
type Locale = KnowledgeRagLocale;

type SelectedDocument = {
  documentId: string;
  chunkId: string | null;
  returnMode: Mode;
};

type EvidenceState =
  | { kind: "support"; response: DemoCustomerChatTurnResponse }
  | {
    kind: "drift";
    session: DriftSession;
    focus?: Pick<IncidentLabFollowUpCitation, "target_scene" | "target_id">;
  }
  | null;

type DriftSession =
  | {
    mode: "live_ai";
    run: IncidentLabRunResponse;
    runReference: string | null;
    plan: IncidentLabPlanResponse;
  }
  | {
    mode: "recorded_replay";
    run: IncidentLabRunResponse;
    runReference: string | null;
    replay: IncidentLabReplayResponse;
  };

type AgentBadge = {
  label: string;
  tone: "online" | "paused" | "offline" | "replay";
};

type SupportReplayBundle = {
  policy: KnowledgeReplayResponse;
  boundary: KnowledgeReplayResponse;
  order: DemoOrder;
};

type DriftTurn = {
  id: string;
  question: string;
  response: IncidentLabFollowUpResponse | null;
  localAnswer: string | null;
  pending: boolean;
  error: string | null;
};

type LocalDriftSuggestion = "when" | "what_unknown_receipt" | "customer_impact_receipt";

const EXPECTED_DRIFT_FAMILIES: GeneratedIncidentFamily[] = [
  "payment_timeout",
  "catalog_cache_invalidation",
  "order_event_backlog",
  "order_idempotency_failure",
];

function driftPlanInstruction(family: IncidentFamilyCapability, locale: Locale) {
  const description = family.description[locale];
  const impact = family.customer_impact[locale];
  return localized(
    locale,
    `Skapa ett syntetiskt Nordly-fall: ${description} Kundpåverkan: ${impact} Utred endast med läsverktyg, redovisa den starkaste stödda bidragande faktorn och säg tydligt vad som fortfarande är osäkert. Kräv mänskligt godkännande för nästa steg och gör inga ändringar.`,
    `Create a synthetic Nordly case: ${description} Customer impact: ${impact} Investigate with read-only tools, report the strongest supported contributing factor, and state clearly what remains uncertain. Require human approval for the next step and make no changes.`,
  );
}

const COPY = {
  sv: {
    modes: { drift: "Driftagent", support: "Supportagent", documents: "Dokumentarkiv" },
    footer: "Verifierade källor · Endast läsning · Interaktiv AI-demo",
    synthetic: "Portfolio-demo med syntetisk data · Använd Live-AI ansvarsfullt – begränsad dagskvot",
    supportIdentity: "Hjälper Nordlys kunder",
    driftIdentity: "Rapporterar vad som händer i Nordlys köpflöde",
    greeting: "Hej Shirre! Vad kan jag hjälpa dig med?",
    supportPlaceholder: "Skriv till Nordly…",
    driftPlaceholder: "Fråga om rapporten…",
    send: "Skicka",
    supportSuggestions: ["Vad beställde jag?", "När har kundservice öppet?", "Kan jag få pengarna tillbaka?"],
    waiting: "Nordly undersöker…",
    slow: "Det tar lite längre än vanligt. Jag arbetar fortfarande med ditt svar.",
    retry: "Försök igen",
    confirmTitle: "Sök i Nordlys godkända dokument?",
    confirmBody: "För att svara behöver jag göra en live-sökning i företagets policydokument.",
    noProviderYet: "Ingen sökning har startat ännu.",
    cancel: "Avbryt",
    searchDocuments: "Sök i dokumenten",
    behindAnswer: "Så kom svaret fram",
    behindReport: "Så kom jag fram till det",
    source: "Källa",
    order: "Order",
    status: "Status",
    delivery: "Beräknad leverans",
    verifiedNoChange: "Verifierat svar · ingen ändring",
    readOnly: "Endast läst · inget ändrat",
    newConversation: "Ny konversation",
    endConversation: "Avsluta samtal",
    replayPill: "Säkerhetsreplay",
    driftIdleTitle: "Vilket problem vill du att jag undersöker?",
    driftIdleBody: "Välj ett syntetiskt kundproblem. Jag granskar nya loggar och relevanta driftinstruktioner och återkommer här i chatten.",
    driftGreeting: "Hej. Jag är Nordlys Driftagent och håller koll på köpflödet.",
    driftCasePrompt: (impact: string) => `Undersök: ${impact.replace(/[.]$/, "")}`,
    driftGenericPrompt: "Undersök ett nytt syntetiskt problem i köpflödet",
    driftCasesLoading: "Hämtar fyra syntetiska situationer…",
    customerAffected: "Jag ser att kunder påverkas.",
    alarmTrigger: "Det här utlöste larmet",
    humanReport: "Min bedömning",
    strongestFactor: "Starkaste bidragande faktor",
    teamSuggestion: "Mitt förslag till teamet",
    stillUncertain: "Fortfarande osäkert",
    continueChat: "Jag har inte ändrat något. Du kan fortsätta fråga om tid, påverkan, källor eller osäkerhet.",
    startReplay: "Visa säkerhetsreplay",
    startDriftAlarm: "Starta live-utredning",
    liveAvailable: "Live-AI tillgänglig",
    livePaused: "Live-AI pausad · se replay",
    liveOffline: "AI offline · se replay",
    liveUnknown: "AI-status okänd · se replay",
    replayTruth: "Historisk säkerhetskörning · osäkert svar stoppas · 0 nya AI-anrop",
    liveTruth: "Ny AI-körning · syntetisk data · endast läsning",
    replayChatPlaceholder: "Fri chatt kräver Live-AI",
    replayNotAvailable: "Replay är inte tillgänglig",
    receiptWaiting: "Jag har tagit emot larmet. Jag granskar testmiljöns loggar och relevanta driftinstruktioner…",
    receiptReady: "Driftagenten granskar loggar och relevanta driftinstruktioner…",
    showNow: "Visa rapporten",
    replayUnavailable: "Larmdemot kunde inte laddas.",
    incidentHello: "Jag ser att kunder påverkas.",
    whenQuestion: "När började det?",
    whyQuestion: "Vet du varför?",
    timeAnswer: (times: string, service: string) => `${times}. Händelserna registrerades i ${service}, som är larmets signalkälla.`,
    withheldTitle: "Jag kan se problemet – men inte bevisa orsaken än.",
    withheldBody: "Underlaget räcker inte för en säker rotorsak. Jag rapporterar därför vad som är känt utan att gissa.",
    noRunReference: "Körningen saknar ett giltigt följdfrågekvitto. Källorna går fortfarande att granska.",
    driftSuggestions: {
      how_conclusion: "Varför tror du det?",
      show_sources: "Visa källorna",
      what_unknown: "Vad är fortfarande osäkert?",
      customer_impact: "Hur påverkas kunderna?",
      agent_boundary: "Vad fick agenten göra?",
    },
    archiveTitle: "Dokumentarkiv",
    archiveLead: "Källorna som bestämmer vad agenterna får veta.",
    searchPlaceholder: "Sök i dokument…",
    archiveLoading: "Hämtar företagets dokument…",
    archiveError: "Dokumentarkivet kunde inte hämtas.",
    approvedSource: "Godkänd källa",
    protected: "Skyddat",
    protectedLabel: "Begränsad källa",
    protectedTitle: "Innehållet är skyddat och visas inte här.",
    protectedBody: "Dokumentet indexeras inte och skickas aldrig till modellen. Endast tillåten metadata är synlig.",
    accessChecked: "Behörighet kontrollerad",
    noPassages: "0 dokumentpassager lästa",
    noModelCalls: "0 modell-anrop",
    backToArchive: "Tillbaka till dokumentarkivet",
    backToSupport: "Tillbaka till Support",
    backToDrift: "Tillbaka till Drift",
    sourceInAnswer: "Källa i svaret",
    passageUsed: "Passage som användes i svaret",
    noResults: "Inga dokument matchar sökningen.",
    chooseDocument: "Välj ett dokument för att läsa den godkända versionen.",
    evidenceEyebrow: "Bakom svaret",
    reportEyebrow: "Bakom rapporten",
    registeredNotThoughts: "Registrerade steg — inte AI:ns dolda tankar.",
    sourcesAndEvidence: "Källor och bevis",
    technicalReceipt: "Tekniskt kvitto",
    close: "Stäng",
    safetyReceipt: "Säkerhetskvitto",
    stoppedBeforeAi: "Så skyddades frågan",
    safetyWon: "Skyddsreglerna följdes · inget privat lästes",
    changesZero: "0 ändringar",
  },
  en: {
    modes: { drift: "Operations agent", support: "Support agent", documents: "Document archive" },
    footer: "Verified sources · Read only · Interactive AI demo",
    synthetic: "Portfolio demo with synthetic data · Please use Live AI responsibly — limited daily quota",
    supportIdentity: "Helps Nordly customers",
    driftIdentity: "Reports what is happening in Nordly's checkout flow",
    greeting: "Hi Shirre! How can I help?",
    supportPlaceholder: "Message Nordly…",
    driftPlaceholder: "Ask about the report…",
    send: "Send",
    supportSuggestions: ["What did I order?", "When is customer service open?", "Can I get my money back?"],
    waiting: "Nordly is checking…",
    slow: "This is taking a little longer. We are still waiting for the backend's complete receipt.",
    retry: "Try again",
    confirmTitle: "Search Nordly's approved documents?",
    confirmBody: "To answer, I need to run a live search across the company's approved policy documents.",
    noProviderYet: "No provider calls have been made yet.",
    cancel: "Cancel",
    searchDocuments: "Search documents",
    behindAnswer: "How this answer was made",
    behindReport: "How I reached this",
    source: "Source",
    order: "Order",
    status: "Status",
    delivery: "Estimated delivery",
    verifiedNoChange: "Verified answer · no change",
    readOnly: "Read only · nothing changed",
    newConversation: "New conversation",
    endConversation: "End conversation",
    replayPill: "Safety replay",
    driftIdleTitle: "Which problem should I investigate?",
    driftIdleBody: "Choose a synthetic customer problem. I review fresh logs and relevant operating guides, then report back here in the chat.",
    driftGreeting: "Hi. I am Nordly's Operations agent and I watch the checkout flow.",
    driftCasePrompt: (impact: string) => `Investigate: ${impact.replace(/[.]$/, "")}`,
    driftGenericPrompt: "Investigate a new synthetic checkout issue",
    driftCasesLoading: "Loading four synthetic situations…",
    customerAffected: "I can see customers are affected.",
    alarmTrigger: "This triggered the alert",
    humanReport: "My assessment",
    strongestFactor: "Strongest contributing factor",
    teamSuggestion: "My suggestion to the team",
    stillUncertain: "Still uncertain",
    continueChat: "I changed nothing. You can keep asking about timing, impact, sources, or uncertainty.",
    startReplay: "View safety replay",
    startDriftAlarm: "Start live investigation",
    liveAvailable: "Live AI available",
    livePaused: "Live AI paused · view replay",
    liveOffline: "AI offline · view replay",
    liveUnknown: "AI status unknown · view replay",
    replayTruth: "Historical safety run · unsupported answer withheld · 0 new AI calls",
    liveTruth: "New AI run · synthetic data · read only",
    replayChatPlaceholder: "Free chat requires Live AI",
    replayNotAvailable: "Replay is unavailable",
    receiptWaiting: "I received the alert. I am reviewing the test environment's logs and relevant operating guides…",
    receiptReady: "The Operations agent is reviewing logs and relevant operating guides…",
    showNow: "Show the report",
    replayUnavailable: "The alert demo could not be loaded.",
    incidentHello: "I can see customers are affected.",
    whenQuestion: "When did it start?",
    whyQuestion: "Do you know why?",
    timeAnswer: (times: string, service: string) => `${times}. The events were recorded in ${service}, which is the alarm's signal source.`,
    withheldTitle: "I can see the problem — but I cannot prove the cause yet.",
    withheldBody: "The evidence is not enough for a safe root cause. I report what is known without guessing.",
    noRunReference: "This run has no valid follow-up receipt. Its sources can still be inspected.",
    driftSuggestions: {
      how_conclusion: "Why do you think that?",
      show_sources: "Show sources",
      what_unknown: "What is still uncertain?",
      customer_impact: "How are customers affected?",
      agent_boundary: "What could the agent do?",
    },
    archiveTitle: "Document archive",
    archiveLead: "The sources that determine what the agents may know.",
    searchPlaceholder: "Search documents…",
    archiveLoading: "Loading company documents…",
    archiveError: "The document archive could not be loaded.",
    approvedSource: "Approved source",
    protected: "Protected",
    protectedLabel: "Restricted source",
    protectedTitle: "This content is protected and is not shown here.",
    protectedBody: "The document is not indexed and is never sent to the model. Only permitted metadata is visible.",
    accessChecked: "Access checked",
    noPassages: "0 document passages read",
    noModelCalls: "0 model calls",
    backToArchive: "Back to document archive",
    backToSupport: "Back to Support",
    backToDrift: "Back to Operations",
    sourceInAnswer: "Source in the answer",
    passageUsed: "Passage used in the answer",
    noResults: "No documents match the search.",
    chooseDocument: "Choose a document to read the approved version.",
    evidenceEyebrow: "Behind the answer",
    reportEyebrow: "Behind the report",
    registeredNotThoughts: "Registered steps — not the AI's hidden thoughts.",
    sourcesAndEvidence: "Sources and evidence",
    technicalReceipt: "Technical receipt",
    close: "Close",
    safetyReceipt: "Safety receipt",
    stoppedBeforeAi: "How the request was protected",
    safetyWon: "Protection rules followed · no private data read",
    changesZero: "0 changes",
  },
} as const;

const REPLAY_STEP_DELAYS = [550, 1350, 1550] as const;
const SUPPORT_REPLAY_STEP_DELAYS = [450, 750, 1250, 850, 750, 1350] as const;

function modeFromHash(): Mode {
  const hash = window.location.hash.replace(/^#/, "");
  if (["drift", "incident-lab", "incident-detective"].includes(hash)) return "drift";
  if (["documents", "knowledge-room"].includes(hash)) return "documents";
  return "support";
}

function hashForMode(mode: Mode) {
  return mode === "drift" ? "#drift" : mode === "documents" ? "#documents" : "#support";
}

function clientId(prefix: string) {
  const random = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}_${random}`;
}

function apiErrorText(error: unknown, locale: Locale) {
  void error;
  return locale === "sv"
    ? "Jag kunde inte slutföra svaret just nu. Försök igen."
    : "I could not complete the answer right now. Please try again.";
}

function clock(value: string) {
  const match = value.match(/T(\d{2}:\d{2}:\d{2})/);
  return match?.[1] ?? value;
}

function serviceLabel(value: string, locale: Locale) {
  if (value.toUpperCase() === "PAYMENT_ADAPTER") {
    return localized(locale, "betalningsflödet", "the payment flow");
  }
  if (value.toLowerCase() === "backend") {
    return localized(locale, "köpflödet", "the checkout flow");
  }
  return value.replaceAll("_", " ").toLowerCase();
}

function shortDate(value: string, locale: Locale) {
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  return new Intl.DateTimeFormat(locale === "sv" ? "sv-SE" : "en-GB", {
    year: "numeric",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  }).format(date);
}

function localized(locale: Locale, sv: string, en: string) {
  return locale === "sv" ? sv : en;
}

function useMediaQuery(query: string) {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const update = () => setMatches(media.matches);
    media.addEventListener("change", update);
    update();
    return () => media.removeEventListener("change", update);
  }, [query]);
  return matches;
}

function alarmLogs(run: IncidentLabRunResponse | null): LogEvidence[] {
  if (!run) return [];
  const ids = new Set(run.alarm_receipt?.evidence_ids ?? []);
  const exact = run.backend_logs.filter((item) => ids.has(item.evidence_id));
  if (exact.length > 0) return exact;
  const highlightedIds = new Set(run.developer_response?.highlighted_log_evidence_ids ?? []);
  const highlighted = run.backend_logs.filter((item) => highlightedIds.has(item.evidence_id));
  if (highlighted.length > 0) return highlighted;
  return run.backend_logs.slice(0, 3);
}

function alarmValueLabel(value: number, unit: string, locale: Locale) {
  const normalized = Number.isInteger(value) ? value.toFixed(0) : value.toLocaleString(locale === "sv" ? "sv-SE" : "en-GB");
  const localizedUnit = locale === "sv"
    ? ({ count: "händelser", versions: "versioner", seconds: "sekunder" }[unit] ?? unit)
    : ({ count: "events", versions: "versions", seconds: "seconds" }[unit] ?? unit);
  return `${normalized} ${localizedUnit}`;
}

function titleForDocument(document: KnowledgeDocumentLibraryDocument, locale: Locale) {
  return locale === "sv" ? document.title_sv : document.title;
}

function runbookDocumentSource(
  runbook: RunbookEvidence,
  library: KnowledgeDocumentLibraryResponse | null,
  locale: Locale,
): DemoCustomerChatSource | null {
  if (!library) return null;
  const sourceId = runbook.content.document_id;
  const aliases: Record<string, string> = {
    "rb-cache-invalidation": "kb-catalog-cache-invalidation",
    "rb-message-queue-backlog": "kb-order-event-backlog",
  };
  const document = library.documents.find((item) => item.id === sourceId)
    ?? library.documents.find((item) => item.id === aliases[sourceId])
    ?? library.documents.find((item) => item.id === sourceId.replace(/^rb-/, "kb-"));
  if (!document) return null;
  const chunk = document.chunks.find((item) => item.id === runbook.content.chunk_id) ?? null;
  return {
    kind: "company_policy",
    document_id: document.id,
    chunk_id: chunk?.id ?? null,
    document_version: document.version,
    title: titleForDocument(document, locale),
    section_heading: chunk?.section_heading ?? null,
    source_ref: chunk?.source_ref ?? runbook.source_ref,
    evidence_id: runbook.evidence_id,
    lifecycle: document.lifecycle,
    similarity: null,
    display_summary_sv: chunk?.display_summary_sv ?? document.summary_sv,
    display_summary_en: chunk?.display_summary_en ?? document.summary_en,
  };
}

function supportOutcomeLabel(response: DemoCustomerChatTurnResponse, locale: Locale) {
  switch (response.outcome) {
    case "answered":
      return COPY[locale].verifiedNoChange;
    case "refused":
      return COPY[locale].safetyWon;
    case "outside_authority":
      return localized(locale, "Utanför agentens befogenhet · inget ändrat", "Outside the agent's authority · nothing changed");
    case "clarification_required":
      return localized(locale, "Behöver ett förtydligande · inget ändrat", "Needs clarification · nothing changed");
    case "insufficient_evidence":
      return localized(locale, "Otillräckligt underlag · inget ändrat", "Insufficient evidence · nothing changed");
    case "unsupported":
      return localized(locale, "Kan inte utföras av agenten · inget ändrat", "The agent cannot perform this · nothing changed");
    case "unavailable":
      return localized(locale, "Tillfälligt otillgänglig · inget ändrat", "Temporarily unavailable · nothing changed");
    default:
      return localized(locale, "Väntar på bekräftelse · inget ändrat", "Awaiting confirmation · nothing changed");
  }
}

export default function NordlyV2App() {
  const [locale, setLocale] = useState<Locale>("sv");
  const [mode, setModeState] = useState<Mode>(() => modeFromHash());
  const [evidence, setEvidence] = useState<EvidenceState>(null);
  const [selectedDocument, setSelectedDocument] = useState<SelectedDocument | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocumentLibraryResponse | null>(null);
  const [documentError, setDocumentError] = useState<string | null>(null);
  const [capabilities, setCapabilities] = useState<CapabilitiesResponse | null>(null);
  const [liveAiStatus, setLiveAiStatus] = useState<LiveAiStatusResponse | null>(null);
  const [liveAiStatusResolved, setLiveAiStatusResolved] = useState(false);
  const [supportTurns, setSupportTurns] = useState<CustomerChatTurn[]>([]);
  const [supportDraft, setSupportDraft] = useState("");
  const [supportPendingId, setSupportPendingId] = useState<string | null>(null);
  const [driftSession, setDriftSession] = useState<DriftSession | null>(null);
  const [driftState, setDriftState] = useState<"idle" | "requesting" | "playing" | "ready" | "error">("idle");
  const [driftFailureMode, setDriftFailureMode] = useState<DriftSession["mode"] | null>(null);
  const [driftError, setDriftError] = useState<string | null>(null);
  const [playbackStage, setPlaybackStage] = useState(0);
  const [driftTurns, setDriftTurns] = useState<DriftTurn[]>([]);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
  const supportSessionVersionRef = useRef(0);
  const driftSessionVersionRef = useRef(0);
  const copy = COPY[locale];

  useEffect(() => {
    const controller = new AbortController();
    getKnowledgeDocuments(controller.signal)
      .then(setDocuments)
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setDocumentError(apiErrorText(error, locale));
      });
    return () => controller.abort();
  }, [locale]);

  useEffect(() => {
    const controller = new AbortController();
    getCapabilities(controller.signal)
      .then((response) => {
        if (!controller.signal.aborted && response.contract_version === "capabilities-v5") {
          setCapabilities(response);
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setCapabilities(null);
      });
    return () => controller.abort();
  }, []);

  const refreshLiveAiStatus = useCallback(async (signal?: AbortSignal) => {
    setLiveAiStatusResolved(false);
    try {
      const status = await getLiveAiStatus(signal);
      if (!signal?.aborted) setLiveAiStatus(status);
    } catch {
      if (!signal?.aborted) setLiveAiStatus(null);
    } finally {
      if (!signal?.aborted) setLiveAiStatusResolved(true);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refreshLiveAiStatus(controller.signal);
    return () => controller.abort();
  }, [refreshLiveAiStatus]);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => {
    const onHashChange = () => setModeState(modeFromHash());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    if (driftState !== "playing") return;
    if (playbackStage >= 3) {
      setDriftState("ready");
      return;
    }
    const delay = REPLAY_STEP_DELAYS[playbackStage] ?? 1200;
    const timer = window.setTimeout(() => setPlaybackStage((current) => current + 1), delay);
    return () => window.clearTimeout(timer);
  }, [driftState, playbackStage]);

  const setMode = useCallback((next: Mode) => {
    setEvidence(null);
    setModeState(next);
    if (window.location.hash !== hashForMode(next)) {
      window.history.replaceState(null, "", hashForMode(next));
    }
  }, []);

  const openEvidence = useCallback((next: NonNullable<EvidenceState>) => {
    restoreFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null;
    setEvidence(next);
  }, []);

  const closeEvidence = useCallback(() => {
    setEvidence(null);
    window.requestAnimationFrame(() => restoreFocusRef.current?.focus());
  }, []);

  const openDocument = useCallback((source: DemoCustomerChatSource, returnMode: Mode = "support") => {
    if (!source.document_id) return;
    setSelectedDocument({
      documentId: source.document_id,
      chunkId: source.chunk_id,
      returnMode,
    });
    setMode("documents");
  }, [setMode]);

  const cancelSupportTurn = useCallback((id: string) => {
    setSupportTurns((current) => current.filter((turn) => turn.clientId !== id));
  }, []);

  const resetSupportConversation = useCallback(() => {
    supportSessionVersionRef.current += 1;
    setSupportTurns([]);
    setSupportDraft("");
    setSupportPendingId(null);
    setEvidence(null);
  }, []);

  const submitSupport = useCallback(async (
    rawMessage: string,
    confirmLiveAi = true,
    existingId?: string,
  ) => {
    const message = rawMessage.trim();
    if (!message || supportPendingId) return;
    const id = existingId ?? clientId("support");
    const sessionVersion = supportSessionVersionRef.current;
    const priorTurns = supportTurns.filter((turn) => turn.clientId !== id);
    if (!existingId) {
      setSupportTurns((current) => [...current, {
        clientId: id,
        question: message,
        locale,
        response: null,
        errorCode: null,
      }]);
    }
    setSupportPendingId(id);
    try {
      const response = await runDemoCustomerChatTurn({
        message,
        locale,
        confirm_live_ai: confirmLiveAi,
        recent_conversation: buildCustomerChatConversation(priorTurns),
      });
      if (supportSessionVersionRef.current !== sessionVersion) return;
      setSupportTurns((current) => current.map((turn) => turn.clientId === id
        ? {
          ...turn,
          question: turn.question,
          locale,
          response,
          errorCode: null,
        }
        : turn));
    } catch (error) {
      if (supportSessionVersionRef.current !== sessionVersion) return;
      setSupportTurns((current) => current.map((turn) => turn.clientId === id
        ? { ...turn, errorCode: apiErrorText(error, locale) }
        : turn));
    } finally {
      if (supportSessionVersionRef.current === sessionVersion) setSupportPendingId(null);
    }
  }, [locale, supportPendingId, supportTurns]);

  const startReplay = useCallback(async () => {
    if (driftState === "requesting") return;
    const sessionVersion = driftSessionVersionRef.current;
    setDriftFailureMode("recorded_replay");
    setDriftError(null);
    setDriftSession(null);
    setDriftState("requesting");
    setPlaybackStage(0);
    setDriftTurns([]);
    try {
      const response = await runIncidentLabReplay();
      if (driftSessionVersionRef.current !== sessionVersion) return;
      setDriftSession({
        mode: "recorded_replay",
        run: response.recorded_run,
        runReference: response.run_reference ?? response.recorded_run.run_reference,
        replay: response,
      });
      setDriftState("playing");
    } catch (error) {
      if (driftSessionVersionRef.current !== sessionVersion) return;
      setDriftError(apiErrorText(error, locale));
      setDriftState("error");
    }
  }, [driftState, locale]);

  const startLiveInvestigation = useCallback(async (family: IncidentFamilyCapability | null) => {
    if (driftState === "requesting") return;
    const sessionVersion = driftSessionVersionRef.current;
    setDriftFailureMode("live_ai");
    setDriftError(null);
    setDriftSession(null);
    setDriftState("requesting");
    setPlaybackStage(0);
    setDriftTurns([]);
    try {
      const plan = await createIncidentLabPlan({
        instruction: family
          ? driftPlanInstruction(family, locale)
          : localized(
            locale,
            "Skapa ett syntetiskt Nordly-fall där tre betalningar får HTTP 504 inom ett kort tidsfönster. Utred endast med läsverktyg och kräv mänskligt godkännande för nästa steg.",
            "Create a synthetic Nordly case where three payments receive HTTP 504 within a short window. Investigate only with read tools and require human approval for the next step.",
          ),
        confirm_live_ai: true,
      });
      if (driftSessionVersionRef.current !== sessionVersion) return;
      const approvedPlan = plan.outcome === "plan_ready" ? plan.java_validation?.plan : null;
      if (!approvedPlan) {
        const rejected = locale === "sv"
          ? plan.safety.summary_sv
          : plan.safety.summary_en;
        throw new IncidentApiError(rejected, 422, plan.safety.reason_code);
      }
      if (family && approvedPlan.incident_family !== family.id) {
        throw new IncidentApiError(
          localized(
            locale,
            "Planeringsagenten valde ett annat fall. Ingen utredning startades.",
            "The planning agent selected a different case. No investigation was started.",
          ),
          422,
          "PLAN_FAMILY_MISMATCH",
        );
      }
      const run = await runIncidentLab({
        plan: approvedPlan,
        evidence_mode: "diagnostic",
        confirm_live_ai: true,
      });
      if (driftSessionVersionRef.current !== sessionVersion) return;
      if (family && (
        run.plan.incident_family !== family.id
        || run.generation_receipt.incident_family !== family.id
        || run.alarm_receipt?.incident_family !== family.id
      )) {
        throw new IncidentApiError(
          localized(
            locale,
            "Körningskvittot matchade inte det valda fallet. Svaret stoppades.",
            "The run receipt did not match the selected case. The answer was withheld.",
          ),
          422,
          "RUN_FAMILY_MISMATCH",
        );
      }
      setDriftSession({ mode: "live_ai", run, runReference: run.run_reference, plan });
      setDriftState("playing");
    } catch (error) {
      if (driftSessionVersionRef.current !== sessionVersion) return;
      setDriftError(apiErrorText(error, locale));
      setDriftState("error");
    }
  }, [driftState, locale]);

  const resetDriftConversation = useCallback(() => {
    driftSessionVersionRef.current += 1;
    setDriftSession(null);
    setDriftState("idle");
    setDriftFailureMode(null);
    setDriftError(null);
    setPlaybackStage(0);
    setDriftTurns([]);
    setEvidence(null);
  }, []);

  const submitDrift = useCallback(async (
    rawQuestion: string,
    suggestionId?: IncidentLabFollowUpSuggestionId | LocalDriftSuggestion,
  ) => {
    const question = rawQuestion.trim();
    if (!question || !driftSession) return;
    const sessionVersion = driftSessionVersionRef.current;
    const id = clientId("drift");
    const { run } = driftSession;
    const logs = alarmLogs(run);
    if (["when", "what_unknown_receipt", "customer_impact_receipt"].includes(suggestionId ?? "")) {
      const presentation = run.localized_presentations[locale].business_response;
      const answer = suggestionId === "when"
        ? copy.timeAnswer(
          logs.map((log) => clock(log.observed_at)).join(", "),
          serviceLabel(
            run.alarm_receipt?.service ?? logs[0]?.content.service ?? "backend",
            locale,
          ),
        )
        : suggestionId === "what_unknown_receipt"
          ? presentation.what_remains_unknown.join(" ")
          : presentation.impact;
      setDriftTurns((current) => [...current, {
        id,
        question,
        response: null,
        localAnswer: answer,
        pending: false,
        error: null,
      }]);
      return;
    }
    const turn: DriftTurn = {
      id,
      question,
      response: null,
      localAnswer: null,
      pending: true,
      error: null,
    };
    setDriftTurns((current) => [...current, turn]);
    if (driftSession.mode === "recorded_replay" && !suggestionId) {
      setDriftTurns((current) => current.map((item) => item.id === id
        ? {
          ...item,
          pending: false,
          error: localized(locale, "Fri chatt kräver Live-AI. Välj en av frågorna nedan i replayläget.", "Free chat requires Live AI. Choose one of the questions below in replay mode."),
        }
        : item));
      return;
    }
    if (!driftSession.runReference) {
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, pending: false, error: copy.noRunReference }
        : item));
      return;
    }
    try {
      const response = await runIncidentLabFollowUp({
        run_reference: driftSession.runReference,
        client_turn_id: id,
        question,
        suggestion_id: suggestionId,
        locale,
        confirm_live_ai: driftSession.mode === "live_ai",
      });
      if (driftSessionVersionRef.current !== sessionVersion) return;
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, response, pending: false }
        : item));
    } catch (error) {
      if (driftSessionVersionRef.current !== sessionVersion) return;
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, pending: false, error: apiErrorText(error, locale) }
        : item));
    }
  }, [copy, driftSession, locale]);

  const currentDocument = useMemo(() => {
    if (!documents) return null;
    if (selectedDocument) {
      return documents.documents.find((item) => item.id === selectedDocument.documentId) ?? null;
    }
    return documents.documents.find((item) => item.content_visible) ?? documents.documents[0] ?? null;
  }, [documents, selectedDocument]);

  const driftFamilies = useMemo(
    () => capabilities?.incident_lab.incident_families
      .filter((family) => EXPECTED_DRIFT_FAMILIES.includes(family.id))
      .sort((left, right) => EXPECTED_DRIFT_FAMILIES.indexOf(left.id) - EXPECTED_DRIFT_FAMILIES.indexOf(right.id))
      ?? [],
    [capabilities],
  );

  return (
    <MotionConfig reducedMotion="user" transition={{ duration: 0.26, ease: [0.16, 1, 0.3, 1] }}>
      <div className="nordly-canvas">
        <div className={`nordly-app${evidence ? " nordly-app--sheet-open" : ""}`}>
          <Header
            mode={mode}
            locale={locale}
            setLocale={setLocale}
            setMode={setMode}
          />

          <main className="nordly-main">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={mode}
                className="nordly-scene"
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.2 }}
              >
                {mode === "support" ? (
                  <SupportAgentView
                    locale={locale}
                    liveAiStatus={liveAiStatus}
                    liveAiStatusResolved={liveAiStatusResolved}
                    turns={supportTurns}
                    draft={supportDraft}
                    pendingId={supportPendingId}
                    setDraft={setSupportDraft}
                    submit={submitSupport}
                    cancel={cancelSupportTurn}
                    resetConversation={resetSupportConversation}
                    refreshLiveAiStatus={() => refreshLiveAiStatus()}
                    openEvidence={(response) => openEvidence({ kind: "support", response })}
                    openDocument={openDocument}
                  />
                ) : mode === "drift" ? (
                  <DriftAgentView
                    locale={locale}
                    liveAiStatus={liveAiStatus}
                    session={driftSession}
                    sessionState={driftState}
                    failureMode={driftFailureMode}
                    error={driftError}
                    playbackStage={playbackStage}
                    turns={driftTurns}
                    documents={documents}
                    incidentFamilies={driftFamilies}
                    startLiveInvestigation={startLiveInvestigation}
                    startReplay={startReplay}
                    resetConversation={resetDriftConversation}
                    refreshLiveAiStatus={() => refreshLiveAiStatus()}
                    submit={submitDrift}
                    openEvidence={(focus) => driftSession && openEvidence({ kind: "drift", session: driftSession, focus })}
                    openDocument={(source) => openDocument(source, "drift")}
                  />
                ) : (
                  <DocumentArchiveView
                    locale={locale}
                    library={documents}
                    error={documentError}
                    current={currentDocument}
                    selection={selectedDocument}
                    select={(documentId) => setSelectedDocument({ documentId, chunkId: null, returnMode: "documents" })}
                    goBack={() => {
                      if (selectedDocument?.returnMode && selectedDocument.returnMode !== "documents") {
                        setMode(selectedDocument.returnMode);
                      }
                      setSelectedDocument(null);
                    }}
                  />
                )}
              </motion.div>
            </AnimatePresence>
          </main>

          <footer className="nordly-footer">
            <span>{copy.footer}</span>
            <span>{copy.synthetic}</span>
          </footer>

          <MobileNavigation mode={mode} setMode={setMode} locale={locale} />

          <AnimatePresence>
            {evidence ? (
              <EvidenceSheet
                state={evidence}
                locale={locale}
                close={closeEvidence}
                openDocument={openDocument}
              />
            ) : null}
          </AnimatePresence>
        </div>
      </div>
    </MotionConfig>
  );
}

function Header({
  mode,
  locale,
  setLocale,
  setMode,
}: {
  mode: Mode;
  locale: Locale;
  setLocale: (locale: Locale) => void;
  setMode: (mode: Mode) => void;
}) {
  const copy = COPY[locale];
  return (
    <header className="nordly-header">
      <button className="nordly-brand" type="button" onClick={() => setMode("support")} aria-label="Nordly">
        <NordlySignal className="nordly-brand__mark" />
        <span>Nordly</span>
      </button>
      <LayoutGroup id="desktop-mode-navigation">
        <nav className="nordly-tabs" aria-label={localized(locale, "Välj agent", "Choose agent")}>
          {(["drift", "support", "documents"] as const).map((item) => (
            <button
              key={item}
              type="button"
              className={mode === item ? "is-active" : ""}
              onClick={() => setMode(item)}
              aria-current={mode === item ? "page" : undefined}
            >
              {mode === item ? <motion.span className="nordly-tabs__active" layoutId="desktop-active-mode" /> : null}
              <span>{copy.modes[item]}</span>
            </button>
          ))}
        </nav>
      </LayoutGroup>
      <div className="nordly-language" aria-label={localized(locale, "Språk", "Language")}>
        <button type="button" className={locale === "sv" ? "is-active" : ""} onClick={() => setLocale("sv")} aria-pressed={locale === "sv"}>SV</button>
        <span>/</span>
        <button type="button" className={locale === "en" ? "is-active" : ""} onClick={() => setLocale("en")} aria-pressed={locale === "en"}>EN</button>
      </div>
    </header>
  );
}

function MobileNavigation({ mode, setMode, locale }: { mode: Mode; setMode: (mode: Mode) => void; locale: Locale }) {
  const labels = locale === "sv"
    ? { drift: "Drift", support: "Support", documents: "Arkiv" }
    : { drift: "Operations", support: "Support", documents: "Archive" };
  return (
    <LayoutGroup id="mobile-mode-navigation">
      <nav className="nordly-mobile-nav" aria-label={localized(locale, "Huvudnavigation", "Main navigation")}>
        {(["drift", "support", "documents"] as const).map((item) => (
          <button
            key={item}
            type="button"
            className={mode === item ? "is-active" : ""}
            onClick={() => setMode(item)}
            aria-current={mode === item ? "page" : undefined}
          >
            {mode === item ? <motion.span className="nordly-mobile-nav__active" layoutId="mobile-active-mode" /> : null}
            {item === "drift" ? <NordlySignal /> : item === "support" ? <ChatIcon /> : <DocumentIcon />}
            <span>{labels[item]}</span>
          </button>
        ))}
      </nav>
    </LayoutGroup>
  );
}

function AgentIdentity({
  kind,
  locale,
  badge,
  onBadgeClick,
}: {
  kind: "drift" | "support";
  locale: Locale;
  badge?: AgentBadge;
  onBadgeClick?: () => void;
}) {
  const copy = COPY[locale];
  return (
    <div className="agent-identity">
      <span className="agent-identity__mark"><NordlySignal /></span>
      <span className="agent-identity__text">
        <h1>{copy.modes[kind]}</h1>
        <small>{kind === "drift" ? copy.driftIdentity : copy.supportIdentity}</small>
      </span>
      {badge ? onBadgeClick ? (
        <button type="button" className={`agent-identity__badge agent-identity__badge--${badge.tone}`} onClick={onBadgeClick}>
          <i />{badge.label}<ChevronRightIcon />
        </button>
      ) : (
        <span className={`agent-identity__badge agent-identity__badge--${badge.tone}`}><i />{badge.label}</span>
      ) : null}
    </div>
  );
}

function AgentIdleState({
  title,
  body,
  actions,
  footnote,
}: {
  title: string;
  body: string;
  actions: ReactNode;
  footnote: string;
}) {
  return (
    <motion.div className="agent-idle-state" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
      <span className="agent-idle-state__signal"><NordlySignal /></span>
      <h2>{title}</h2>
      <p>{body}</p>
      <div className="agent-idle-state__actions">{actions}</div>
      <small><CheckIcon />{footnote}</small>
    </motion.div>
  );
}

function EndConversationButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button type="button" className="end-conversation-button" onClick={onClick}>
      <CloseIcon />
      <span>{label}</span>
    </button>
  );
}

function SupportAgentView({
  locale,
  liveAiStatus,
  liveAiStatusResolved,
  turns,
  draft,
  pendingId,
  setDraft,
  submit,
  cancel,
  resetConversation,
  refreshLiveAiStatus,
  openEvidence,
  openDocument,
}: {
  locale: Locale;
  liveAiStatus: LiveAiStatusResponse | null;
  liveAiStatusResolved: boolean;
  turns: CustomerChatTurn[];
  draft: string;
  pendingId: string | null;
  setDraft: (value: string) => void;
  submit: (message: string, confirm?: boolean, existingId?: string) => Promise<void>;
  cancel: (id: string) => void;
  resetConversation: () => void;
  refreshLiveAiStatus: () => Promise<void>;
  openEvidence: (response: DemoCustomerChatTurnResponse) => void;
  openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void;
}) {
  const copy = COPY[locale];
  const bottomRef = useRef<HTMLDivElement>(null);
  const [slow, setSlow] = useState(false);
  const [offlineReplay, setOfflineReplay] = useState<SupportReplayBundle | null>(null);
  const [offlineReplayState, setOfflineReplayState] = useState<"idle" | "requesting" | "playing" | "ready" | "error">("idle");
  const [offlineReplayStage, setOfflineReplayStage] = useState(0);
  const offlineReplayRequestRef = useRef(0);
  const liveAvailable = liveAiStatusResolved && liveAiStatus?.live_state === "available";
  const offline = liveAiStatusResolved && !liveAvailable;
  const replayAvailable = liveAiStatus?.replay_available === true;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [turns.length, pendingId, offlineReplayStage, offlineReplayState]);

  useEffect(() => {
    if (!pendingId) {
      setSlow(false);
      return;
    }
    const timer = window.setTimeout(() => setSlow(true), 6000);
    return () => window.clearTimeout(timer);
  }, [pendingId]);

  useEffect(() => {
    if (offlineReplayState !== "playing") return;
    if (offlineReplayStage >= 6) {
      setOfflineReplayState("ready");
      return;
    }
    const delay = SUPPORT_REPLAY_STEP_DELAYS[offlineReplayStage] ?? 1100;
    const timer = window.setTimeout(() => setOfflineReplayStage((current) => current + 1), delay);
    return () => window.clearTimeout(timer);
  }, [offlineReplayStage, offlineReplayState]);

  const startOfflineReplay = useCallback(async () => {
    if (offlineReplayState === "requesting") return;
    const requestVersion = offlineReplayRequestRef.current + 1;
    offlineReplayRequestRef.current = requestVersion;
    setOfflineReplay(null);
    setOfflineReplayStage(0);
    setOfflineReplayState("requesting");
    try {
      const [policy, boundary, orderLookup] = await Promise.all([
        runKnowledgeReplay("refund-timing"),
        runKnowledgeReplay("refund-customer-action"),
        getDemoOrder("NORD-2051"),
      ]);
      if (offlineReplayRequestRef.current !== requestVersion) return;
      setOfflineReplay({ policy, boundary, order: orderLookup.order });
      setOfflineReplayStage(1);
      setOfflineReplayState("playing");
    } catch {
      if (offlineReplayRequestRef.current !== requestVersion) return;
      setOfflineReplayState("error");
    }
  }, [offlineReplayState]);

  const endConversation = useCallback(() => {
    offlineReplayRequestRef.current += 1;
    setOfflineReplay(null);
    setOfflineReplayStage(0);
    setOfflineReplayState("idle");
    setSlow(false);
    resetConversation();
  }, [resetConversation]);

  const replaySources = useMemo<DemoCustomerChatSource[]>(() => (
    offlineReplay?.policy.retrieval.ranked_matches.map((match) => ({
      kind: "company_policy",
      document_id: match.document_id,
      chunk_id: match.chunk_id,
      document_version: match.document_version,
      title: match.title,
      section_heading: match.section_heading,
      source_ref: match.source_ref,
      evidence_id: match.evidence_id,
      lifecycle: match.status,
      similarity: match.similarity,
      display_summary_sv: match.display_summary_sv,
      display_summary_en: match.display_summary_en,
    })) ?? []
  ), [offlineReplay]);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!draft.trim() || pendingId) return;
    const message = draft;
    setDraft("");
    void submit(message);
  };

  const replayActive = ["playing", "ready"].includes(offlineReplayState);
  const conversationActive = (liveAvailable && turns.length > 0) || replayActive;
  const showOfflineStart = offline && ["idle", "error"].includes(offlineReplayState);
  const showConversation = liveAvailable || (offline && ["playing", "ready"].includes(offlineReplayState));
  const replayExplicitlyUnavailable = liveAiStatus?.replay_available === false;
  const supportReplayLabel = replayExplicitlyUnavailable
    ? copy.replayNotAvailable
    : offlineReplayState === "error"
      ? localized(locale, "Försök med replay igen", "Try replay again")
      : copy.startReplay;

  return (
    <section className="chat-stage" aria-label={copy.modes.support}>
      <div className="chat-column">
        <div className="agent-session-header">
          <AgentIdentity kind="support" locale={locale} />
          {conversationActive ? (
            <EndConversationButton label={copy.endConversation} onClick={endConversation} />
          ) : null}
        </div>
        <div className={`chat-thread${!showConversation ? " chat-thread--offline" : ""}`} role="log" aria-live="polite" aria-relevant="additions text" aria-busy={Boolean(pendingId) || !liveAiStatusResolved || offlineReplayState === "requesting" || offlineReplayState === "playing"}>
          {liveAvailable ? <MessageBubble side="assistant">
            <p>{copy.greeting}</p>
          </MessageBubble> : null}

          {liveAvailable ? turns.map((turn) => (
            <SupportTurn
              key={turn.clientId}
              turn={turn}
              locale={locale}
              slow={slow && pendingId === turn.clientId}
              onConfirm={() => void submit(turn.question, true, turn.clientId)}
              onCancel={() => cancel(turn.clientId)}
              openEvidence={openEvidence}
              openDocument={openDocument}
            />
          )) : null}

          {!liveAiStatusResolved ? (
            <div className="offline-replay-loading" role="status">
              <TypingIndicator label={localized(locale, "Kontrollerar Live-AI…", "Checking Live AI…")} />
            </div>
          ) : null}

          {showOfflineStart ? (
            <AgentIdleState
              title={localized(locale, "AI:n är offline just nu.", "AI is offline right now.")}
              body={localized(
                locale,
                replayExplicitlyUnavailable
                  ? "Varken Live-AI eller replay är tillgänglig just nu. Kontrollera igen om en stund."
                  : offlineReplayState === "error"
                  ? "Replayen kunde inte hämtas. Du kan försöka igen eller kontrollera om AI:n är tillbaka."
                  : "Ett verifierat syntetiskt supportfall från backend. Replayen gör inga nya AI-anrop.",
                replayExplicitlyUnavailable
                  ? "Neither Live AI nor replay is available right now. Check again shortly."
                  : offlineReplayState === "error"
                  ? "The replay could not be loaded. Try again or check whether AI is back."
                  : "A verified synthetic support case from the backend. The replay makes no new AI calls.",
              )}
              actions={<>
                <button type="button" className="button-primary" onClick={() => void startOfflineReplay()} disabled={!replayAvailable}>
                  <NordlySignal />{supportReplayLabel}
                </button>
                <button type="button" className="button-secondary" onClick={() => void refreshLiveAiStatus()}>
                  <RefreshIcon />{localized(locale, "Kontrollera AI igen", "Check AI again")}
                </button>
              </>}
              footnote={replayExplicitlyUnavailable
                ? localized(locale, "Backend rapporterar att replay saknas", "Backend reports that replay is unavailable")
                : localized(locale, "Verifierad syntetisk supportreplay · inga nya AI-anrop", "Verified synthetic support replay · no new AI calls")}
            />
          ) : null}

          {offline && offlineReplayState === "requesting" ? (
            <div className="offline-replay-loading" role="status">
              <TypingIndicator label={localized(locale, "Hämtar verifierad replay från backend…", "Loading verified replay from the backend…")} />
            </div>
          ) : null}

          {offline && offlineReplay ? (
            <AnimatePresence initial={false}>
              {offlineReplayStage >= 1 ? (
                <MessageBubble key="offline-policy-question" side="user">
                  <p>{locale === "sv" ? offlineReplay.policy.question.prompt_sv : offlineReplay.policy.question.prompt_en}</p>
                </MessageBubble>
              ) : null}
              {offlineReplayStage === 2 ? (
                <MessageBubble key="offline-policy-typing" side="assistant" tone="pending"><TypingIndicator label={copy.waiting} /></MessageBubble>
              ) : null}
              {offlineReplayStage >= 3 ? (
                <MessageBubble key="offline-policy-answer" side="assistant">
                  <p>{locale === "sv" ? offlineReplay.policy.answer.summary_sv : offlineReplay.policy.answer.summary_en}</p>
                  <ReplayOrderStrip order={offlineReplay.order} locale={locale} />
                  <SourceChips sources={replaySources} locale={locale} onOpen={openDocument} />
                  <div className="verified-row"><CheckIcon />{localized(locale, "Verifierad inspelning · inga nya AI-anrop", "Verified recording · no new AI calls")}</div>
                </MessageBubble>
              ) : null}
              {offlineReplayStage >= 4 ? (
                <MessageBubble key="offline-boundary-question" side="user">
                  <p>{locale === "sv" ? offlineReplay.boundary.question.prompt_sv : offlineReplay.boundary.question.prompt_en}</p>
                </MessageBubble>
              ) : null}
              {offlineReplayStage === 5 ? (
                <MessageBubble key="offline-boundary-typing" side="assistant" tone="pending"><TypingIndicator label={copy.waiting} /></MessageBubble>
              ) : null}
              {offlineReplayStage >= 6 ? (
                <MessageBubble key="offline-boundary-answer" side="assistant">
                  <p>{locale === "sv" ? offlineReplay.boundary.answer.summary_sv : offlineReplay.boundary.answer.summary_en}</p>
                  <div className="protected-inline"><ShieldIcon /><span>{localized(locale, "Read-only · agenten gjorde ingen ändring", "Read only · the agent made no change")}</span></div>
                  <div className="offline-replay-actions">
                    <button type="button" className="button-secondary" onClick={() => void startOfflineReplay()}><RefreshIcon />{localized(locale, "Spela igen", "Play again")}</button>
                  </div>
                </MessageBubble>
              ) : null}
            </AnimatePresence>
          ) : null}
          <div ref={bottomRef} />
        </div>

        {liveAvailable ? <div className="chat-composer-wrap">
          {turns.length === 0 ? (
            <div className="suggestion-row" aria-label={localized(locale, "Exempelfrågor", "Example questions")}>
              {copy.supportSuggestions.map((suggestion) => (
                <button key={suggestion} type="button" onClick={() => void submit(suggestion)} disabled={Boolean(pendingId)}>{suggestion}</button>
              ))}
            </div>
          ) : null}
          <form className="chat-composer" onSubmit={onSubmit}>
            <label className="sr-only" htmlFor="support-message">{copy.supportPlaceholder}</label>
              <input
                id="support-message"
                name="support-message"
                autoComplete="off"
                value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={copy.supportPlaceholder}
              maxLength={500}
              disabled={Boolean(pendingId)}
            />
            <button type="submit" disabled={!draft.trim() || Boolean(pendingId)} aria-label={copy.send}><SendIcon /></button>
          </form>
        </div> : null}
      </div>
    </section>
  );
}

function SupportTurn({
  turn,
  locale,
  slow,
  onConfirm,
  onCancel,
  openEvidence,
  openDocument,
}: {
  turn: CustomerChatTurn;
  locale: Locale;
  slow: boolean;
  onConfirm: () => void;
  onCancel: () => void;
  openEvidence: (response: DemoCustomerChatTurnResponse) => void;
  openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void;
}) {
  const copy = COPY[locale];
  const response = turn.response;
  const text = response
    ? locale === "sv" ? response.assistant_message.text_sv : response.assistant_message.text_en
    : null;
  return (
    <motion.div className="chat-turn" layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
      <MessageBubble side="user"><p>{turn.question}</p></MessageBubble>
      {turn.errorCode ? (
        <MessageBubble side="assistant" tone="error">
          <p>{turn.errorCode}</p>
        </MessageBubble>
      ) : !response ? (
        <MessageBubble side="assistant" tone="pending">
          <TypingIndicator label={slow ? copy.slow : copy.waiting} />
        </MessageBubble>
      ) : response.outcome === "confirmation_required" ? (
        <MessageBubble side="assistant" className="confirmation-bubble">
          <div className="confirmation-card">
            <span className="confirmation-card__icon"><DocumentIcon /></span>
            <div>
              <strong>{copy.confirmTitle}</strong>
              <p>{copy.confirmBody}</p>
            </div>
            <div className="confirmation-card__safe"><CheckIcon />{copy.noProviderYet}</div>
            <div className="confirmation-card__actions">
              <button type="button" className="button-secondary" onClick={onCancel}>{copy.cancel}</button>
              <button type="button" className="button-primary" onClick={onConfirm}>{copy.searchDocuments}</button>
            </div>
          </div>
        </MessageBubble>
      ) : (
        <MessageBubble side="assistant">
          <p>{text}</p>
          {response.order ? <OrderStrip response={response} locale={locale} /> : null}
          {response.sources.length > 0 ? (
            <SourceChips sources={response.sources} locale={locale} onOpen={openDocument} />
          ) : null}
          {response.outcome === "refused" ? (
            <div className="protected-inline"><LockIcon /><span>{localized(locale, "Privat innehåll öppnades inte", "Private content was not opened")}</span></div>
          ) : null}
          <button type="button" className="disclosure-row" onClick={() => openEvidence(response)}>
            <span>{copy.behindAnswer}</span><ChevronRightIcon />
          </button>
          <div className="verified-row"><CheckIcon />{supportOutcomeLabel(response, locale)}</div>
        </MessageBubble>
      )}
    </motion.div>
  );
}

function OrderStrip({ response, locale }: { response: DemoCustomerChatTurnResponse; locale: Locale }) {
  const order = response.order;
  if (!order) return null;
  return (
    <div className="order-strip">
      <BoxIcon />
      <span className="order-strip__copy">
        <strong>{locale === "sv" ? order.item_summary_sv : order.item_summary_en}</strong>
        <small>
          {order.order_id} · {locale === "sv" ? order.status_sv : order.status_en} · {shortDate(order.estimated_delivery_from, locale)}–{shortDate(order.estimated_delivery_through, locale)}
        </small>
      </span>
      <em>{order.item_count} {localized(locale, order.item_count === 1 ? "vara" : "varor", order.item_count === 1 ? "item" : "items")}</em>
    </div>
  );
}

function ReplayOrderStrip({ order, locale }: { order: DemoOrder; locale: Locale }) {
  return (
    <div className="order-strip">
      <BoxIcon />
      <span className="order-strip__copy">
        <strong>{locale === "sv" ? order.item_summary_sv : order.item_summary_en}</strong>
        <small>
          {order.order_id} · {locale === "sv" ? order.status_sv : order.status_en} · {shortDate(order.estimated_delivery_from, locale)}–{shortDate(order.estimated_delivery_through, locale)}
        </small>
      </span>
      <em>{order.item_count} {localized(locale, order.item_count === 1 ? "vara" : "varor", order.item_count === 1 ? "item" : "items")}</em>
    </div>
  );
}

function SourceChips({
  sources,
  locale,
  onOpen,
  returnMode = "support",
}: {
  sources: DemoCustomerChatSource[];
  locale: Locale;
  onOpen: (source: DemoCustomerChatSource, returnMode?: Mode) => void;
  returnMode?: Mode;
}) {
  return (
    <div className="source-chips">
      {sources.map((source) => (
        <button
          key={source.evidence_id}
          type="button"
          onClick={() => onOpen(source, returnMode)}
          disabled={!source.document_id}
        >
          <DocumentIcon />
          <span>{source.title}</span>
          {source.document_id ? <ChevronRightIcon /> : null}
        </button>
      ))}
    </div>
  );
}

function DriftAgentView({
  locale,
  liveAiStatus,
  session,
  sessionState,
  failureMode,
  error,
  playbackStage,
  turns,
  documents,
  incidentFamilies,
  startLiveInvestigation,
  startReplay,
  resetConversation,
  refreshLiveAiStatus,
  submit,
  openEvidence,
  openDocument,
}: {
  locale: Locale;
  liveAiStatus: LiveAiStatusResponse | null;
  session: DriftSession | null;
  sessionState: "idle" | "requesting" | "playing" | "ready" | "error";
  failureMode: DriftSession["mode"] | null;
  error: string | null;
  playbackStage: number;
  turns: DriftTurn[];
  documents: KnowledgeDocumentLibraryResponse | null;
  incidentFamilies: IncidentFamilyCapability[];
  startLiveInvestigation: (family: IncidentFamilyCapability | null) => Promise<void>;
  startReplay: () => Promise<void>;
  resetConversation: () => void;
  refreshLiveAiStatus: () => Promise<void>;
  submit: (question: string, suggestionId?: IncidentLabFollowUpSuggestionId | LocalDriftSuggestion) => Promise<void>;
  openEvidence: (focus?: Pick<IncidentLabFollowUpCitation, "target_scene" | "target_id">) => void;
  openDocument: (source: DemoCustomerChatSource) => void;
}) {
  const copy = COPY[locale];
  const bottomRef = useRef<HTMLDivElement>(null);
  const [draft, setDraft] = useState("");
  const [slow, setSlow] = useState(false);
  const [selectedFamily, setSelectedFamily] = useState<IncidentFamilyCapability | null>(null);
  const run = session?.run ?? null;
  const logs = alarmLogs(run);
  const alarm = run?.alarm_receipt;
  const hasAlarm = Boolean(alarm);
  const count = Number(alarm?.signal.observed_value ?? logs.length);
  const service = alarm?.service ?? logs[0]?.content.service ?? "backend";
  const times = logs.map((log) => clock(log.observed_at));
  const presentation = run?.localized_presentations[locale].business_response;
  const activeFamily = incidentFamilies.find((family) => family.id === run?.plan?.incident_family)
    ?? selectedFamily;
  const ready = sessionState === "ready";
  const pending = turns.some((turn) => turn.pending);
  const liveChat = session?.mode === "live_ai";
  const canChat = ready && liveChat && Boolean(session?.runReference) && !pending;
  const runbooks = run?.agent_turn?.tool_events
    .flatMap((event) => event.evidence)
    .filter((item): item is RunbookEvidence => item.evidence_type === "runbook") ?? [];
  const runbookSources = useMemo(
    () => {
      const unique = new Map<string, DemoCustomerChatSource>();
      runbooks.forEach((runbook) => {
        const source = runbookDocumentSource(runbook, documents, locale);
        if (!source?.document_id) return;
        const current = unique.get(source.document_id);
        if (!current || (!current.chunk_id && source.chunk_id)) unique.set(source.document_id, source);
      });
      return [...unique.values()];
    },
    [documents, locale, runbooks],
  );
  const liveAvailable = liveAiStatus?.live_state === "available";
  const replayAvailable = liveAiStatus?.replay_available === true;
  const replayExplicitlyUnavailable = liveAiStatus?.replay_available === false;
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [playbackStage, sessionState, turns.length]);
  useEffect(() => {
    if (!pending) {
      setSlow(false);
      return;
    }
    const timer = window.setTimeout(() => setSlow(true), 6000);
    return () => window.clearTimeout(timer);
  }, [pending]);
  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!canChat || !draft.trim()) return;
    const question = draft;
    setDraft("");
    void submit(question);
  };
  const endConversation = useCallback(() => {
    setDraft("");
    setSelectedFamily(null);
    resetConversation();
  }, [resetConversation]);
  const openCitation = useCallback((citation: IncidentLabFollowUpCitation) => {
    const runbook = runbooks.find((item) => item.evidence_id === citation.evidence_id);
    const source = runbook ? runbookDocumentSource(runbook, documents, locale) : null;
    if (source) {
      openDocument(source);
      return;
    }
    openEvidence(citation);
  }, [documents, locale, openDocument, openEvidence, runbooks]);
  const reportActive = ["requesting", "playing", "ready"].includes(sessionState);
  const timeRange = times.length > 0
    ? `${times[0]}–${times.at(-1)}`
    : localized(locale, "Tid registrerad i larmkvittot", "Time recorded in the alert receipt");
  const diagnosed = run?.answer_state === "diagnosed";
  const reportHeadline = diagnosed
    ? presentation?.headline || copy.strongestFactor
    : copy.withheldTitle;
  const reportBody = (diagnosed ? presentation?.what_is_known?.at(-1) : null)
    || presentation?.what_is_known?.join(" ")
    || presentation?.what_remains_unknown?.join(" ")
    || copy.withheldBody;
  const uncertainty = presentation?.what_remains_unknown?.join(" ") || copy.withheldBody;
  return (
    <section className="chat-stage" aria-label={copy.modes.drift}>
      <div className="chat-column">
        <div className="agent-session-header">
          <AgentIdentity kind="drift" locale={locale} />
          {reportActive ? <EndConversationButton label={copy.endConversation} onClick={endConversation} /> : null}
        </div>
        <div className="chat-thread chat-thread--drift" role="log" aria-live="polite" aria-relevant="additions text" aria-busy={sessionState === "requesting" || sessionState === "playing" || pending}>
          {sessionState === "idle" ? (
            liveAvailable ? (
              <motion.div className="drift-case-start" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                <MessageBubble side="assistant" className="drift-case-start__intro">
                  <p className="message-emphasis">{copy.driftGreeting}</p>
                  <p>{copy.driftIdleTitle}</p>
                  <p>{copy.driftIdleBody}</p>
                </MessageBubble>
                <div className="incident-family-list" role="group" aria-label={copy.driftIdleTitle}>
                  {incidentFamilies.length > 0 ? incidentFamilies.map((family, index) => (
                    <motion.button
                      key={family.id}
                      type="button"
                      className="incident-family-option"
                      aria-label={copy.driftCasePrompt(family.customer_impact[locale])}
                      onClick={() => {
                        setSelectedFamily(family);
                        void startLiveInvestigation(family);
                      }}
                      initial={{ opacity: 0, y: 6 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: 0.04 * index }}
                    >
                      <span className="incident-family-option__icon"><AlertIcon /></span>
                      <span className="incident-family-option__copy">
                        <strong>{family.customer_impact[locale]}</strong>
                        <small>{family.label[locale]}</small>
                      </span>
                      <ChevronRightIcon />
                    </motion.button>
                  )) : (
                    <button
                      type="button"
                      className="incident-family-option"
                      aria-label={copy.startDriftAlarm}
                      onClick={() => {
                        setSelectedFamily(null);
                        void startLiveInvestigation(null);
                      }}
                    >
                      <span className="incident-family-option__icon"><AlertIcon /></span>
                      <span className="incident-family-option__copy">
                        <strong>{copy.startDriftAlarm}</strong>
                        <small>{copy.driftCasesLoading}</small>
                      </span>
                      <ChevronRightIcon />
                    </button>
                  )}
                </div>
                <div className="drift-case-start__footer">
                  <span><CheckIcon />{copy.liveTruth}</span>
                  {replayAvailable ? <button type="button" onClick={() => void startReplay()}>{copy.startReplay}</button> : null}
                </div>
              </motion.div>
            ) : (
              <AgentIdleState
                title={copy.driftIdleTitle}
                body={copy.driftIdleBody}
                actions={<>
                  <button type="button" className="button-primary" onClick={() => void startReplay()} disabled={!replayAvailable}>
                    {replayExplicitlyUnavailable ? localized(locale, "Replay är inte tillgänglig", "Replay unavailable") : copy.startReplay}
                  </button>
                  <button type="button" className="button-secondary" onClick={() => void refreshLiveAiStatus()}>
                    <RefreshIcon />{localized(locale, "Kontrollera AI igen", "Check AI again")}
                  </button>
                </>}
                footnote={copy.replayTruth}
              />
            )
          ) : sessionState === "requesting" ? (
            <div className="drift-requesting" role="status">
              <MessageBubble side="user">
                <p>{selectedFamily ? copy.driftCasePrompt(selectedFamily.customer_impact[locale]) : copy.driftGenericPrompt}</p>
              </MessageBubble>
              <MessageBubble side="assistant" tone="pending">
                <TypingIndicator label={copy.receiptWaiting} />
              </MessageBubble>
            </div>
          ) : sessionState === "error" ? (
            <AgentIdleState
              title={failureMode === "live_ai"
                ? localized(locale, "Live-utredningen kunde inte slutföras.", "The live investigation could not be completed.")
                : copy.replayUnavailable}
              body={error ?? localized(locale, "Ingen automatisk omkörning gjordes.", "No automatic retry was made.")}
              actions={<>
                {failureMode === "live_ai" && liveAvailable ? (
                  <button type="button" className="button-primary" onClick={() => void startLiveInvestigation(selectedFamily)}><RefreshIcon />{copy.retry}</button>
                ) : null}
                {replayAvailable ? (
                  <button type="button" className="button-secondary" onClick={() => void startReplay()}>{copy.startReplay}</button>
                ) : (
                  <button type="button" className="button-secondary" onClick={() => void refreshLiveAiStatus()}><RefreshIcon />{localized(locale, "Kontrollera AI igen", "Check AI again")}</button>
                )}
              </>}
              footnote={localized(locale, "Inget svar ersattes automatiskt med replay.", "No answer was automatically replaced with a replay.")}
            />
          ) : session ? (
            <>
              <AnimatePresence initial={false}>
                {playbackStage >= 1 ? (
                  <MessageBubble key="selected-case" side="user">
                    <p>{activeFamily
                      ? copy.driftCasePrompt(activeFamily.customer_impact[locale])
                      : copy.driftCasePrompt(presentation?.impact ?? copy.driftGenericPrompt)}</p>
                  </MessageBubble>
                ) : null}
                {playbackStage >= 1 ? (
                  <MessageBubble key="alarm" side="assistant" tone={hasAlarm ? "alert" : "default"} timestamp={times.at(-1)}>
                    <p className="message-emphasis">{hasAlarm
                      ? copy.customerAffected
                      : localized(locale, "Körningen skapade inget larm.", "The run did not create an alert.")}</p>
                    <p>{presentation?.impact}</p>
                    {presentation?.what_happened ? (
                      <div className="incident-trigger">
                        <small>{copy.alarmTrigger}</small>
                        <span>{presentation.what_happened}</span>
                      </div>
                    ) : null}
                    {hasAlarm ? (
                      <div className="incident-strip">
                        <AlertIcon />
                        <strong>{alarmValueLabel(count, alarm?.signal.unit ?? "count", locale)}</strong>
                        <span>·</span>
                        <span>{timeRange}</span>
                        <span>·</span>
                        <span>{serviceLabel(service, locale)}</span>
                      </div>
                    ) : null}
                  </MessageBubble>
                ) : null}
                {playbackStage === 2 ? (
                  <MessageBubble key="investigating" side="assistant" tone="pending">
                    <TypingIndicator label={copy.receiptReady} />
                  </MessageBubble>
                ) : null}
                {playbackStage >= 3 ? (
                  <MessageBubble key="report" side="assistant">
                    <small className="message-kicker">{copy.humanReport}</small>
                    <p className="message-emphasis">{reportHeadline}</p>
                    <p>{reportBody}</p>
                    {presentation?.safe_next_step ? (
                      <p className="report-next-step"><strong>{copy.teamSuggestion}:</strong> {presentation.safe_next_step}</p>
                    ) : null}
                    <div className="report-uncertainty">
                      <strong>{copy.stillUncertain}</strong>
                      <span>{uncertainty}</span>
                    </div>
                    <div className="source-chips">
                      {logs.length > 0 ? (
                        <button type="button" onClick={() => openEvidence({ target_scene: "logs", target_id: logs[0].evidence_id })}>
                          <DocumentIcon />{logs.length} {localized(locale, "markerade loggar", "highlighted logs")}<ChevronRightIcon />
                        </button>
                      ) : null}
                    </div>
                    {runbookSources.length > 0 ? (
                      <SourceChips sources={runbookSources} locale={locale} onOpen={openDocument} returnMode="drift" />
                    ) : runbooks.length > 0 ? (
                      <div className="source-chips">
                        <button type="button" onClick={() => openEvidence({ target_scene: "agent_rag", target_id: runbooks[0].evidence_id })}>
                          <DocumentIcon />{localized(locale, "Driftinstruktioner", "Operating guides")}<ChevronRightIcon />
                        </button>
                      </div>
                    ) : null}
                    <button type="button" className="disclosure-row" onClick={() => openEvidence()}><span>{copy.behindReport}</span><ChevronRightIcon /></button>
                    <p className="report-closure">{copy.continueChat}</p>
                    <div className="verified-row"><CheckIcon />{session.mode === "live_ai"
                      ? localized(locale, "Liveanalys · syntetiskt fall · inget ändrat", "Live analysis · synthetic case · nothing changed")
                      : localized(locale, "Säkerhetsreplay · inget ändrat", "Safety replay · nothing changed")}</div>
                  </MessageBubble>
                ) : null}
              </AnimatePresence>

              {turns.map((turn) => (
                <motion.div key={turn.id} className="chat-turn" layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                  <MessageBubble side="user"><p>{turn.question}</p></MessageBubble>
                  <MessageBubble side="assistant" tone={turn.error ? "error" : "default"}>
                    {turn.pending ? <TypingIndicator label={slow ? copy.slow : copy.waiting} /> : null}
                    {turn.localAnswer ? <p>{turn.localAnswer}</p> : null}
                    {turn.localAnswer ? <div className="verified-row"><CheckIcon />{localized(locale, "Baserat på den här rapporten · inget ändrat", "Based on this report · nothing changed")}</div> : null}
                    {turn.response ? (
                      <>
                        <p>{turn.response.answer.text}</p>
                        {turn.response.citations.length > 0 ? (
                          <div className="source-chips">
                            {turn.response.citations.slice(0, 3).map((citation) => {
                              const documentSource = runbookSources.find((source) => source.evidence_id === citation.evidence_id);
                              return (
                                <button key={citation.evidence_id} type="button" onClick={() => openCitation(citation)}>
                                  <DocumentIcon />{documentSource?.title ?? citation.label}<ChevronRightIcon />
                                </button>
                              );
                            })}
                          </div>
                        ) : null}
                      </>
                    ) : null}
                    {turn.error ? <p>{turn.error}</p> : null}
                  </MessageBubble>
                </motion.div>
              ))}
              <div ref={bottomRef} />
            </>
          ) : null}
        </div>

        {ready && session ? (
          <div className="chat-composer-wrap">
            {turns.length === 0 ? (
              <div className="suggestion-row suggestion-row--drift">
                <button type="button" onClick={() => void submit(copy.whenQuestion, "when")} disabled={pending}>{copy.whenQuestion}</button>
                {session.runReference ? (["customer_impact", "how_conclusion", "what_unknown"] as const).map((id) => (
                  <button key={id} type="button" onClick={() => void submit(copy.driftSuggestions[id], id)} disabled={pending}>{copy.driftSuggestions[id]}</button>
                )) : (
                  <>
                    <button type="button" onClick={() => void submit(copy.driftSuggestions.what_unknown, "what_unknown_receipt")} disabled={pending}>{copy.driftSuggestions.what_unknown}</button>
                    <button type="button" onClick={() => void submit(copy.driftSuggestions.customer_impact, "customer_impact_receipt")} disabled={pending}>{copy.driftSuggestions.customer_impact}</button>
                  </>
                )}
              </div>
            ) : null}
            <form className="chat-composer" onSubmit={onSubmit} aria-label={localized(locale, "Fråga Driftagenten", "Ask the Operations agent")}>
              <label className="sr-only" htmlFor="drift-message">{copy.driftPlaceholder}</label>
              <input
                id="drift-message"
                name="drift-message"
                autoComplete="off"
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder={!session.runReference
                  ? copy.noRunReference
                  : liveChat
                    ? copy.driftPlaceholder
                    : copy.replayChatPlaceholder}
                maxLength={500}
                disabled={!canChat}
              />
              <button type="submit" disabled={!canChat || !draft.trim()} aria-label={copy.send}><SendIcon /></button>
            </form>
          </div>
        ) : null}
      </div>
    </section>
  );
}

function MessageBubble({
  side,
  tone = "default",
  timestamp,
  className = "",
  children,
}: {
  side: "assistant" | "user";
  tone?: "default" | "pending" | "error" | "protected" | "alert" | "withheld";
  timestamp?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <motion.div
      className={`message-row message-row--${side} ${className}`}
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.24 }}
    >
      <div className={`message-bubble message-bubble--${side} message-bubble--${tone}`}>{children}</div>
      {timestamp ? <time>{timestamp}</time> : null}
    </motion.div>
  );
}

function TypingIndicator({ label }: { label: string }) {
  return (
    <div className="typing-indicator" role="status">
      <span className="typing-indicator__dots" aria-hidden="true"><i /><i /><i /></span>
      {label ? <span>{label}</span> : null}
    </div>
  );
}

function DocumentArchiveView({
  locale,
  library,
  error,
  current,
  selection,
  select,
  goBack,
}: {
  locale: Locale;
  library: KnowledgeDocumentLibraryResponse | null;
  error: string | null;
  current: KnowledgeDocumentLibraryDocument | null;
  selection: SelectedDocument | null;
  select: (documentId: string) => void;
  goBack: () => void;
}) {
  const copy = COPY[locale];
  const [query, setQuery] = useState("");
  const normalized = query.trim().toLocaleLowerCase(locale === "sv" ? "sv-SE" : "en-US");
  const filtered = library?.documents.filter((document) => {
    if (!normalized) return true;
    return `${titleForDocument(document, locale)} ${locale === "sv" ? document.summary_sv : document.summary_en}`.toLocaleLowerCase().includes(normalized);
  }) ?? [];
  const fromChat = Boolean(selection && selection.returnMode !== "documents");
  const readerOpen = Boolean(selection);
  return (
    <section className={`archive-stage${readerOpen ? " archive-stage--reader-open" : ""}${fromChat ? " archive-stage--deep-link" : ""}`} aria-label={copy.archiveTitle}>
      <div className="archive-heading">
        <div>
          <h1 aria-label={copy.archiveTitle}>{copy.archiveTitle}</h1>
          <p>{copy.archiveLead}</p>
        </div>
        <label className="archive-search">
          <SearchIcon />
          <span className="sr-only">{copy.searchPlaceholder}</span>
          <input aria-label={copy.searchPlaceholder} value={query} onChange={(event) => setQuery(event.target.value)} placeholder={copy.searchPlaceholder} />
        </label>
      </div>

      {error ? <div className="archive-state archive-state--error"><AlertIcon /><p>{copy.archiveError}</p></div> : !library ? (
        <div className="archive-state"><TypingIndicator label={copy.archiveLoading} /></div>
      ) : (
        <div className="archive-layout">
          <div className="document-list">
            {filtered.length === 0 ? <p className="document-list__empty">{copy.noResults}</p> : filtered.map((document) => (
              <button
                key={document.id}
                type="button"
                className={current?.id === document.id ? "is-selected" : ""}
                onClick={() => select(document.id)}
                aria-label={`${titleForDocument(document, locale)}. ${locale === "sv" ? document.summary_sv : document.summary_en}`}
              >
                <span className={`document-list__icon${document.content_visible ? "" : " is-locked"}`}>
                  {document.content_visible ? <DocumentIcon /> : <LockIcon />}
                </span>
                <span className="document-list__copy">
                  <strong>{titleForDocument(document, locale)}</strong>
                  <small>{locale === "sv" ? document.summary_sv : document.summary_en}</small>
                </span>
                <span className="document-list__meta">
                  <small>{document.version}</small>
                  <small>{document.content_visible ? shortDate(document.effective_from, locale) : copy.protected}</small>
                </span>
                <ChevronRightIcon />
              </button>
            ))}
            <div className="document-list__footer">
              {library.eligible_document_count} {localized(locale, "godkända källor", "approved sources")} · {library.document_count - library.eligible_document_count} {localized(locale, "skyddade eller exkluderade", "protected or excluded")}
            </div>
          </div>
          <DocumentReader
            document={current}
            locale={locale}
            selection={selection}
            fromChat={fromChat}
            goBack={goBack}
          />
        </div>
      )}
    </section>
  );
}

function DocumentReader({
  document,
  locale,
  selection,
  fromChat,
  goBack,
}: {
  document: KnowledgeDocumentLibraryDocument | null;
  locale: Locale;
  selection: SelectedDocument | null;
  fromChat: boolean;
  goBack: () => void;
}) {
  const copy = COPY[locale];
  useEffect(() => {
    if (!selection?.chunkId || !document) return;
    const frame = window.requestAnimationFrame(() => {
      window.document.getElementById(`chunk-${selection.chunkId}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [document, selection?.chunkId]);
  if (!document) return <div className="document-reader document-reader--empty"><DocumentIcon /><p>{copy.chooseDocument}</p></div>;
  const backLabel = selection?.returnMode === "drift" ? copy.backToDrift : selection?.returnMode === "support" ? copy.backToSupport : copy.backToArchive;
  if (!document.content_visible) {
    return (
      <article className="document-reader document-reader--protected">
        <button type="button" className="document-reader__back" onClick={goBack}><ArrowLeftIcon />{backLabel}</button>
        <LockIcon className="document-reader__hero-icon" />
        <span className="eyebrow">{copy.protectedLabel}</span>
        <h2 aria-label={titleForDocument(document, locale)}>{titleForDocument(document, locale)}</h2>
        <p>{localized(locale, `Version ${document.version} · Endast ${document.owner_team}`, `Version ${document.version} · ${document.owner_team} only`)}</p>
        <div className="protected-card">
          <h3>{copy.protectedTitle}</h3>
          <p>{copy.protectedBody}</p>
          <ul>
            <li><CheckIcon />{copy.accessChecked}</li>
            <li><CheckIcon />{copy.noPassages}</li>
            <li><CheckIcon />{copy.noModelCalls}</li>
          </ul>
        </div>
      </article>
    );
  }
  return (
    <article className="document-reader">
      {selection ? <button type="button" className="document-reader__back" onClick={goBack}><ArrowLeftIcon />{backLabel}</button> : null}
      <header className="document-reader__header">
        <DocumentIcon />
        <div>
          <h2 aria-label={titleForDocument(document, locale)}>{titleForDocument(document, locale)}</h2>
          <p>{localized(locale, `Version ${document.version} · Uppdaterad ${shortDate(document.effective_from, locale)}`, `Version ${document.version} · Updated ${shortDate(document.effective_from, locale)}`)}</p>
        </div>
        <span>{fromChat ? copy.sourceInAnswer : copy.approvedSource}</span>
      </header>
      <div className="document-reader__body">
        {document.chunks.filter((chunk) => chunk.text !== null).map((chunk) => {
          const highlighted = selection?.chunkId === chunk.id;
          return (
            <section key={chunk.id} id={`chunk-${chunk.id}`} className={highlighted ? "is-highlighted" : ""}>
              {highlighted ? <span className="eyebrow">{copy.passageUsed}</span> : null}
              <h3 aria-label={chunk.section_heading}>{chunk.section_heading}</h3>
              <p>{chunk.text}</p>
            </section>
          );
        })}
      </div>
    </article>
  );
}

function EvidenceSheet({
  state,
  locale,
  close,
  openDocument,
}: {
  state: NonNullable<EvidenceState>;
  locale: Locale;
  close: () => void;
  openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void;
}) {
  const copy = COPY[locale];
  const closeRef = useRef<HTMLButtonElement>(null);
  const sheetRef = useRef<HTMLElement>(null);
  const isModal = useMediaQuery("(max-width: 1279px)");
  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
      if (event.key !== "Tab" || !isModal || !sheetRef.current) return;
      const focusable = Array.from(sheetRef.current.querySelectorAll<HTMLElement>(
        "button:not([disabled]), a[href], input:not([disabled]), summary, [tabindex]:not([tabindex='-1'])",
      ));
      const first = focusable[0];
      const last = focusable.at(-1);
      if (!first || !last) return;
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    const background = isModal
      ? Array.from(window.document.querySelectorAll<HTMLElement>(".nordly-header, .nordly-main, .nordly-footer, .nordly-mobile-nav"))
      : [];
    background.forEach((element) => {
      element.inert = true;
      element.setAttribute("aria-hidden", "true");
    });
    return () => {
      window.removeEventListener("keydown", onKey);
      background.forEach((element) => {
        element.inert = false;
        element.removeAttribute("aria-hidden");
      });
    };
  }, [close, isModal]);
  return (
    <>
      <motion.button className="evidence-backdrop" type="button" aria-label={copy.close} onClick={close} initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} />
      <motion.aside
        ref={sheetRef}
        className="evidence-sheet"
        role={isModal ? "dialog" : "complementary"}
        aria-modal={isModal ? "true" : undefined}
        aria-labelledby="evidence-title"
        initial={{ opacity: 0, x: 24 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: 18 }}
        transition={{ duration: 0.38 }}
      >
        <button ref={closeRef} type="button" className="evidence-sheet__close" onClick={close} aria-label={copy.close}><CloseIcon /></button>
        {state.kind === "support" ? (
          <SupportEvidence response={state.response} locale={locale} openDocument={openDocument} />
        ) : (
          <DriftEvidence
            key={`${state.focus?.target_scene ?? "overview"}-${state.focus?.target_id ?? "all"}`}
            session={state.session}
            locale={locale}
            focus={state.focus}
          />
        )}
      </motion.aside>
    </>
  );
}

function SupportEvidence({ response, locale, openDocument }: { response: DemoCustomerChatTurnResponse; locale: Locale; openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void }) {
  const copy = COPY[locale];
  const blocked = response.outcome === "refused" || response.safety.decision === "BLOCK";
  const blockedBeforeAi = blocked && response.receipt.provider_calls === 0;
  const handledWithinBoundary = blocked && !blockedBeforeAi;
  const executedSteps = response.tool_events.filter((event) => event.executed);
  return (
    <div className="evidence-content">
      <span className="eyebrow">{blocked ? copy.safetyReceipt : copy.evidenceEyebrow}</span>
      <h2 id="evidence-title">{blockedBeforeAi
        ? copy.stoppedBeforeAi
        : handledWithinBoundary
          ? localized(locale, "Hanterad inom säkerhetsgränsen", "Handled within the safety boundary")
          : copy.behindAnswer}</h2>
      <p className="evidence-content__lead">{copy.registeredNotThoughts}</p>
      <div className="evidence-timeline">
        {blockedBeforeAi ? (
          <>
            <EvidenceStep icon={<ShieldIcon />} title={localized(locale, "Frågan kontrollerades", "The question was checked")} body={locale === "sv" ? response.safety.summary_sv : response.safety.summary_en} />
            <EvidenceStep icon={<CheckIcon />} title={localized(locale, "Inget dokumentinnehåll lästes", "No document content was read")} body={`${response.receipt.read_operations} ${localized(locale, "läsningar", "reads")}`} />
            <EvidenceStep icon={<CheckIcon />} title={localized(locale, "Inget skickades till modellen", "Nothing was sent to the model")} body={`${response.receipt.provider_calls} ${localized(locale, "provideranrop", "provider calls")}`} />
          </>
        ) : handledWithinBoundary ? (
          <>
            <EvidenceStep icon={<ShieldIcon />} title={localized(locale, "Befogenheten kontrollerades", "Authority was checked")} body={locale === "sv" ? response.safety.summary_sv : response.safety.summary_en} />
            <EvidenceStep icon={<ChatIcon />} title={localized(locale, "Ett naturligt svar formulerades", "A natural response was composed")} body={`${response.receipt.provider_calls} ${localized(locale, "modell-anrop", "model call")}`} />
            <EvidenceStep icon={<CheckIcon />} title={localized(locale, "Ingen åtgärd utfördes", "No action was performed")} body={localized(locale, "Agenten höll sig inom företagets regler.", "The agent stayed within the company's rules.")} />
          </>
        ) : executedSteps.slice(0, 5).map((event) => (
          <EvidenceStep
            key={`${event.sequence}-${event.name}`}
            icon={event.type === "backend_read" ? <ToolIcon /> : event.type === "verification" ? <ShieldIcon /> : <DocumentIcon />}
            title={locale === "sv" ? event.summary_sv : event.summary_en}
            body={event.source_ref ?? localized(locale, "Registrerat backendsteg", "Registered backend step")}
          />
        ))}
      </div>
      {response.sources.length > 0 ? (
        <section className="evidence-section">
          <h3>{copy.sourcesAndEvidence}</h3>
          {response.sources.map((source) => (
            <button key={source.evidence_id} type="button" className="evidence-source" onClick={() => openDocument(source, "support")} disabled={!source.document_id}>
              <DocumentIcon /><span><strong>{source.title}</strong><small>{source.section_heading ?? (locale === "sv" ? source.display_summary_sv : source.display_summary_en)}</small></span>{source.document_id ? <ChevronRightIcon /> : null}
            </button>
          ))}
        </section>
      ) : null}
      <ReceiptRows rows={[
        [localized(locale, "Backendläsningar", "Backend reads"), String(response.receipt.read_operations)],
        [localized(locale, "Modellanrop", "Model calls"), String(response.receipt.provider_calls)],
        [localized(locale, "Dokumentpassager", "Document passages"), String(response.sources.filter((source) => source.kind === "company_policy" && source.chunk_id).length)],
        [localized(locale, "Estimerad kostnad", "Estimated cost"), response.receipt.estimated_cost_usd === null || response.receipt.estimated_cost_usd === undefined ? localized(locale, "Ej rapporterad", "Not reported") : `$${response.receipt.estimated_cost_usd.toFixed(8)}`],
        [localized(locale, "Ändringar", "Changes"), "0"],
      ]} />
      {blocked ? <div className="evidence-result evidence-result--safe"><CheckIcon /><strong>{blockedBeforeAi
        ? copy.safetyWon
        : localized(locale, "Företagets regler följdes · ingen åtgärd utfördes", "Company rules followed · no action performed")}</strong></div> : null}
    </div>
  );
}

function DriftEvidence({
  session,
  locale,
  focus,
}: {
  session: DriftSession;
  locale: Locale;
  focus?: Pick<IncidentLabFollowUpCitation, "target_scene" | "target_id">;
}) {
  const copy = COPY[locale];
  const run = session.run;
  const alarm = run.alarm_receipt;
  const agent = run.agent_turn;
  const evidence = agent?.tool_events.flatMap((event) => event.evidence) ?? [];
  const runbooks = (agent?.tool_events ?? []).flatMap((event) => event.evidence
    .filter((item): item is RunbookEvidence => item.evidence_type === "runbook")
    .map((item) => ({
      evidence: item,
      match: event.runbook_retrieval?.matches.find((match) => match.evidence_id === item.evidence_id) ?? null,
    })));
  const logIndex = new Map<string, LogEvidence>();
  run.backend_logs.forEach((log) => logIndex.set(log.evidence_id, log));
  evidence
    .filter((item): item is LogEvidence => item.evidence_type === "log")
    .forEach((log) => logIndex.set(log.evidence_id, log));
  const highlightedLogIds = new Set([
    ...(alarm?.evidence_ids ?? []),
    ...run.developer_response.highlighted_log_evidence_ids,
  ]);
  const focusedLogId = focus?.target_scene === "logs" ? focus.target_id : null;
  if (focusedLogId) highlightedLogIds.add(focusedLogId);
  const visibleLogs = [...logIndex.values()].filter((log) => highlightedLogIds.has(log.evidence_id));
  const sourceLogs = visibleLogs.length > 0 ? visibleLogs : alarmLogs(run);
  const released = agent?.verification_event?.answer_released ?? false;
  const failedCheckCount = agent?.verification_event
    ? [
      agent.verification_event.schema_valid,
      agent.verification_event.citations_valid,
      agent.verification_event.direct_evidence_support_valid,
      agent.verification_event.factual_result_matches_ground_truth,
      agent.verification_event.agent_sequence_valid,
      agent.verification_event.evidence_handoff_valid,
      agent.verification_event.tool_boundary_valid,
      agent.verification_event.final_author_valid,
    ].filter((valid) => !valid).length
    : 0;
  const presentation = run.localized_presentations[locale].business_response;
  const conclusion = presentation.headline || (released ? presentation.what_is_known.join(" ") : copy.withheldTitle);
  const estimatedCost = agent?.receipt.estimated_cost_usd;
  return (
    <div className="evidence-content">
      <span className="eyebrow">{copy.reportEyebrow}</span>
      <h2 id="evidence-title">{copy.behindReport}</h2>
      <p className="evidence-content__lead">{copy.registeredNotThoughts}</p>
      <div className="evidence-timeline">
        <EvidenceStep
          icon={<AlertIcon />}
          title={alarm
            ? localized(locale, "Larmet upptäcktes", "The alarm was detected")
            : localized(locale, "Inget larm utlöstes", "No alert was triggered")}
          body={alarm
            ? `${alarm.signal.observed_value} HTTP 5xx · ${alarm.signal.lookback_seconds ?? localized(locale, "okänt fönster", "unknown window")} ${typeof alarm.signal.lookback_seconds === "number" ? localized(locale, "sekunder", "seconds") : ""}`
            : localized(locale, "Agentutredningen startade inte och inga åtgärder utfördes.", "The agent investigation did not start and no actions were performed.")}
        />
        <EvidenceStep icon={<ToolIcon />} title={localized(locale, "Bevis hämtades", "Evidence was retrieved")} body={`${evidence.length} ${localized(locale, "avgränsade bevis", "bounded evidence items")} · ${copy.readOnly}`} />
        <EvidenceStep icon={<DocumentIcon />} title={localized(locale, "Runbookpassager hämtades", "Runbook passages were retrieved")} body={`${runbooks.length} ${localized(locale, "runbookpassager", "runbook passages")}`} />
        <div className={focus?.target_scene === "java" ? "evidence-focus" : ""}>
          <EvidenceStep
            tone={released ? "blue" : "amber"}
            icon={<ShieldIcon />}
            title={released
              ? localized(locale, "Verifieringen godkände slutsatsen", "Verification released the conclusion")
              : localized(locale, "Verifieringen höll tillbaka diagnosen", "Verification withheld the diagnosis")}
            body={released
              ? localized(locale, "Källor, direkt evidensstöd och agentordning godkändes.", "Sources, direct evidence support, and agent order passed verification.")
              : localized(
                locale,
                failedCheckCount === 1
                  ? "1 kontroll blev inte godkänd. Diagnosen visades därför inte som verifierad."
                  : `${failedCheckCount || 1} kontroller blev inte godkända. Diagnosen visades därför inte som verifierad.`,
                failedCheckCount === 1
                  ? "1 check failed. The diagnosis was therefore not released as verified."
                  : `${failedCheckCount || 1} checks failed. The diagnosis was therefore not released as verified.`,
              )}
          />
        </div>
      </div>
      <div className={`evidence-result evidence-result--conclusion${released ? " evidence-result--safe" : " evidence-result--amber"}`}>
        <span className="eyebrow">{localized(locale, "Rapporterad slutsats", "Reported conclusion")}</span>
        <strong>{conclusion}</strong>
        <p>{released
          ? presentation.what_is_known.join(" ")
          : localized(locale, "Agenten rapporterade påverkan utan att gissa eller ändra något.", "The agent reported the impact without guessing or changing anything.")}</p>
      </div>
      <details className="evidence-details" open={focus?.target_scene === "logs"}>
        <summary>{localized(locale, "Markerade loggar", "Highlighted logs")}<ChevronRightIcon /></summary>
        <div className="evidence-log-list">
          {sourceLogs.map((log) => (
            <div
              key={log.evidence_id}
              className={`${highlightedLogIds.has(log.evidence_id) ? "is-highlighted" : ""}${focusedLogId === log.evidence_id ? " is-focused" : ""}`}
            >
              <span>{clock(log.observed_at)}</span>
              <strong>{log.content.service}</strong>
              <em>{log.content.attributes.http_status ? `HTTP ${log.content.attributes.http_status}` : log.content.level}</em>
              <code>{log.content.message}</code>
            </div>
          ))}
        </div>
      </details>
      <details className="evidence-details" open={focus?.target_scene === "agent_rag"}>
        <summary>{localized(locale, "Hämtade runbookpassager", "Retrieved runbook passages")}<ChevronRightIcon /></summary>
        <div className="evidence-runbook-list">
          {runbooks.map(({ evidence: runbook, match }) => (
            <article
              key={runbook.evidence_id}
              className={`evidence-runbook${focus?.target_scene === "agent_rag" && focus.target_id === runbook.evidence_id ? " is-focused" : ""}`}
            >
              <div className="evidence-runbook__meta">
                <DocumentIcon />
                <span>
                  <strong>{runbook.content.document_id}</strong>
                  <small>{runbook.content.chunk_id} · v{runbook.content.document_version}</small>
                </span>
              </div>
              <p>{runbook.content.text}</p>
              {match ? (
                <small className="evidence-runbook__match">
                  {localized(locale, "Semantisk träff", "Semantic match")} #{match.rank}
                  {match.cosine_similarity === null ? "" : ` · ${match.cosine_similarity.toFixed(3)}`}
                </small>
              ) : null}
            </article>
          ))}
        </div>
      </details>
      <details className="evidence-details" open={focus?.target_scene === "java"}>
        <summary>{copy.technicalReceipt}<ChevronRightIcon /></summary>
        <ReceiptRows rows={session.mode === "live_ai" ? [
          [localized(locale, "Planeringsanrop", "Planner calls"), String(session.plan.provider_receipt ? 1 : 0)],
          [localized(locale, "Agentens modellanrop", "Agent model calls"), String(agent?.receipt.model_calls ?? 0)],
          [localized(locale, "Embedding-anrop", "Embedding calls"), String(agent?.receipt.embedding_calls ?? 0)],
          [localized(locale, "ADK-verktyg", "ADK tools"), String(agent?.receipt.adk_tool_calls ?? 0)],
          [localized(locale, "Agentkostnad, estimat", "Agent cost estimate"), estimatedCost === null || estimatedCost === undefined ? localized(locale, "Ej rapporterad", "Not reported") : `$${estimatedCost.toFixed(8)}`],
          [localized(locale, "Svar frisläppt", "Answer released"), released ? localized(locale, "Ja", "Yes") : localized(locale, "Nej", "No")],
          [localized(locale, "Ändringar", "Changes"), "0"],
        ] : [
          [localized(locale, "Nya AI-anrop i uppspelningen", "New AI calls in this playback"), String(session.replay.playback_receipt.model_calls)],
          [localized(locale, "Historiska modellanrop", "Historical model calls"), String(agent?.receipt.model_calls ?? 0)],
          [localized(locale, "Historiska ADK-verktyg", "Historical ADK tools"), String(agent?.receipt.adk_tool_calls ?? 0)],
          [localized(locale, "Historisk agentkostnad, estimat", "Historical agent cost estimate"), estimatedCost === null || estimatedCost === undefined ? localized(locale, "Ej rapporterad", "Not reported") : `$${estimatedCost.toFixed(8)}`],
          [localized(locale, "Svar frisläppt", "Answer released"), released ? localized(locale, "Ja", "Yes") : localized(locale, "Nej", "No")],
          [localized(locale, "Ändringar", "Changes"), "0"],
        ]} />
      </details>
      <p className="evidence-footnote">{session.mode === "live_ai"
        ? localized(locale, "Live-körning · backendregistrerade kvitton", "Live run · backend-recorded receipts")
        : localized(locale, "Historisk uppspelning · 0 nya AI-anrop", "Historical playback · 0 new AI calls")} · {copy.changesZero}</p>
    </div>
  );
}

function EvidenceStep({ icon, title, body, tone = "blue" }: { icon: ReactNode; title: string; body: string; tone?: "blue" | "amber" }) {
  return (
    <div className={`evidence-step evidence-step--${tone}`}>
      <span>{icon}</span>
      <div><strong>{title}</strong><p>{body}</p></div>
    </div>
  );
}

function ReceiptRows({ rows }: { rows: Array<[string, string]> }) {
  return (
    <dl className="receipt-rows">
      {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}
    </dl>
  );
}
