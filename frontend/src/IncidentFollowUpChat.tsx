import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import { IncidentApiError, runIncidentLabFollowUp } from "./api/client";
import type {
  IncidentLabFollowUpCitation,
  IncidentLabFollowUpClaim,
  IncidentLabFollowUpMode,
  IncidentLabFollowUpResponse,
  IncidentLabFollowUpStep,
  KnowledgeRagLocale,
} from "./api/generated";

interface IncidentFollowUpChatProps {
  locale: KnowledgeRagLocale;
  runReference: string;
  mode: IncidentLabFollowUpMode;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
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
    { id: "how_conclusion", label: "Hur kom du fram till slutsatsen?" },
    { id: "show_sources", label: "Vilka källor vägde tyngst?" },
    { id: "what_unknown", label: "Vad går inte att fastställa?" },
    { id: "customer_impact", label: "Hur påverkades kunderna?" },
    { id: "agent_boundary", label: "Varför fick agenten inte åtgärda?" },
  ],
  en: [
    { id: "how_conclusion", label: "How did you reach that conclusion?" },
    { id: "show_sources", label: "Which sources mattered most?" },
    { id: "what_unknown", label: "What could not be established?" },
    { id: "customer_impact", label: "How were customers affected?" },
    { id: "agent_boundary", label: "Why could the agent not take action?" },
  ],
} as const;

const labels = {
  sv: {
    eyebrow: "FÖLJDFRÅGOR · SAMMA INCIDENTKVITTO",
    title: "Fråga Driftagenten om den här körningen.",
    lead:
      "Agenten får bara svara från körningens frysta loggar, mätvärden, RAG-källor och Java-kontroll. Varje fråga binds till samma körning.",
    inputLabel: "Din fråga om utredningen",
    placeholder: "Fråga fritt om problemet, orsaken, påverkan eller källorna…",
    replayPlaceholder: "Den verifierade reprisen svarar via frågevalen ovan.",
    liveSend: "Fråga med Live AI",
    replaySend: "Välj en verifierad fråga ovan",
    sending: "Kontrollerar kvittot…",
    suggestions: "Du kan fråga",
    liveMode: "Live AI · källbundet svar",
    blockedMode: "Kontrollerad gräns",
    replayMode: "Verifierad återspelning · 0 nya AI-anrop",
    answerLabel: "Svar från Nordly Driftagent",
    problem: "Var finns problemet?",
    cause: "Varför uppstod det?",
    impact: "Vilken påverkan fick det?",
    known: "Det här vet vi",
    unknown: "Det här går inte att fastställa",
    sources: "Källor",
    verified: "Java verifierade svaret mot samma frysta incidentkvitto",
    bounded: "Kontrollen höll svaret inom incidentens gräns",
    boundedReceipt: "AI-anrop · Endast läsning · 0 ändringar",
    readOnly: "Endast läsning · 0 ändringar",
    certainty: "Bedömning",
    journey: "Så kom jag fram till svaret",
    journeyNote: "Registrerade backendsteg – inte AI:ns dolda tankar.",
    error: "Frågan kunde inte besvaras med ett verifierat svar.",
    retry: "Du kan försöka igen utan att starta om incidenten.",
    expired: "Incidentkvittot har gått ut. Historiken visas, men inga nya frågor kan ställas.",
    replayNote: "Reprisen använder fasta verifierade frågor. Starta en liveincident för att skriva fritt.",
  },
  en: {
    eyebrow: "FOLLOW-UPS · SAME INCIDENT RECEIPT",
    title: "Ask the Incident Agent about this run.",
    lead:
      "The agent may only answer from the run's frozen logs, metrics, RAG sources and Java verification. Every question is bound to the same run.",
    inputLabel: "Your question about the investigation",
    placeholder: "Ask freely about the problem, cause, impact or sources…",
    replayPlaceholder: "The verified recording answers through the choices above.",
    liveSend: "Ask with Live AI",
    replaySend: "Choose a verified question above",
    sending: "Checking the receipt…",
    suggestions: "You can ask",
    liveMode: "Live AI · source-grounded answer",
    blockedMode: "Controlled boundary",
    replayMode: "Verified recording · 0 new AI calls",
    answerLabel: "Answer from the Nordly Incident Agent",
    problem: "Where is the problem?",
    cause: "Why did it happen?",
    impact: "What was the impact?",
    known: "What we know",
    unknown: "What cannot be established",
    sources: "Sources",
    verified: "Java verified the answer against the same frozen incident receipt",
    bounded: "The control kept the answer within the incident boundary",
    boundedReceipt: "AI calls · Read-only · 0 changes",
    readOnly: "Read-only · 0 changes",
    certainty: "Assessment",
    journey: "How I reached the answer",
    journeyNote: "Recorded backend steps – not the AI's hidden reasoning.",
    error: "The question could not be answered with a verified response.",
    retry: "You can try again without restarting the incident.",
    expired: "The incident receipt has expired. History remains visible, but no new questions can be asked.",
    replayNote: "The recording uses fixed verified questions. Start a live incident to type freely.",
  },
} as const;

