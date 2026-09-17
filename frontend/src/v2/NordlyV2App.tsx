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
  getKnowledgeDocuments,
  IncidentApiError,
  runDemoCustomerChatTurn,
  runIncidentLabFollowUp,
  runIncidentLabReplay,
} from "../api/client";
import type {
  DemoCustomerChatSource,
  DemoCustomerChatTurnResponse,
  IncidentLabFollowUpResponse,
  IncidentLabFollowUpSuggestionId,
  IncidentLabReplayResponse,
  KnowledgeDocumentLibraryDocument,
  KnowledgeDocumentLibraryResponse,
  KnowledgeRagLocale,
  LogEvidence,
} from "../api/generated";
import {
  buildCustomerChatConversation,
  loadCustomerChatSession,
  saveCustomerChatSession,
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
  | { kind: "drift"; replay: IncidentLabReplayResponse }
  | null;

type DriftTurn = {
  id: string;
  question: string;
  response: IncidentLabFollowUpResponse | null;
  localAnswer: string | null;
  pending: boolean;
  error: string | null;
};

type LocalDriftSuggestion = "when" | "what_unknown_receipt" | "customer_impact_receipt";

const COPY = {
  sv: {
    modes: { drift: "Driftagent", support: "Supportagent", documents: "Dokumentarkiv" },
    footer: "Verifierade källor · Endast läsning · 0 ändringar",
    synthetic: "Fiktiv Nordly-miljö · verklig backend",
    supportIdentity: "Hjälper Nordlys kunder",
    driftIdentity: "Bevakar Nordlys köpflöde",
    greeting: "Hej! Vad kan jag hjälpa dig med?",
    supportPlaceholder: "Skriv till Nordly…",
    driftPlaceholder: "Välj en verifierad följdfråga ovan",
    send: "Skicka",
    supportSuggestions: ["Var är min order?", "Kan jag avbryta den?", "Hur fungerar retur?"],
    waiting: "Nordly undersöker…",
    slow: "Det tar lite längre än vanligt. Vi väntar fortfarande på backendens kompletta kvitto.",
    retry: "Försök igen",
    confirmTitle: "Sök i Nordlys godkända dokument?",
    confirmBody: "För att svara behöver jag göra en live-sökning i företagets policydokument.",
    noProviderYet: "Inga provideranrop har gjorts ännu.",
    cancel: "Avbryt",
    searchDocuments: "Sök i dokumenten",
    behindAnswer: "Så kom svaret fram",
    behindReport: "Så kom rapporten fram",
    source: "Källa",
    order: "Order",
    status: "Status",
    delivery: "Beräknad leverans",
    verifiedNoChange: "Verifierat svar · ingen ändring",
    readOnly: "Endast läst · inget ändrat",
    newConversation: "Ny konversation",
    replayPill: "Inspelad replay",
    driftIdleTitle: "Driftagenten håller vakt.",
    driftIdleBody: "Starta ett registrerat syntetiskt fall för att se hur agenten rapporterar ett larm.",
    startReplay: "Starta replay",
    replayTruth: "0 nya AI-anrop i uppspelningen",
    receiptWaiting: "Väntar på backendens kompletta kvitto…",
    receiptReady: "Kvitto mottaget · spelar upp registrerade händelser",
    showNow: "Visa resultat nu",
    replayUnavailable: "Replayen kunde inte laddas.",
    incidentHello: (count: number) => `Hej. Jag har upptäckt ${count} felhändelser i betalningsflödet.`,
    incidentBody: (count: number, status: string) => `I den inspelade utredningen påverkades betalningar i kassan samtidigt som ${count} logghändelser med ${status} registrerades. Nu spelas kvittot upp utan att något körs om.`,
    whenQuestion: "När hände det?",
    whyQuestion: "Vet du varför?",
    timeAnswer: (times: string, service: string) => `${times}. Händelserna registrerades i ${service}, som är larmets signalkälla.`,
    withheldTitle: "Jag kan se problemet – men inte bevisa orsaken än.",
    withheldBody: "Java-kontrollen frisläppte ingen rotorsak eller felplats. Jag rapporterar därför påverkan utan att gissa.",
    noRunReference: "Körningen saknar ett giltigt följdfrågekvitto. Källorna går fortfarande att granska.",
    driftSuggestions: {
      how_conclusion: "Hur kom du fram till det?",
      show_sources: "Visa källorna",
      what_unknown: "Vad vet du inte?",
      customer_impact: "Kundpåverkan",
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
    stoppedBeforeAi: "Stoppad före AI",
    safetyWon: "Företagets åtkomstregler vann.",
    changesZero: "0 ändringar",
  },
  en: {
    modes: { drift: "Operations agent", support: "Support agent", documents: "Document archive" },
    footer: "Verified sources · Read only · 0 changes",
    synthetic: "Fictional Nordly environment · real backend",
    supportIdentity: "Helps Nordly customers",
    driftIdentity: "Watches Nordly's checkout flow",
    greeting: "Hi! How can I help?",
    supportPlaceholder: "Message Nordly…",
    driftPlaceholder: "Choose a verified follow-up above",
    send: "Send",
    supportSuggestions: ["Where is my order?", "Can I cancel it?", "How do returns work?"],
    waiting: "Nordly is checking…",
    slow: "This is taking a little longer. We are still waiting for the backend's complete receipt.",
    retry: "Try again",
    confirmTitle: "Search Nordly's approved documents?",
    confirmBody: "To answer, I need to run a live search across the company's approved policy documents.",
    noProviderYet: "No provider calls have been made yet.",
    cancel: "Cancel",
    searchDocuments: "Search documents",
    behindAnswer: "How this answer was made",
    behindReport: "How this report was made",
    source: "Source",
    order: "Order",
    status: "Status",
    delivery: "Estimated delivery",
    verifiedNoChange: "Verified answer · no change",
    readOnly: "Read only · nothing changed",
    newConversation: "New conversation",
    replayPill: "Recorded replay",
    driftIdleTitle: "The Operations agent is watching.",
    driftIdleBody: "Start a recorded synthetic case to see how the agent reports an alarm.",
    startReplay: "Start replay",
    replayTruth: "0 new AI calls in this playback",
    receiptWaiting: "Waiting for the backend's complete receipt…",
    receiptReady: "Receipt received · replaying registered events",
    showNow: "Show result now",
    replayUnavailable: "The replay could not be loaded.",
    incidentHello: (count: number) => `Hi. I detected ${count} error events in the payment flow.`,
    incidentBody: (count: number, status: string) => `In the recorded investigation, checkout payments were affected while ${count} log events with ${status} were registered. The receipt is now replayed without rerunning anything.`,
    whenQuestion: "When did it happen?",
    whyQuestion: "Do you know why?",
    timeAnswer: (times: string, service: string) => `${times}. The events were recorded in ${service}, which is the alarm's signal source.`,
    withheldTitle: "I can see the problem — but I cannot prove the cause yet.",
    withheldBody: "The Java verification released neither a root cause nor a failure location. I report the impact without guessing.",
    noRunReference: "This run has no valid follow-up receipt. Its sources can still be inspected.",
    driftSuggestions: {
      how_conclusion: "How did you reach that?",
      show_sources: "Show sources",
      what_unknown: "What don't you know?",
      customer_impact: "Customer impact",
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
    stoppedBeforeAi: "Stopped before AI",
    safetyWon: "The company's access rules won.",
    changesZero: "0 changes",
  },
} as const;

const FOLLOW_UP_IDS: IncidentLabFollowUpSuggestionId[] = [
  "how_conclusion",
  "show_sources",
  "what_unknown",
  "customer_impact",
  "agent_boundary",
];

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
  if (error instanceof IncidentApiError) return error.message;
  return locale === "sv" ? "Backend kunde inte lämna ett svar." : "The backend could not return an answer.";
}

function clock(value: string) {
  const match = value.match(/T(\d{2}:\d{2}:\d{2})/);
  return match?.[1] ?? value;
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

function alarmLogs(replay: IncidentLabReplayResponse | null): LogEvidence[] {
  if (!replay) return [];
  const run = replay.recorded_run;
  const ids = new Set(run.alarm_receipt?.evidence_ids ?? []);
  const exact = run.backend_logs.filter((item) => ids.has(item.evidence_id));
  if (exact.length > 0) return exact;
  return run.backend_logs.filter((item) => item.content.attributes.http_status?.startsWith("5")).slice(0, 3);
}

function statusLabel(logs: LogEvidence[]) {
  const statuses = [...new Set(logs.map((item) => item.content.attributes.http_status).filter(Boolean))];
  if (statuses.length === 1) return `HTTP ${statuses[0]}`;
  return "HTTP 5xx";
}

function titleForDocument(document: KnowledgeDocumentLibraryDocument, locale: Locale) {
  return locale === "sv" ? document.title_sv : document.title;
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
  const [supportTurns, setSupportTurns] = useState<CustomerChatTurn[]>(() => loadCustomerChatSession());
  const [supportDraft, setSupportDraft] = useState("");
  const [supportPendingId, setSupportPendingId] = useState<string | null>(null);
  const [replay, setReplay] = useState<IncidentLabReplayResponse | null>(null);
  const [replayState, setReplayState] = useState<"idle" | "requesting" | "playing" | "ready" | "error">("idle");
  const [playbackStage, setPlaybackStage] = useState(0);
  const [driftTurns, setDriftTurns] = useState<DriftTurn[]>([]);
  const restoreFocusRef = useRef<HTMLElement | null>(null);
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
    saveCustomerChatSession(supportTurns);
  }, [supportTurns]);

  useEffect(() => {
    const onHashChange = () => setModeState(modeFromHash());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    if (replayState !== "playing") return;
    if (playbackStage >= 5) {
      setReplayState("ready");
      return;
    }
    const delay = playbackStage === 0 ? 260 : 540;
    const timer = window.setTimeout(() => setPlaybackStage((current) => current + 1), delay);
    return () => window.clearTimeout(timer);
  }, [playbackStage, replayState]);

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

  const submitSupport = useCallback(async (
    rawMessage: string,
    confirmLiveAi = false,
    existingId?: string,
  ) => {
    const message = rawMessage.trim();
    if (!message || supportPendingId) return;
    const id = existingId ?? clientId("support");
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
      setSupportTurns((current) => current.map((turn) => turn.clientId === id
        ? {
          ...turn,
          question: response.submitted_message.text,
          locale,
          response,
          errorCode: null,
        }
        : turn));
    } catch (error) {
      setSupportTurns((current) => current.map((turn) => turn.clientId === id
        ? { ...turn, errorCode: apiErrorText(error, locale) }
        : turn));
    } finally {
      setSupportPendingId(null);
    }
  }, [locale, supportPendingId, supportTurns]);

  const startReplay = useCallback(async () => {
    if (replayState === "requesting") return;
    setReplayState("requesting");
    setPlaybackStage(0);
    setDriftTurns([]);
    try {
      const response = await runIncidentLabReplay();
      setReplay(response);
      setReplayState("playing");
    } catch {
      setReplayState("error");
    }
  }, [replayState]);

  const skipPlayback = useCallback(() => {
    setPlaybackStage(5);
    setReplayState("ready");
  }, []);

  const submitDrift = useCallback(async (
    rawQuestion: string,
    suggestionId?: IncidentLabFollowUpSuggestionId | LocalDriftSuggestion,
  ) => {
    const question = rawQuestion.trim();
    if (!question || !replay) return;
    const id = clientId("drift");
    const logs = alarmLogs(replay);
    if (["when", "what_unknown_receipt", "customer_impact_receipt"].includes(suggestionId ?? "")) {
      const presentation = replay.recorded_run.localized_presentations[locale].business_response;
      const answer = suggestionId === "when"
        ? copy.timeAnswer(
          logs.map((log) => clock(log.observed_at)).join(", "),
          replay.recorded_run.alarm_receipt?.service ?? logs[0]?.content.service ?? "backend",
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
    if (!replay.run_reference) {
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, pending: false, error: copy.noRunReference }
        : item));
      return;
    }
    try {
      const response = await runIncidentLabFollowUp({
        run_reference: replay.run_reference,
        client_turn_id: id,
        question,
        suggestion_id: suggestionId,
        locale,
        confirm_live_ai: false,
      });
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, response, pending: false }
        : item));
    } catch (error) {
      setDriftTurns((current) => current.map((item) => item.id === id
        ? { ...item, pending: false, error: apiErrorText(error, locale) }
        : item));
    }
  }, [copy, locale, replay]);

  const currentDocument = useMemo(() => {
    if (!documents) return null;
    if (selectedDocument) {
      return documents.documents.find((item) => item.id === selectedDocument.documentId) ?? null;
    }
    return documents.documents.find((item) => item.content_visible) ?? documents.documents[0] ?? null;
  }, [documents, selectedDocument]);

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
                    turns={supportTurns}
                    draft={supportDraft}
                    pendingId={supportPendingId}
                    setDraft={setSupportDraft}
                    submit={submitSupport}
                    cancel={cancelSupportTurn}
                    openEvidence={(response) => openEvidence({ kind: "support", response })}
                    openDocument={openDocument}
                  />
                ) : mode === "drift" ? (
                  <DriftAgentView
                    locale={locale}
                    replay={replay}
                    replayState={replayState}
                    playbackStage={playbackStage}
                    turns={driftTurns}
                    startReplay={startReplay}
                    skipPlayback={skipPlayback}
                    submit={submitDrift}
                    openEvidence={() => replay && openEvidence({ kind: "drift", replay })}
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

function AgentIdentity({ kind, locale, badge }: { kind: "drift" | "support"; locale: Locale; badge?: string }) {
  const copy = COPY[locale];
  return (
    <div className="agent-identity">
      <span className="agent-identity__mark"><NordlySignal /></span>
      <span className="agent-identity__text">
        <h1>{copy.modes[kind]}</h1>
        <small><i />{kind === "drift" ? copy.driftIdentity : copy.supportIdentity}</small>
      </span>
      {badge ? <span className="agent-identity__badge"><i />{badge}</span> : null}
    </div>
  );
}

function SupportAgentView({
  locale,
  turns,
  draft,
  pendingId,
  setDraft,
  submit,
  cancel,
  openEvidence,
  openDocument,
}: {
  locale: Locale;
  turns: CustomerChatTurn[];
  draft: string;
  pendingId: string | null;
  setDraft: (value: string) => void;
  submit: (message: string, confirm?: boolean, existingId?: string) => Promise<void>;
  cancel: (id: string) => void;
  openEvidence: (response: DemoCustomerChatTurnResponse) => void;
  openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void;
}) {
  const copy = COPY[locale];
  const bottomRef = useRef<HTMLDivElement>(null);
  const [slow, setSlow] = useState(false);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [turns.length, pendingId]);

  useEffect(() => {
    if (!pendingId) {
      setSlow(false);
      return;
    }
    const timer = window.setTimeout(() => setSlow(true), 6000);
    return () => window.clearTimeout(timer);
  }, [pendingId]);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!draft.trim() || pendingId) return;
    const message = draft;
    setDraft("");
    void submit(message);
  };

  return (
    <section className="chat-stage" aria-label={copy.modes.support}>
      <div className="chat-column">
        <AgentIdentity kind="support" locale={locale} />
        <div className="chat-thread" role="log" aria-live="polite" aria-relevant="additions text" aria-busy={Boolean(pendingId)}>
          <MessageBubble side="assistant" timestamp="14:30">
            <p>{copy.greeting}</p>
          </MessageBubble>

          {turns.map((turn) => (
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
          ))}
          <div ref={bottomRef} />
        </div>

        <div className="chat-composer-wrap">
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
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder={copy.supportPlaceholder}
              maxLength={500}
              disabled={Boolean(pendingId)}
            />
            <button type="submit" disabled={!draft.trim() || Boolean(pendingId)} aria-label={copy.send}><SendIcon /></button>
          </form>
        </div>
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
        <MessageBubble side="assistant" tone={response.outcome === "refused" ? "protected" : "default"}>
          <p className="message-emphasis">{text}</p>
          {response.order ? <OrderStrip response={response} locale={locale} /> : null}
          {response.sources.length > 0 ? (
            <SourceChips sources={response.sources} locale={locale} onOpen={openDocument} />
          ) : null}
          {response.outcome === "refused" ? (
            <div className="protected-inline"><LockIcon /><span>{localized(locale, "Stoppad innan dokument eller AI användes", "Stopped before documents or AI were used")}</span></div>
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
      <strong>{order.order_id}</strong>
      <span>·</span>
      <em>{locale === "sv" ? order.status_sv : order.status_en}</em>
      <span>·</span>
      <span>{shortDate(order.estimated_delivery_from, locale)}–{shortDate(order.estimated_delivery_through, locale)}</span>
    </div>
  );
}

function SourceChips({ sources, locale, onOpen }: { sources: DemoCustomerChatSource[]; locale: Locale; onOpen: (source: DemoCustomerChatSource, returnMode?: Mode) => void }) {
  return (
    <div className="source-chips">
      {sources.map((source) => (
        <button
          key={source.evidence_id}
          type="button"
          onClick={() => onOpen(source, "support")}
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
  replay,
  replayState,
  playbackStage,
  turns,
  startReplay,
  skipPlayback,
  submit,
  openEvidence,
}: {
  locale: Locale;
  replay: IncidentLabReplayResponse | null;
  replayState: "idle" | "requesting" | "playing" | "ready" | "error";
  playbackStage: number;
  turns: DriftTurn[];
  startReplay: () => Promise<void>;
  skipPlayback: () => void;
  submit: (question: string, suggestionId?: IncidentLabFollowUpSuggestionId | LocalDriftSuggestion) => Promise<void>;
  openEvidence: () => void;
}) {
  const copy = COPY[locale];
  const bottomRef = useRef<HTMLDivElement>(null);
  const logs = alarmLogs(replay);
  const alarm = replay?.recorded_run.alarm_receipt;
  const count = Number(alarm?.signal.observed_value ?? logs.length);
  const service = alarm?.service ?? logs[0]?.content.service ?? "backend";
  const status = statusLabel(logs);
  const times = logs.map((log) => clock(log.observed_at));
  const ready = replayState === "ready";
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [playbackStage, replayState, turns.length]);
  return (
    <section className="chat-stage" aria-label={copy.modes.drift}>
      <div className="chat-column">
        <AgentIdentity kind="drift" locale={locale} badge={replay ? copy.replayPill : undefined} />
        <div className="chat-thread chat-thread--drift" role="log" aria-live="polite" aria-relevant="additions text" aria-busy={replayState === "requesting"}>
          {replayState === "idle" ? (
            <motion.div className="drift-idle" initial={{ opacity: 0 }} animate={{ opacity: 1 }}>
              <span className="drift-idle__signal"><NordlySignal /></span>
              <h2>{copy.driftIdleTitle}</h2>
              <p>{copy.driftIdleBody}</p>
              <button type="button" className="button-primary" onClick={() => void startReplay()}>{copy.startReplay}</button>
              <small><DocumentIcon />{copy.replayTruth}</small>
            </motion.div>
          ) : replayState === "requesting" ? (
            <div className="drift-requesting" role="status">
              <span className="drift-requesting__signal"><NordlySignal /></span>
              <strong>{copy.receiptWaiting}</strong>
              <TypingIndicator label="" />
            </div>
          ) : replayState === "error" ? (
            <div className="drift-idle drift-idle--error">
              <AlertIcon />
              <h2>{copy.replayUnavailable}</h2>
              <button type="button" className="button-primary" onClick={() => void startReplay()}><RefreshIcon />{copy.retry}</button>
            </div>
          ) : replay ? (
            <>
              {replayState === "playing" ? (
                <div className="playback-status" role="status">
                  <span><CheckIcon />{copy.receiptReady}</span>
                  <button type="button" onClick={skipPlayback}>{copy.showNow}</button>
                </div>
              ) : null}
              <AnimatePresence initial={false}>
                {playbackStage >= 1 ? (
                  <MessageBubble key="alarm" side="assistant" tone="alert" timestamp={times.at(-1)}>
                    <p className="message-emphasis">{copy.incidentHello(count)}</p>
                    <p>{copy.incidentBody(count, status)}</p>
                    <div className="incident-strip"><AlertIcon /><strong>{count} × {status}</strong><span>·</span><span>{times[0]}–{times.at(-1)}</span><span>·</span><span>{localized(locale, "Betalningsflödet", "Payment flow")}</span></div>
                  </MessageBubble>
                ) : null}
                {playbackStage >= 2 ? (
                  <MessageBubble key="when-question" side="user"><p>{copy.whenQuestion}</p></MessageBubble>
                ) : null}
                {playbackStage >= 3 ? (
                  <MessageBubble key="when-answer" side="assistant">
                    <p>{copy.timeAnswer(times.join(", "), service)}</p>
                    <div className="source-chips source-chips--times">
                      {logs.map((log) => <span key={log.evidence_id}><DocumentIcon />{clock(log.observed_at)}</span>)}
                    </div>
                    <div className="verified-row"><CheckIcon />{localized(locale, "Från larmkvittot · inget nytt AI-anrop", "From the alarm receipt · no new AI call")}</div>
                  </MessageBubble>
                ) : null}
                {playbackStage >= 4 ? (
                  <MessageBubble key="why-question" side="user"><p>{copy.whyQuestion}</p></MessageBubble>
                ) : null}
                {playbackStage >= 5 ? (
                  <MessageBubble key="why-answer" side="assistant" tone="withheld">
                    <p className="message-emphasis">{copy.withheldTitle}</p>
                    <p>{copy.withheldBody}</p>
                    <div className="source-chips">
                      <span><DocumentIcon />{count} {localized(locale, "logghändelser", "log events")}</span>
                      <span><DocumentIcon />{replay.recorded_run.agent_turn?.tool_events.flatMap((event) => event.evidence).filter((item) => item.evidence_type === "runbook").length ?? 0} {localized(locale, "runbookpassager", "runbook passages")}</span>
                    </div>
                    <button type="button" className="disclosure-row" onClick={openEvidence}><span>{copy.behindReport}</span><ChevronRightIcon /></button>
                    <div className="verified-row"><CheckIcon />{copy.readOnly}</div>
                  </MessageBubble>
                ) : null}
              </AnimatePresence>

              {turns.map((turn) => (
                <motion.div key={turn.id} className="chat-turn" layout initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}>
                  <MessageBubble side="user"><p>{turn.question}</p></MessageBubble>
                  <MessageBubble side="assistant" tone={turn.error ? "error" : "default"}>
                    {turn.pending ? <TypingIndicator label={localized(locale, "Kontrollerar det frysta kvittot…", "Checking the frozen receipt…")} /> : null}
                    {turn.localAnswer ? <p>{turn.localAnswer}</p> : null}
                    {turn.localAnswer ? <div className="verified-row"><CheckIcon />{localized(locale, "Från larmkvittot · inget nytt AI-anrop", "From the alarm receipt · no new AI call")}</div> : null}
                    {turn.response ? (
                      <>
                        <p>{turn.response.answer.text}</p>
                        {turn.response.citations.length > 0 ? (
                          <div className="source-chips">
                            {turn.response.citations.slice(0, 3).map((citation) => <span key={citation.evidence_id}><DocumentIcon />{citation.label}</span>)}
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

        <div className="chat-composer-wrap">
          {ready && replay ? (
            <div className="suggestion-row suggestion-row--drift">
              <button type="button" onClick={() => void submit(copy.whenQuestion, "when")}>{copy.whenQuestion}</button>
              {replay.run_reference ? FOLLOW_UP_IDS.slice(0, 3).map((id) => (
                <button key={id} type="button" onClick={() => void submit(copy.driftSuggestions[id], id)}>{copy.driftSuggestions[id]}</button>
              )) : (
                <>
                  <button type="button" onClick={() => void submit(copy.driftSuggestions.what_unknown, "what_unknown_receipt")}>{copy.driftSuggestions.what_unknown}</button>
                  <button type="button" onClick={() => void submit(copy.driftSuggestions.customer_impact, "customer_impact_receipt")}>{copy.driftSuggestions.customer_impact}</button>
                  <button type="button" onClick={openEvidence}>{copy.driftSuggestions.show_sources}</button>
                </>
              )}
            </div>
          ) : null}
          <div className="chat-composer chat-composer--locked" aria-label={localized(locale, "Replayens följdfrågor", "Replay follow-ups")}>
            <label className="sr-only" htmlFor="drift-message">{copy.driftPlaceholder}</label>
            <input id="drift-message" value="" placeholder={copy.driftPlaceholder} disabled readOnly />
            <button type="button" disabled aria-label={copy.send}><SendIcon /></button>
          </div>
        </div>
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
          <DriftEvidence replay={state.replay} locale={locale} />
        )}
      </motion.aside>
    </>
  );
}

function SupportEvidence({ response, locale, openDocument }: { response: DemoCustomerChatTurnResponse; locale: Locale; openDocument: (source: DemoCustomerChatSource, returnMode?: Mode) => void }) {
  const copy = COPY[locale];
  const blocked = response.outcome === "refused" || response.safety.decision === "BLOCK";
  const executedSteps = response.tool_events.filter((event) => event.executed);
  return (
    <div className="evidence-content">
      <span className="eyebrow">{blocked ? copy.safetyReceipt : copy.evidenceEyebrow}</span>
      <h2 id="evidence-title">{blocked ? copy.stoppedBeforeAi : copy.behindAnswer}</h2>
      <p className="evidence-content__lead">{copy.registeredNotThoughts}</p>
      <div className="evidence-timeline">
        {blocked ? (
          <>
            <EvidenceStep icon={<ShieldIcon />} title={localized(locale, "Frågan kontrollerades", "The question was checked")} body={locale === "sv" ? response.safety.summary_sv : response.safety.summary_en} />
            <EvidenceStep icon={<CheckIcon />} title={localized(locale, "Inget dokumentinnehåll lästes", "No document content was read")} body={`${response.receipt.read_operations} ${localized(locale, "läsningar", "reads")}`} />
            <EvidenceStep icon={<CheckIcon />} title={localized(locale, "Inget skickades till modellen", "Nothing was sent to the model")} body={`${response.receipt.provider_calls} ${localized(locale, "provideranrop", "provider calls")}`} />
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
        [localized(locale, "Ändringar", "Changes"), "0"],
      ]} />
      {blocked ? <div className="evidence-result evidence-result--safe"><CheckIcon /><strong>{copy.safetyWon}</strong></div> : null}
    </div>
  );
}

function DriftEvidence({ replay, locale }: { replay: IncidentLabReplayResponse; locale: Locale }) {
  const copy = COPY[locale];
  const run = replay.recorded_run;
  const alarm = run.alarm_receipt;
  const agent = run.agent_turn;
  const evidence = agent?.tool_events.flatMap((event) => event.evidence) ?? [];
  const runbooks = evidence.filter((item) => item.evidence_type === "runbook");
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
  return (
    <div className="evidence-content">
      <span className="eyebrow">{copy.reportEyebrow}</span>
      <h2 id="evidence-title">{copy.behindReport}</h2>
      <p className="evidence-content__lead">{copy.registeredNotThoughts}</p>
      <div className="evidence-timeline">
        <EvidenceStep icon={<AlertIcon />} title={localized(locale, "Larmet upptäcktes", "The alarm was detected")} body={`${alarm?.signal.observed_value ?? 0} HTTP 5xx · ${alarm?.signal.lookback_seconds ?? 0} ${localized(locale, "sekunder", "seconds")}`} />
        <EvidenceStep icon={<ToolIcon />} title={localized(locale, "Bevis hämtades", "Evidence was retrieved")} body={`${evidence.length} ${localized(locale, "avgränsade bevis", "bounded evidence items")} · ${copy.readOnly}`} />
        <EvidenceStep icon={<DocumentIcon />} title={localized(locale, "Runbookpassager hämtades", "Runbook passages were retrieved")} body={`${runbooks.length} ${localized(locale, "runbookpassager", "runbook passages")}`} />
        <EvidenceStep
          tone="amber"
          icon={<ShieldIcon />}
          title={localized(locale, "Java undanhöll diagnosen", "Java withheld the diagnosis")}
          body={localized(
            locale,
            failedCheckCount === 1
              ? "1 verifieringskontroll blev inte godkänd. Diagnosen visades därför inte som verifierad."
              : `${failedCheckCount || 1} verifieringskontroller blev inte godkända. Diagnosen visades därför inte som verifierad.`,
            failedCheckCount === 1
              ? "1 verification check failed. The diagnosis was therefore not released as verified."
              : `${failedCheckCount || 1} verification checks failed. The diagnosis was therefore not released as verified.`,
          )}
        />
      </div>
      <div className="evidence-result evidence-result--amber">
        <span className="eyebrow">{localized(locale, "Rapporterad slutsats", "Reported conclusion")}</span>
        <strong>{copy.withheldTitle}</strong>
        <p>{localized(locale, "Agenten rapporterade påverkan utan att gissa eller ändra något.", "The agent reported the impact without guessing or changing anything.")}</p>
      </div>
      <details className="evidence-details">
        <summary>{copy.sourcesAndEvidence}<ChevronRightIcon /></summary>
        <div className="evidence-log-list">
          {alarmLogs(replay).map((log) => <div key={log.evidence_id}><span>{clock(log.observed_at)}</span><strong>{log.content.service}</strong><em>{log.content.attributes.http_status ? `HTTP ${log.content.attributes.http_status}` : log.content.level}</em></div>)}
        </div>
      </details>
      <details className="evidence-details">
        <summary>{copy.technicalReceipt}<ChevronRightIcon /></summary>
        <ReceiptRows rows={[
          [localized(locale, "Nya AI-anrop i uppspelningen", "New AI calls in this playback"), String(replay.playback_receipt.model_calls)],
          [localized(locale, "Historiska modellanrop", "Historical model calls"), String(agent?.receipt.model_calls ?? 0)],
          [localized(locale, "Historiska ADK-verktyg", "Historical ADK tools"), String(agent?.receipt.adk_tool_calls ?? 0)],
          [localized(locale, "Svar frisläppt", "Answer released"), released ? localized(locale, "Ja", "Yes") : localized(locale, "Nej", "No")],
          [localized(locale, "Ändringar", "Changes"), "0"],
        ]} />
      </details>
      <p className="evidence-footnote">{localized(locale, "Den här uppspelningen", "This playback")} · 0 {localized(locale, "nya AI-anrop", "new AI calls")} · {copy.changesZero}</p>
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
