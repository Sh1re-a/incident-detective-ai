import { useEffect, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { IncidentApiError, runIncidentLabFollowUp } from "./api/client";
import type {
  IncidentLabFollowUpCitation,
  IncidentLabFollowUpMode,
  IncidentLabFollowUpResponse,
  IncidentLabFollowUpStep,
  IncidentLabRunResponse,
  KnowledgeRagLocale,
} from "./api/generated";

interface IncidentFollowUpChatProps {
  locale: KnowledgeRagLocale;
  run: IncidentLabRunResponse;
  runReference: string | null;
  mode: IncidentLabFollowUpMode;
  technicalDetailsOpen: boolean;
  onToggleTechnicalDetails: () => void;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
  onOpenEvidenceScene: (target: IncidentLabFollowUpCitation["target_scene"]) => void;
}

interface ChatTurn {
  id: string;
  question: string;
  suggestionId: string | null;
  response: IncidentLabFollowUpResponse | null;
  errorCode: string | null;
  pending: boolean;
}

const INITIAL_SUGGESTIONS = {
  sv: [
    { id: "how_conclusion", label: "Hur vet du det?" },
    { id: "show_sources", label: "Visa källorna" },
    { id: "what_unknown", label: "Vad vet du inte?" },
  ],
  en: [
    { id: "how_conclusion", label: "How do you know?" },
    { id: "show_sources", label: "Show the sources" },
    { id: "what_unknown", label: "What do you not know?" },
  ],
} as const;

const labels = {
  sv: {
    region: "Samtal med Nordly Driftagent",
    complete: "Utredningen är klar",
    replayMode: "Verifierad demo",
    liveMode: "Live AI",
    withheldTitle: "Jag kan se problemet – men inte bevisa orsaken än.",
    impact: "Påverkan",
    unknown: "Det här kan jag inte fastställa",
    askPrompt: "Fråga mig om slutsatsen, påverkan eller källorna.",
    showEvidence: "Visa underlag och källor",
    location: "Var syns problemet?",
    cause: "Vad pekar underlaget på?",
    evidenceImpact: "Vad blev påverkan?",
    logs: "Öppna loggarna",
    rag: "Öppna RAG-källan",
    java: "Öppna Java-kontrollen",
    technical: "Visa tekniskt kvitto",
    hideTechnical: "Dölj tekniskt kvitto",
    inputLabel: "Fråga Driftagenten om larmet",
    placeholder: "Fråga om larmet…",
    send: "Skicka",
    sending: "Driftagenten granskar underlaget…",
    suggestions: "Frågor om slutsatsen",
    exploreReplay: "Utforska den verifierade körningen",
    exploreLive: "Vad vill du veta?",
    replayNote: "Den här demon använder verifierade frågor och gör inga nya AI-anrop.",
    liveNote: "Svaret får bara använda underlaget från den här incidenten.",
    answerLabel: "Svar från Nordly Driftagent",
    evidenceCount: (count: number) => `Visa ${count} ${count === 1 ? "källa" : "källor"} och kontroll`,
    sources: "Källor bakom svaret",
    journey: "Så kontrollerades svaret",
    journeyNote: "Registrerade backendsteg – inte AI:ns dolda tankar.",
    verified: "Verifierat av Java · endast läsning · 0 ändringar",
    bounded: "Svaret hölls inom den här incidenten",
    error: "Jag kunde inte lämna ett verifierat svar.",
    retry: "Försök gärna igen utan att starta om utredningen.",
    expired: "Incidenten har gått ut. Samtalet finns kvar, men nya frågor är stängda.",
    unavailable: "Slutsatsen går att läsa, men den här körningen kan inte ta emot frågor.",
  },
  en: {
    region: "Conversation with the Nordly Incident Agent",
    complete: "The investigation is complete",
    replayMode: "Verified demo",
    liveMode: "Live AI",
    withheldTitle: "I can see the problem – but I cannot prove the cause yet.",
    impact: "Impact",
    unknown: "What I cannot establish",
    askPrompt: "Ask me about the conclusion, impact or sources.",
    showEvidence: "Show evidence and sources",
    location: "Where is the problem visible?",
    cause: "What does the evidence indicate?",
    evidenceImpact: "What was the impact?",
    logs: "Open the logs",
    rag: "Open the RAG source",
    java: "Open Java verification",
    technical: "Show technical receipt",
    hideTechnical: "Hide technical receipt",
    inputLabel: "Ask the Incident Agent about the alert",
    placeholder: "Ask about the alert…",
    send: "Send",
    sending: "The Incident Agent is checking the evidence…",
    suggestions: "Questions about the conclusion",
    exploreReplay: "Explore the verified run",
    exploreLive: "What would you like to know?",
    replayNote: "This demo uses verified questions and makes no new AI calls.",
    liveNote: "The answer may only use evidence from this incident.",
    answerLabel: "Answer from the Nordly Incident Agent",
    evidenceCount: (count: number) => `Show ${count} ${count === 1 ? "source" : "sources"} and verification`,
    sources: "Sources behind the answer",
    journey: "How the answer was checked",
    journeyNote: "Recorded backend steps – not the AI's hidden reasoning.",
    verified: "Verified by Java · read-only · 0 changes",
    bounded: "The answer stayed within this incident",
    error: "I could not provide a verified answer.",
    retry: "Try again without restarting the investigation.",
    expired: "The incident has expired. The conversation remains, but new questions are closed.",
    unavailable: "The conclusion remains readable, but this run cannot accept questions.",
  },
} as const;

function createTurnId() {
  const uuid = globalThis.crypto?.randomUUID?.();
  if (uuid) return `incident-follow-up-${uuid}`;
  return `incident-follow-up-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function humanise(value: string) {
  return value.replaceAll("_", " ").replace(/^./, (letter) => letter.toUpperCase());
}

function lastAnsweredTurn(turns: ChatTurn[]) {
  for (let index = turns.length - 1; index >= 0; index -= 1) {
    if (turns[index].response) return turns[index].response;
  }
  return null;
}

function CitationButtons({
  citations,
  label,
  onOpenCitation,
}: {
  citations: IncidentLabFollowUpCitation[];
  label: string;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
}) {
  if (citations.length === 0) return null;
  return (
    <div className="incident-chat-source-list" aria-label={label}>
      {citations.map((citation) => (
        <button key={citation.evidence_id} type="button" onClick={() => onOpenCitation(citation)}>
          <span aria-hidden="true">↗</span>
          {citation.label}
        </button>
      ))}
    </div>
  );
}

function AgentMovement({
  steps,
  locale,
}: {
  steps: IncidentLabFollowUpStep[];
  locale: KnowledgeRagLocale;
}) {
  const copy = labels[locale];
  if (steps.length === 0) return null;
  const orderedSteps = [...steps].sort((left, right) => left.sequence - right.sequence);
  return (
    <section className="incident-chat-process" aria-label={copy.journey}>
      <header>
        <strong>{copy.journey}</strong>
        <span>{copy.journeyNote}</span>
      </header>
      <ol>
        {orderedSteps.map((step) => (
          <li key={`${step.sequence}-${step.code}`} data-status={step.status}>
            <span aria-hidden="true">{step.status === "completed" ? "✓" : "!"}</span>
            <p>{step.summary}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}

function ConclusionMessage({
  run,
  locale,
  mode,
  technicalDetailsOpen,
  onToggleTechnicalDetails,
  onOpenEvidenceScene,
}: {
  run: IncidentLabRunResponse;
  locale: KnowledgeRagLocale;
  mode: IncidentLabFollowUpMode;
  technicalDetailsOpen: boolean;
  onToggleTechnicalDetails: () => void;
  onOpenEvidenceScene: (target: IncidentLabFollowUpCitation["target_scene"]) => void;
}) {
  const copy = labels[locale];
  const presentation = run.localized_presentations[locale];
  const developerResponse = presentation.developer_response;
  const location = developerResponse.affected_service
    ? humanise(developerResponse.affected_service)
    : run.alarm_receipt?.service
      ? humanise(run.alarm_receipt.service)
      : "–";
  const unknown = presentation.business_response.what_remains_unknown[0] ?? null;
  const cause = run.answer_state === "diagnosed" && developerResponse.root_cause_code
    ? humanise(developerResponse.root_cause_code)
    : unknown ?? "–";
  const title = run.answer_state === "withheld"
    ? copy.withheldTitle
    : presentation.business_response.headline;

  return (
    <div className="message-row message-row--assistant incident-answer-chat__opening">
      <span className="message-avatar" aria-hidden="true">N</span>
      <article className="message-bubble message-bubble--assistant incident-conclusion" aria-label={copy.complete}>
        <div className="incident-conclusion__status">
          <span aria-hidden="true" />
          {copy.complete}
          <em>{mode === "live_ai" ? copy.liveMode : copy.replayMode}</em>
        </div>
        <h4>{title}</h4>
        <p>{presentation.business_response.what_happened}</p>
        <p className="incident-conclusion__fact"><strong>{copy.impact}:</strong> {presentation.business_response.impact}</p>
        {unknown ? <p className="incident-conclusion__unknown"><strong>{copy.unknown}:</strong> {unknown}</p> : null}
        <p className="incident-conclusion__prompt">{copy.askPrompt}</p>

        <details className="incident-chat-disclosure incident-chat-disclosure--conclusion">
          <summary>
            <span>{copy.showEvidence}</span>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
          </summary>
          <div className="incident-chat-disclosure__body">
            <dl className="incident-conclusion__evidence">
              <div><dt>{copy.location}</dt><dd>{location}</dd></div>
              <div><dt>{copy.cause}</dt><dd>{cause}</dd></div>
              <div><dt>{copy.evidenceImpact}</dt><dd>{presentation.business_response.impact}</dd></div>
            </dl>
            <div className="incident-conclusion__source-actions">
              <button type="button" onClick={() => onOpenEvidenceScene("logs")}>{copy.logs}</button>
              <button type="button" onClick={() => onOpenEvidenceScene("agent_rag")}>{copy.rag}</button>
              <button type="button" onClick={() => onOpenEvidenceScene("java")}>{copy.java}</button>
            </div>
            <p className="incident-conclusion__boundary">{presentation.action_receipt.summary}</p>
            <button
              className="incident-conclusion__technical"
              type="button"
              aria-expanded={technicalDetailsOpen}
              aria-controls="incident-investigation-details"
              onClick={onToggleTechnicalDetails}
            >
              {technicalDetailsOpen ? copy.hideTechnical : copy.technical}
            </button>
          </div>
        </details>
      </article>
    </div>
  );
}

function FollowUpAnswer({
  response,
  locale,
  onOpenCitation,
}: {
  response: IncidentLabFollowUpResponse;
  locale: KnowledgeRagLocale;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
}) {
  const copy = labels[locale];
  const answered = response.answer_state === "answered";
  return (
    <article className="message-bubble message-bubble--assistant incident-chat-answer" aria-label={copy.answerLabel}>
      <p>{response.answer.text}</p>
      {answered ? (
        <details className="incident-chat-disclosure incident-chat-disclosure--turn">
          <summary>
            <span>{copy.evidenceCount(response.citations.length)}</span>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
          </summary>
          <div className="incident-chat-disclosure__body">
            <CitationButtons citations={response.citations} label={copy.sources} onOpenCitation={onOpenCitation} />
            <AgentMovement steps={response.steps} locale={locale} />
            <p className="incident-chat-answer__boundary">{response.answer.boundary}</p>
            <small className="incident-chat-answer__verified">{copy.verified}</small>
          </div>
        </details>
      ) : (
        <p className="incident-chat-answer__bounded">{copy.bounded} · {response.answer.boundary}</p>
      )}
    </article>
  );
}

export default function IncidentFollowUpChat({
  locale,
  run,
  runReference,
  mode,
  technicalDetailsOpen,
  onToggleTechnicalDetails,
  onOpenCitation,
  onOpenEvidenceScene,
}: IncidentFollowUpChatProps) {
  const copy = labels[locale];
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [expired, setExpired] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const latestTurnRef = useRef<HTMLElement | null>(null);
  const pending = turns.some((turn) => turn.pending);
  const latestResponse = lastAnsweredTurn(turns);
  const suggestions = (latestResponse?.suggested_questions ?? INITIAL_SUGGESTIONS[locale]).slice(0, 3);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (turns.length === 0) return;
    latestTurnRef.current?.scrollIntoView?.({
      block: "nearest",
      behavior: window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth",
    });
    if (!pending && mode === "live_ai") inputRef.current?.focus();
  }, [mode, pending, turns.length]);

  async function ask(nextQuestion: string, suggestionId: string | null) {
    const normalized = nextQuestion.trim();
    if (!runReference || !normalized || pending || expired) return;
    if (mode === "recorded_replay" && !suggestionId) return;

    const controller = new AbortController();
    abortRef.current = controller;
    const id = createTurnId();
    setQuestion("");
    setTurns((current) => [...current, {
      id,
      question: normalized,
      suggestionId,
      response: null,
      errorCode: null,
      pending: true,
    }]);

    try {
      const response = await runIncidentLabFollowUp({
        run_reference: runReference,
        client_turn_id: id,
        question: normalized,
        locale,
        confirm_live_ai: mode === "live_ai",
        ...(suggestionId ? { suggestion_id: suggestionId } : {}),
      }, controller.signal);
      setTurns((current) => current.map((turn) => turn.id === id
        ? { ...turn, response, pending: false }
        : turn));
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      const errorCode = error instanceof IncidentApiError
        ? error.code ?? `HTTP_${error.status}`
        : "FOLLOW_UP_UNAVAILABLE";
      const referenceExpired = error instanceof IncidentApiError
        && (error.status === 404 || error.status === 410 || error.code?.includes("EXPIRED"));
      if (referenceExpired) setExpired(true);
      setTurns((current) => current.map((turn) => turn.id === id
        ? { ...turn, errorCode, pending: false }
        : turn));
    } finally {
      abortRef.current = null;
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void ask(question, null);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    void ask(question, null);
  }

  return (
    <section className="incident-answer-chat" aria-label={copy.region}>
      <ConclusionMessage
        run={run}
        locale={locale}
        mode={mode}
        technicalDetailsOpen={technicalDetailsOpen}
        onToggleTechnicalDetails={onToggleTechnicalDetails}
        onOpenEvidenceScene={onOpenEvidenceScene}
      />

      <div className="incident-followup__thread" aria-busy={pending}>
        {turns.map((turn, index) => (
          <article className="incident-followup-turn" key={turn.id} ref={index === turns.length - 1 ? latestTurnRef : undefined}>
            <div className="message-row message-row--user">
              <div className="message-bubble message-bubble--user">{turn.question}</div>
            </div>

            {turn.pending ? (
              <div className="message-row message-row--assistant">
                <span className="message-avatar" aria-hidden="true">N</span>
                <div className="message-bubble message-bubble--assistant incident-followup-wait" role="status">
                  <span className="knowledge-chat-wait__dots" aria-hidden="true"><span /><span /><span /></span>
                  {copy.sending}
                </div>
              </div>
            ) : null}

            {turn.response && !turn.pending ? (
              <div className="message-row message-row--assistant" aria-live="polite" aria-atomic="true">
                <span className="message-avatar" aria-hidden="true">N</span>
                <FollowUpAnswer response={turn.response} locale={locale} onOpenCitation={onOpenCitation} />
              </div>
            ) : null}

            {turn.errorCode && !turn.pending ? (
              <div className="message-row message-row--assistant" aria-live="polite" aria-atomic="true">
                <span className="message-avatar" aria-hidden="true">N</span>
                <div className="message-bubble message-bubble--assistant incident-followup-error">
                  <strong>{expired ? copy.expired : copy.error}</strong>
                  {!expired ? <p>{copy.retry}</p> : null}
                  <code translate="no">{turn.errorCode}</code>
                </div>
              </div>
            ) : null}
          </article>
        ))}
      </div>

      {runReference ? (
        <div className="incident-answer-chat__controls">
          <p>{mode === "recorded_replay" ? copy.exploreReplay : copy.exploreLive}</p>
          <div className="incident-followup__suggestions" aria-label={copy.suggestions}>
            {suggestions.map((suggestion) => (
              <button key={suggestion.id} type="button" disabled={pending || expired} onClick={() => void ask(suggestion.label, suggestion.id)}>
                {suggestion.label}
              </button>
            ))}
          </div>

          {mode === "live_ai" ? (
            <form className="incident-followup__composer" onSubmit={submit}>
              <label className="sr-only" htmlFor="incident-followup-question">{copy.inputLabel}</label>
              <textarea
                id="incident-followup-question"
                ref={inputRef}
                name="incident_followup_question"
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={copy.placeholder}
                disabled={pending || expired}
                maxLength={500}
                rows={1}
                autoComplete="off"
              />
              <button type="submit" disabled={pending || expired || !question.trim()}>
                <span>{pending ? copy.sending : copy.send}</span>
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 12 11-6-3.2 12-2.1-4.1L6 12Zm5.7 1.9L17 6" /></svg>
              </button>
            </form>
          ) : null}
          <small className="incident-followup__note">{mode === "recorded_replay" ? copy.replayNote : copy.liveNote}</small>
        </div>
      ) : (
        <p className="incident-followup-unavailable">{copy.unavailable}</p>
      )}
    </section>
  );
}