function createTurnId() {
  const uuid = globalThis.crypto?.randomUUID?.();
  if (uuid) return `incident-follow-up-${uuid}`;
  return `incident-follow-up-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function claimsForSection(
  claims: IncidentLabFollowUpClaim[],
  section: IncidentLabFollowUpClaim["section"],
) {
  return claims.filter((claim) => claim.section === section);
}

function SourceLinks({
  claim,
  citations,
  locale,
  onOpenCitation,
}: {
  claim: IncidentLabFollowUpClaim | undefined;
  citations: IncidentLabFollowUpCitation[];
  locale: KnowledgeRagLocale;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
}) {
  const copy = labels[locale];
  if (!claim || claim.citation_ids.length === 0) return null;
  const citationById = new Map(citations.map((citation) => [citation.evidence_id, citation]));
  const linked = claim.citation_ids
    .map((id) => citationById.get(id))
    .filter((citation): citation is IncidentLabFollowUpCitation => citation !== undefined);
  if (linked.length === 0) return null;

  return (
    <span className="incident-followup-sources" aria-label={copy.sources}>
      {linked.map((citation) => (
        <button
          key={citation.evidence_id}
          type="button"
          title={citation.label}
          onClick={() => onOpenCitation(citation)}
        >
          <span>{citation.label}</span>
          <code translate="no">{citation.evidence_id}</code>
        </button>
      ))}
    </span>
  );
}

function AnswerSection({
  title,
  value,
  certainty,
  claim,
  citations,
  locale,
  onOpenCitation,
}: {
  title: string;
  value: string;
  certainty?: string;
  claim: IncidentLabFollowUpClaim | undefined;
  citations: IncidentLabFollowUpCitation[];
  locale: KnowledgeRagLocale;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
}) {
  const copy = labels[locale];
  return (
    <section className="incident-followup-report__section">
      <h6>{title}</h6>
      <p>{value}</p>
      {certainty ? <small><strong>{copy.certainty}:</strong> {certainty}</small> : null}
      <SourceLinks claim={claim} citations={citations} locale={locale} onOpenCitation={onOpenCitation} />
    </section>
  );
}

function AgentMovement({
  steps,
  citations,
  locale,
  onOpenCitation,
}: {
  steps: IncidentLabFollowUpStep[];
  citations: IncidentLabFollowUpCitation[];
  locale: KnowledgeRagLocale;
  onOpenCitation: (citation: IncidentLabFollowUpCitation) => void;
}) {
  const copy = labels[locale];
  const citationById = new Map(citations.map((citation) => [citation.evidence_id, citation]));
  const orderedSteps = [...steps].sort((left, right) => left.sequence - right.sequence);

  if (orderedSteps.length === 0) return null;

  return (
    <section className="incident-followup-journey" aria-label={copy.journey}>
      <header>
        <h6>{copy.journey}</h6>
        <p>{copy.journeyNote}</p>
      </header>
      <ol>
        {orderedSteps.map((step) => {
          const linked = step.evidence_ids
            .map((id) => citationById.get(id))
            .filter((citation): citation is IncidentLabFollowUpCitation => citation !== undefined);
          const completed = step.status === "completed";
          return (
            <li key={`${step.sequence}-${step.code}`} data-status={step.status}>
              <span aria-hidden="true">{completed ? "✓" : "!"}</span>
              <div>
                <small>{String(step.sequence).padStart(2, "0")}</small>
                <p>{step.summary}</p>
                {linked.length > 0 ? (
                  <span className="incident-followup-sources">
                    {linked.slice(0, 2).map((citation) => (
                      <button
                        key={citation.evidence_id}
                        type="button"
                        title={citation.label}
                        onClick={() => onOpenCitation(citation)}
                      >
                        <span>{citation.label}</span>
                        <code translate="no">{citation.evidence_id}</code>
                      </button>
                    ))}
                    {linked.length > 2 ? (
                      <em>+{linked.length - 2} {locale === "sv" ? "källor" : "sources"}</em>
                    ) : null}
                  </span>
                ) : null}
              </div>
            </li>
          );
        })}
      </ol>
    </section>
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
  if (response.answer_state !== "answered") {
    return (
      <article className="message-bubble message-bubble--assistant incident-followup-answer" aria-label={copy.answerLabel}>
        <div className="incident-followup-answer__mode" data-mode={response.mode}>
          <span aria-hidden="true" />
          {response.mode === "live_ai"
            ? `${copy.blockedMode} · ${response.receipt.provider_calls} ${locale === "sv" ? "AI-anrop" : "AI calls"}`
            : copy.replayMode}
        </div>
        <p className="incident-followup-answer__lead">{response.answer.text}</p>
        <p className="incident-followup-answer__boundary">{response.answer.boundary}</p>
        <div className="incident-followup-answer__verification incident-followup-answer__verification--bounded">
          <span aria-hidden="true">!</span>
          <p>
            <strong>{copy.bounded}</strong>
            <small>{response.receipt.provider_calls} {copy.boundedReceipt}</small>
          </p>
        </div>
      </article>
    );
  }
  const problemClaims = claimsForSection(response.claims, "problem_location");
  const causeClaims = claimsForSection(response.claims, "cause");
  const impactClaims = claimsForSection(response.claims, "customer_impact");
  const knownClaims = claimsForSection(response.claims, "known");
  const unknownClaims = claimsForSection(response.claims, "unknown");
  const problemLocation = response.answer.problem_location.service
    ? `${response.answer.problem_location.service} · ${response.answer.problem_location.summary}`
    : response.answer.problem_location.summary;

  return (
    <article className="message-bubble message-bubble--assistant incident-followup-answer" aria-label={copy.answerLabel}>
      <div className="incident-followup-answer__mode" data-mode={response.mode}>
        <span aria-hidden="true" />
        {response.mode === "live_ai" ? copy.liveMode : copy.replayMode}
      </div>
      <p className="incident-followup-answer__lead">{response.answer.text}</p>

      <div className="incident-followup-report">
          <AnswerSection
            title={copy.problem}
            value={problemLocation}
            certainty={response.answer.problem_location.certainty}
            claim={problemClaims[0]}
            citations={response.citations}
            locale={locale}
            onOpenCitation={onOpenCitation}
          />
          <AnswerSection
            title={copy.cause}
            value={response.answer.cause.summary}
            certainty={response.answer.cause.certainty}
            claim={causeClaims[0]}
            citations={response.citations}
            locale={locale}
            onOpenCitation={onOpenCitation}
          />
          <AnswerSection
            title={copy.impact}
            value={response.answer.customer_impact}
            claim={impactClaims[0]}
            citations={response.citations}
            locale={locale}
            onOpenCitation={onOpenCitation}
          />

          <div className="incident-followup-report__knowledge">
            <section>
              <h6>{copy.known}</h6>
              <ul>
                {response.answer.known.map((item, index) => (
                  <li key={`${item}-${index}`}>
                    <span>{item}</span>
                    <SourceLinks
                      claim={knownClaims.find((claim) => claim.text === item) ?? knownClaims[index]}
                      citations={response.citations}
                      locale={locale}
                      onOpenCitation={onOpenCitation}
                    />
                  </li>
                ))}
              </ul>
            </section>
            <section>
              <h6>{copy.unknown}</h6>
              <ul>
                {response.answer.unknown.map((item, index) => (
                  <li key={`${item}-${index}`}>
                    <span>{item}</span>
                    <SourceLinks
                      claim={unknownClaims.find((claim) => claim.text === item) ?? unknownClaims[index]}
                      citations={response.citations}
                      locale={locale}
                      onOpenCitation={onOpenCitation}
                    />
                  </li>
                ))}
              </ul>
            </section>
          </div>
      </div>

      <AgentMovement
        steps={response.steps}
        citations={response.citations}
        locale={locale}
        onOpenCitation={onOpenCitation}
      />
      <p className="incident-followup-answer__boundary">{response.answer.boundary}</p>
      <div className="incident-followup-answer__verification">
        <span aria-hidden="true">✓</span>
        <p><strong>{copy.verified}</strong><small>{copy.readOnly}</small></p>
      </div>
    </article>
  );
}

export default function IncidentFollowUpChat({
  locale,
  runReference,
  mode,
  onOpenCitation,
}: IncidentFollowUpChatProps) {
  const copy = labels[locale];
  const [question, setQuestion] = useState("");
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [expired, setExpired] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const latestTurnRef = useRef<HTMLElement | null>(null);
  const pending = turns.some((turn) => turn.pending);
  const lastResponse = [...turns].reverse().find((turn) => turn.response)?.response ?? null;
  const suggestions = useMemo(
    () => lastResponse?.suggested_questions ?? INITIAL_SUGGESTIONS[locale],
    [lastResponse, locale],
  );

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
    if (!normalized || pending || expired) return;
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
    <section className="incident-followup" aria-labelledby="incident-followup-title">
      <header className="incident-followup__header">
        <p>{copy.eyebrow}</p>
        <h4 id="incident-followup-title">{copy.title}</h4>
        <span>{copy.lead}</span>
      </header>

      <div className="incident-followup__thread" aria-busy={pending}>
        {turns.map((turn, index) => (
          <article
            className="incident-followup-turn"
            key={turn.id}
            ref={index === turns.length - 1 ? latestTurnRef : undefined}
          >
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

      <div className="incident-followup__suggestions" aria-label={copy.suggestions}>
        {suggestions.map((suggestion) => (
          <button
            key={suggestion.id}
            type="button"
            disabled={pending || expired}
            onClick={() => void ask(suggestion.label, suggestion.id)}
          >
            {suggestion.label}
          </button>
        ))}
      </div>

      <form className="incident-followup__composer" onSubmit={submit}>
        <label className="sr-only" htmlFor="incident-followup-question">{copy.inputLabel}</label>
        <textarea
          id="incident-followup-question"
          ref={inputRef}
          name="incident_followup_question"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={mode === "live_ai" ? copy.placeholder : copy.replayPlaceholder}
          disabled={pending || expired || mode === "recorded_replay"}
          maxLength={500}
          rows={1}
          autoComplete="off"
        />
        <button
          type="submit"
          disabled={pending || expired || mode === "recorded_replay" || !question.trim()}
        >
          <span>{pending ? copy.sending : mode === "live_ai" ? copy.liveSend : copy.replaySend}</span>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 12 11-6-3.2 12-2.1-4.1L6 12Zm5.7 1.9L17 6" /></svg>
        </button>
      </form>
      <p className="incident-followup__note">
        {mode === "recorded_replay" ? copy.replayNote : copy.readOnly}
      </p>
    </section>
  );
}
