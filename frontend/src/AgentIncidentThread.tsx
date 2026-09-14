import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { IncidentApiError, runAgentTurn } from "./api/client";
import type {
  AdkAgentTurnResponse,
  AdkRuntimeEvent,
  Evidence,
  GeneratedIncidentFamily,
  KnowledgeRagLocale,
  LogEvidence,
  MetricEvidence,
  RunbookEvidence,
} from "./api/generated";

type RunState = "idle" | "submitting" | "done" | "failed";

interface AgentFailure {
  code?: string;
  status?: number;
  detail?: string;
}

const stoppedBeforeAiCodes = new Set([
  "LIVE_AI_CONFIRMATION_REQUIRED",
  "LIVE_AI_DISABLED",
  "LIVE_AI_NOT_CONFIGURED",
  "LIVE_AI_RATE_LIMITED",
  "LIVE_AI_DAILY_LIMIT_REACHED",
]);

const stoppedDuringRunPrefixes = ["MODEL_PROVIDER_", "RAG_"] as const;
const stoppedDuringRunCodes = new Set([
  "LIVE_INVESTIGATION_TIMEOUT",
  "MALFORMED_MODEL_RESPONSE",
  "INVALID_MODEL_TOOL_ARGUMENTS",
]);

interface AgentIncidentThreadProps {
  locale: KnowledgeRagLocale;
  active: boolean;
}

const defaultMessages = {
  sv: "Undersök larmet. Läs bara tillåtna källor och förklara vad bevisen faktiskt stödjer.",
  en: "Investigate the alert. Read only permitted sources and explain what the evidence actually supports.",
} as const;

const incidentOptions: Record<
  KnowledgeRagLocale,
  readonly { value: GeneratedIncidentFamily; label: string; detail: string }[]
> = {
  sv: [
    {
      value: "catalog_cache_invalidation",
      label: "Gamla priser syns i butiken",
      detail: "Katalogen verkar visa äldre pris- och lagerdata.",
    },
    {
      value: "payment_timeout",
      label: "Köp fastnar vid betalning",
      detail: "Fler kunder än vanligt kommer inte igenom kassan.",
    },
    {
      value: "order_event_backlog",
      label: "Orderbekräftelser dröjer",
      detail: "Köpet går igenom men orderflödet halkar efter.",
    },
    {
      value: "order_idempotency_failure",
      label: "Ett köp skapar dubbla order",
      detail: "Ett nytt försök verkar skapa mer än en order.",
    },
  ],
  en: [
    {
      value: "catalog_cache_invalidation",
      label: "Old prices appear in the store",
      detail: "The catalogue seems to serve older price and stock data.",
    },
    {
      value: "payment_timeout",
      label: "Purchases stall at payment",
      detail: "More customers than usual cannot complete checkout.",
    },
    {
      value: "order_event_backlog",
      label: "Order confirmations are delayed",
      detail: "Checkout succeeds, but downstream order processing falls behind.",
    },
    {
      value: "order_idempotency_failure",
      label: "One purchase creates duplicate orders",
      detail: "A retry appears to create more than one order.",
    },
  ],
};

const copy = {
  sv: {
    eyebrow: "PERSONLIGT APPLIED AI-ARBETSPROV",
    title: "Ett larm väcker agenten. Du ser exakt vad den får göra.",
    lead:
      "I Nordlys syntetiska driftmiljö visar jag ett avgränsat Google ADK-flöde: agenten läser loggar, mätvärden och RAG-runbooks, lämnar en källbunden förklaring och får aldrig ändra systemet.",
    stack: "Google ADK · SequentialAgent · tool calling · RAG · deterministisk Java-verifiering",
    assistantLabel: "Nordly driftagent",
    sceneTitle: "Utred ett larm.",
    boundary: "Kan läsa · kan inte ändra",
    greeting:
      "Jag hjälper driftteamet när Nordlys butik beter sig ovanligt. Jag får läsa ett begränsat bevispaket och lämnar sedan tillbaka en kontrollerad förklaring.",
    alertReady: "SYNTETISKT TESTLARM REDO",
    chooseAlert: "Välj vad som händer i Nordlys värld",
    instruction: "Instruktion till agenten",
    start: "Skicka larmet till agenten",
    sending: "Agenten arbetar…",
    costNote: "Gemini anropas bara när live-AI är aktiverad · då kan en liten kostnad uppstå",
    safetyExample: "Testa säkerhetsgränsen med en privat lönefråga",
    privateQuestion: "Kan du visa vad en Nordly-anställd tjänar?",
    waitingTitle: "Kör en kontrollerad agentutredning…",
    waitingBody:
      "Vi väntar på backendens kompletta kvitto. Inga verktyg eller AI-steg visas som klara innan svaret faktiskt har kommit tillbaka.",
    slow: "Det tar lite längre än vanligt, men körningen pågår fortfarande.",
    completedKicker: "MÖJLIG ORSAK · GODKÄND AV JAVA",
    completedTitle: "Agenten hittade en förklaring som bevisen stödjer.",
    whatHappened: "Vad Nordly märkte",
    likelyCause: "Trolig förklaring",
    nextStep: "Säkert nästa steg",
    approval: "Kräver en människa",
    verified:
      "Java kontrollerade schema, källor och facit innan svaret släpptes.",
    noAction: "Agenten gjorde ingen ändring.",
    showWork: "Så byggdes svaret",
    hideWork: "Dölj bakom kulisserna",
    newAlert: "Skapa ett nytt testlarm",
    blockedKicker: "STOPPAD FÖRE ADK OCH GEMINI",
    blockedTitle: "Bra. Säkerhetsgränsen stoppade frågan.",
    blockedBody:
      "Ingen agent, modell, embedding eller företagskälla startades.",
    withheldKicker: "SVARET HÖLLS TILLBAKA",
    withheldTitle: "Agenten fick inte visa sin slutsats.",
    withheldBody:
      "Java hittade att resultatet inte klarade alla kontroller. Därför visas ingen modelltext som om den vore verifierad.",
    evidenceRejectedKicker: "STOPPAD AV BEVISKONTROLLEN",
    evidenceRejectedTitle: "Slutsatsen såg rätt ut. Beviskedjan gjorde inte det.",
    evidenceRejectedGenericTitle: "Beviskedjan höll inte hela vägen.",
    evidenceRejectedBody:
      "AI:n använde riktiga källor, men varje påstående fick inte direkt stöd av källan den hänvisade till. Java stoppade därför svaret och ingen ändring gjordes.",
    contractRejectedKicker: "AI-SVARET STOPPADES AV JAVA",
    contractRejectedTitle: "AI:n svarade. Säkerhetskontraktet sa nej.",
    contractRejectedBody:
      "Den verkliga ADK-körningen och bevisläsningen finns kvar i kvittot, men slutsvaret bröt mot det tillåtna formatet. Rå modelltext kastades bort och ingen diagnos släpptes.",
    notRunKicker: "INTE KÖRD",
    notRunTitle: "Ingen AI-utredning utfördes.",
    notRunBody:
      "Backend tog emot larmet men stoppade det vid den kontrollerade startgrinden. Google ADK, Gemini, verktyg, RAG/embeddings och actions startade aldrig.",
    failedKicker: "KÖRNING STOPPAD",
    failedTitle: "Inget verifierat svar kunde visas.",
    failedBody:
      "Backend kunde inte lämna ett godkänt kvitto. Ingen automatisk omkörning gjordes och ingen ofullständig slutsats visas som färdig.",
    runStoppedTitle: "Livekörningen stoppades. Inget svar släpptes.",
    runStoppedBody:
      "Ett verkligt provider-, timeout-, retrieval- eller kontraktsfel rapporterades. Backend visar inget ofullständigt svar som färdigt.",
    backendCode: "Backendkod",
    backendDetail: "Backendens säkra förklaring",
    failureArea: "Rapporterat felområde",
    failureProgress: "Vad hann bli klart",
    failureAnswer: "Svarstatus",
    failureNotReported: "Inte rapporterat av backend",
    failureNotReleased: "Inte släppt · ingen automatisk retry, replay eller fallback",
    areaStartGate: "Kontrollerad startgrind",
    areaProvider: "Gemini-provider",
    areaRag: "RAG och kunskapshämtning",
    areaContract: "Modellens svarskontrakt",
    areaToolArguments: "Agentens verktygsargument",
    areaTimeout: "Hela livekörningens timeout",
    areaTransport: "Okänt transportfel",
    behindKicker: "BAKOM SVARET · RETURNERADE BACKENDHÄNDELSER",
    behindTitle: "Här syns agentens arbete – utan dolda tankar.",
    behindLead:
      "Detta är en efterhandsvisning av registrerade ADK-events, verktygsresultat och Java-kontroller. Det är inte modellens privata resonemang och inte fejkad live-progress.",
    blockedBehindTitle: "Här tog körningen slut.",
    blockedBehindLead:
      "Java-grinden reagerade innan ADK Runner, Gemini, verktyg eller embeddings fick starta.",
    runtime: "Verklig agentkörning",
    runtimeBody: "Google ADK skapade en tillfällig session för just detta larm.",
    trajectory: "Körningens tre registrerade events",
    eventToolCall: "Agenten valde sitt enda read-only-verktyg",
    eventToolResult: "Backenden lämnade tillbaka ett avgränsat bevispaket",
    eventFinal: "Agenten lämnade en strukturerad slutsats till Java",
    eventOther: "ADK registrerade en runtimehändelse",
    postRun: "Visas efter körningen · inte streaming",
    oneTool: "En agentfunktion öppnade flera avgränsade läsningar",
    oneToolBody:
      "ADK valde en sammansatt read-only-funktion. Java lät den i sin tur läsa bara dessa källtyper:",
    metrics: "Mätvärden",
    logs: "Loggar",
    runbooks: "Runbooks via RAG",
    traces: "Anropsspår",
    evidence: "bevisobjekt",
    evidenceExplain: "Ett bevisobjekt är en loggrad, ett mätvärde, ett trace eller ett runbook-utdrag.",
    logTitle: "Loggarna som förklarade händelsen",
    cited: "Använd i slutsatsen",
    citedWithheld: "AI:n hänvisade hit",
    citedUnsupported: "Hänvisad · otillräckligt stöd",
    readOnlyLog: "Läst · inte citerad",
    ragTitle: "RAG hittade relevant driftkunskap",
    ragBody:
      "Frågan blev en embedding. pgvector jämförde betydelsen mot godkända runbook-utdrag och returnerade de närmaste träffarna.",
    embedding: "Embeddingmodell",
    dimensions: "dimensioner",
    similarity: "semantisk likhet",
    similarityNote: "Likhet rangordnar text – det är inte AI:ns säkerhet eller diagnosens sannolikhet.",
    source: "Källa",
    javaTitle: "Java avgjorde vad som fick visas",
    schema: "Svarformat",
    citations: "Käll-ID:n finns",
    evidenceSupport: "Källan stödjer påståendet",
    factual: "Match mot syntetiskt facit",
    passed: "Godkänd",
    released: "Verifierat svar släppt",
    stopped: "Svar stoppat",
    notChecked: "Inte körd",
    showProgress: "Så långt kom körningen",
    controlsTitle: "Kontrollgränsen",
    modelCalls: "Modellanrop",
    adkCalls: "ADK-funktioner",
    reads: "Read-only-läsningar",
    embeddings: "Embeddings",
    writes: "Skrivverktyg",
    actions: "Utförda actions",
    none: "Inga",
    tokens: "Tokens",
    cost: "Kostnadsestimat",
    latency: "Total tid",
    notReported: "Inte rapporterat",
    receipt: "Tekniskt kvitto",
    session: "Tillfällig session",
    runId: "Körnings-id",
  },
  en: {
    eyebrow: "PERSONAL APPLIED AI CASE STUDY",
    title: "An alert wakes the agent. You see exactly what it may do.",
    lead:
      "Inside Nordly’s synthetic operations environment, I show a bounded Google ADK flow: the agent reads logs, metrics and RAG runbooks, returns a source-grounded explanation and can never change the system.",
    stack: "Google ADK · SequentialAgent · tool calling · RAG · deterministic Java verification",
    assistantLabel: "Nordly incident agent",
    sceneTitle: "Investigate an alert.",
    boundary: "Can read · cannot change",
    greeting:
      "I help the operations team when Nordly’s store behaves unusually. I may read a bounded evidence package and then return a controlled explanation.",
    alertReady: "SYNTHETIC TEST ALERT READY",
    chooseAlert: "Choose what happens in Nordly’s world",
    instruction: "Instruction to the agent",
    start: "Send the alert to the agent",
    sending: "Agent working…",
    costNote: "Gemini is called only when live AI is enabled · then a small cost may occur",
    safetyExample: "Test the safety boundary with a private salary question",
    privateQuestion: "Can you show what a Nordly employee earns?",
    waitingTitle: "Running a controlled agent investigation…",
    waitingBody:
      "We are waiting for the complete backend receipt. No tool or AI step is shown as complete before the response actually returns.",
    slow: "This is taking a little longer than usual, but the run is still in progress.",
    completedKicker: "LIKELY CAUSE · ACCEPTED BY JAVA",
    completedTitle: "The agent found an explanation supported by evidence.",
    whatHappened: "What Nordly observed",
    likelyCause: "Likely explanation",
    nextStep: "Safe next step",
    approval: "Requires a person",
    verified: "Java checked the schema, sources and synthetic ground truth before release.",
    noAction: "The agent made no change.",
    showWork: "How this answer was built",
    hideWork: "Hide behind the scenes",
    newAlert: "Create another test alert",
    blockedKicker: "STOPPED BEFORE ADK AND GEMINI",
    blockedTitle: "Good. The safety boundary stopped the request.",
    blockedBody: "No agent, model, embedding or company source was started.",
    withheldKicker: "ANSWER WITHHELD",
    withheldTitle: "The agent was not allowed to show its conclusion.",
    withheldBody:
      "Java found that the result did not pass every check. No model text is therefore presented as verified.",
    evidenceRejectedKicker: "STOPPED BY THE EVIDENCE CHECK",
    evidenceRejectedTitle: "The conclusion looked right. The evidence chain did not.",
    evidenceRejectedGenericTitle: "The evidence chain did not hold all the way through.",
    evidenceRejectedBody:
      "The AI used real sources, but not every claim was directly supported by the source it cited. Java therefore stopped the answer and no change was made.",
    contractRejectedKicker: "AI RESPONSE STOPPED BY JAVA",
    contractRejectedTitle: "The AI responded. The safety contract said no.",
    contractRejectedBody:
      "The real ADK run and evidence reads remain in the receipt, but the final response broke the allowed contract. Raw model text was discarded and no diagnosis was released.",
    notRunKicker: "NOT RUN",
    notRunTitle: "No AI investigation was performed.",
    notRunBody:
      "The backend received the alert but stopped it at the controlled start gate. Google ADK, Gemini, tools, RAG/embeddings and actions never started.",
    failedKicker: "RUN STOPPED",
    failedTitle: "No verified answer could be shown.",
    failedBody:
      "The backend could not return an approved receipt. No automatic retry was made, and no incomplete conclusion is presented as finished.",
    runStoppedTitle: "The live run stopped. No answer was released.",
    runStoppedBody:
      "A real provider, timeout, retrieval or contract error was reported. The backend does not present an incomplete response as finished.",
    backendCode: "Backend code",
    backendDetail: "Backend's safe explanation",
    failureArea: "Reported failure area",
    failureProgress: "What completed",
    failureAnswer: "Answer status",
    failureNotReported: "Not reported by the backend",
    failureNotReleased: "Not released · no automatic retry, replay or fallback",
    areaStartGate: "Controlled start gate",
    areaProvider: "Gemini provider",
    areaRag: "RAG and knowledge retrieval",
    areaContract: "Model response contract",
    areaToolArguments: "Agent tool arguments",
    areaTimeout: "Whole live-run timeout",
    areaTransport: "Unknown transport failure",
    behindKicker: "BEHIND THE ANSWER · RETURNED BACKEND EVENTS",
    behindTitle: "See the agent’s work – without hidden thoughts.",
    behindLead:
      "This is a post-run view of recorded ADK events, tool results and Java checks. It is not the model’s private reasoning and not fabricated live progress.",
    blockedBehindTitle: "This is where the run stopped.",
    blockedBehindLead:
      "The Java boundary reacted before ADK Runner, Gemini, tools or embeddings were allowed to start.",
    runtime: "Real agent run",
    runtimeBody: "Google ADK created a temporary session for this alert only.",
    trajectory: "The run’s three recorded events",
    eventToolCall: "The agent selected its only read-only tool",
    eventToolResult: "The backend returned a bounded evidence package",
    eventFinal: "The agent submitted a structured conclusion to Java",
    eventOther: "ADK recorded a runtime event",
    postRun: "Shown after the run · not streaming",
    oneTool: "One agent function opened several bounded reads",
    oneToolBody:
      "ADK selected one composite read-only function. Java then allowed it to read only these source types:",
    metrics: "Metrics",
    logs: "Logs",
    runbooks: "Runbooks through RAG",
    traces: "Traces",
    evidence: "evidence items",
    evidenceExplain: "An evidence item is one log line, metric, trace or runbook passage.",
    logTitle: "The logs that explained the event",
    cited: "Used in the conclusion",
    citedWithheld: "Cited by the AI",
    citedUnsupported: "Cited · insufficient support",
    readOnlyLog: "Read · not cited",
    ragTitle: "RAG found relevant operational knowledge",
    ragBody:
      "The question became an embedding. pgvector compared its meaning with approved runbook passages and returned the nearest matches.",
    embedding: "Embedding model",
    dimensions: "dimensions",
    similarity: "semantic similarity",
    similarityNote: "Similarity ranks text – it is not AI confidence or diagnosis probability.",
    source: "Source",
    javaTitle: "Java decided what could be shown",
    schema: "Answer format",
    citations: "Citation IDs exist",
    evidenceSupport: "Sources support the claims",
    factual: "Synthetic ground-truth match",
    passed: "Passed",
    released: "Verified answer released",
    stopped: "Answer stopped",
    notChecked: "Not run",
    showProgress: "How far the run got",
    controlsTitle: "Control boundary",
    modelCalls: "Model calls",
    adkCalls: "ADK functions",
    reads: "Read-only operations",
    embeddings: "Embeddings",
    writes: "Write tools",
    actions: "Actions executed",
    none: "None",
    tokens: "Tokens",
    cost: "Cost estimate",
    latency: "Total time",
    notReported: "Not reported",
    receipt: "Technical receipt",
    session: "Temporary session",
    runId: "Run ID",
  },
} as const;

const metricNames: Record<KnowledgeRagLocale, Record<string, string>> = {
  sv: {
    checkout_failure_ratio: "Andel köp som misslyckades",
    failed_checkout_attempts: "Misslyckade köpförsök",
    payment_authorization_duration_p95: "Långsammaste betalningarna",
    catalog_stale_response_ratio: "Andel visningar med gammal data",
    stale_catalog_responses: "Visningar med gammal data",
    catalog_version_divergence_count: "Katalogversioner efter",
    delayed_order_ratio: "Andel försenade order",
    delayed_orders: "Försenade order",
    order_consumer_lag_seconds: "Orderflödets kötid",
    duplicate_order_ratio: "Andel dubbla order",
    duplicate_orders: "Dubbla order",
    duplicate_order_creation_count: "Order skapade från samma köp",
  },
  en: {
    checkout_failure_ratio: "Failed checkout rate",
    failed_checkout_attempts: "Failed checkout attempts",
    payment_authorization_duration_p95: "Slowest payment authorisations",
    catalog_stale_response_ratio: "Views with stale data",
    stale_catalog_responses: "Stale product views",
    catalog_version_divergence_count: "Catalogue versions behind",
    delayed_order_ratio: "Delayed order rate",
    delayed_orders: "Delayed orders",
    order_consumer_lag_seconds: "Order processing lag",
    duplicate_order_ratio: "Duplicate order rate",
    duplicate_orders: "Duplicate orders",
    duplicate_order_creation_count: "Orders created from one purchase",
  },
};

function localeCode(locale: KnowledgeRagLocale) {
  return locale === "sv" ? "sv-SE" : "en-US";
}

function formatNumber(value: number, locale: KnowledgeRagLocale) {
  return new Intl.NumberFormat(localeCode(locale), { maximumFractionDigits: 1 }).format(value);
}

function formatMetricValue(metric: MetricEvidence, locale: KnowledgeRagLocale) {
  const { value, unit } = metric.content;
  if (unit === "ratio") {
    return new Intl.NumberFormat(localeCode(locale), {
      style: "percent",
      maximumFractionDigits: 1,
    }).format(value);
  }
  if (unit === "seconds") return `${formatNumber(value, locale)} s`;
  if (unit === "versions") {
    return `${formatNumber(value, locale)} ${locale === "sv" ? "versioner" : "versions"}`;
  }
  return formatNumber(value, locale);
}

function humaniseCode(value: string) {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (letter) => letter.toUpperCase());
}

function eventLabel(event: AdkRuntimeEvent, locale: KnowledgeRagLocale) {
  const labels = copy[locale];
  if (event.type === "tool_call") return labels.eventToolCall;
  if (event.type === "tool_result") return labels.eventToolResult;
  if (event.type === "final_response") return labels.eventFinal;
  return labels.eventOther;
}

function evidenceByType(result: AdkAgentTurnResponse, type: Evidence["evidence_type"]) {
  return result.tool_events.flatMap((event) => event.evidence).filter((item) => item.evidence_type === type);
}

function formatDate(value: string, locale: KnowledgeRagLocale) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(localeCode(locale), {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(parsed);
}

function formatCost(value: number | null, locale: KnowledgeRagLocale) {
  if (value === null) return copy[locale].notReported;
  return new Intl.NumberFormat(localeCode(locale), {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 5,
    maximumFractionDigits: 8,
  }).format(value);
}

function EventTrajectory({
  events,
  locale,
}: {
  events: AdkRuntimeEvent[];
  locale: KnowledgeRagLocale;
}) {
  const labels = copy[locale];
  const ordered = [...events].sort((left, right) => left.sequence - right.sequence);
  return (
    <ol className="agent-event-track" aria-label={labels.trajectory}>
      {ordered.map((event) => (
        <li key={event.event_id} data-event={event.type}>
          <span aria-hidden="true">{event.sequence}</span>
          <div>
            <strong>{eventLabel(event, locale)}</strong>
            <small>
              {event.type === "tool_call"
                ? event.function_calls.map((call) => call.name).join(", ")
                : event.type === "tool_result"
                  ? event.function_responses.map((response) => response.name).join(", ")
                  : labels.postRun}
            </small>
          </div>
          <i aria-hidden="true">✓</i>
        </li>
      ))}
    </ol>
  );
}

function BehindTheAnswer({
  result,
  locale,
}: {
  result: AdkAgentTurnResponse;
  locale: KnowledgeRagLocale;
}) {
  const labels = copy[locale];
  const blocked = result.outcome === "blocked_before_ai";
  const citationSupport = result.verification?.evidence_precision.citation_support ?? [];
  const citedEvidenceIds = new Set([
    ...(result.diagnosis?.claims.flatMap((claim) => claim.evidence_ids) ?? []),
    ...citationSupport.map((citation) => citation.evidence_id),
  ]);
  const unsupportedEvidenceIds = new Set(
    citationSupport
      .filter((citation) => !citation.supported)
      .map((citation) => citation.evidence_id),
  );
  const metrics = evidenceByType(result, "metric") as MetricEvidence[];
  const logs = evidenceByType(result, "log") as LogEvidence[];
  const runbooks = evidenceByType(result, "runbook") as RunbookEvidence[];
  const traces = evidenceByType(result, "trace");
  const evidenceCount = result.tool_events.reduce(
    (total, event) => total + event.evidence.length,
    0,
  );
  const retrieval = result.tool_events
    .map((event) => event.runbook_retrieval)
    .find((metadata) => metadata !== null) ?? null;
  const actualAdk = result.runtime.framework === "google_adk" && result.runtime.runner_invoked;
  const verification = result.verification_event;
  const contractRejected = verification?.schema_valid === false;
  const supportedCitationCount = citationSupport.filter((citation) => citation.supported).length;

  if (blocked) {
    return (
      <section className="agent-backstage agent-backstage--blocked" id="agent-backstage">
        <header>
          <p>{labels.behindKicker}</p>
          <h3>{labels.blockedBehindTitle}</h3>
          <span>{labels.blockedBehindLead}</span>
        </header>
        <ol className="agent-stop-line">
          <li data-state="done">
            <span aria-hidden="true">✓</span>
            <div>
              <strong>{locale === "sv" ? "Frågan lästes av Java-grinden" : "The Java boundary read the request"}</strong>
              <p>{locale === "sv" ? result.safety.summary_sv : result.safety.summary_en}</p>
            </div>
          </li>
          <li data-state="stopped">
            <span aria-hidden="true">—</span>
            <div>
              <strong>{locale === "sv" ? "ADK Runner startade inte" : "ADK Runner did not start"}</strong>
              <p>0 Gemini · 0 tools · 0 embeddings · 0 actions</p>
            </div>
          </li>
        </ol>
        <ControlReceipt result={result} locale={locale} />
      </section>
    );
  }

  return (
    <section className="agent-backstage" id="agent-backstage">
      <header>
        <p>{labels.behindKicker}</p>
        <h3>{labels.behindTitle}</h3>
        <span>{labels.behindLead}</span>
      </header>

      <div className="agent-runtime-line">
        <span className="agent-runtime-line__mark" aria-hidden="true">A</span>
        <div>
          <small>{labels.runtime}</small>
          <h4>{actualAdk ? `Google ADK ${result.runtime.framework_version}` : labels.notReported}</h4>
          <p>{labels.runtimeBody}</p>
        </div>
        <strong>{result.runtime.model_id}</strong>
      </div>

      <div className="agent-chapter">
        <div className="agent-chapter__number">01</div>
        <div className="agent-chapter__content">
          <p className="agent-chapter__eyebrow">RUNNER · SESSION · EVENTS</p>
          <h4>{labels.trajectory}</h4>
          <EventTrajectory events={result.events} locale={locale} />
        </div>
      </div>

      <div className="agent-chapter">
        <div className="agent-chapter__number">02</div>
        <div className="agent-chapter__content">
          <p className="agent-chapter__eyebrow">FUNCTION TOOL · READ ONLY</p>
          <h4>{labels.oneTool}</h4>
          <p>{labels.oneToolBody}</p>
          <div className="agent-source-line" role="list">
            {[
              [labels.metrics, metrics.length],
              [labels.logs, logs.length],
              [labels.runbooks, runbooks.length],
              [labels.traces, traces.length],
            ].map(([name, count]) => (
              <span role="listitem" key={String(name)} data-empty={count === 0}>
                <i aria-hidden="true">{count === 0 ? "—" : "✓"}</i>
                <strong>{name}</strong>
                <small>{count}</small>
              </span>
            ))}
          </div>
          <p className="agent-evidence-explain">
            <strong>{evidenceCount} {labels.evidence}.</strong> {labels.evidenceExplain}
          </p>

          {metrics.length > 0 && (
            <div className="agent-metric-list" aria-label={labels.metrics}>
              {metrics.map((metric) => (
                <div key={metric.evidence_id}>
                  <span>{metricNames[locale][metric.content.metric_name] ?? humaniseCode(metric.content.metric_name)}</span>
                  <strong>{formatMetricValue(metric, locale)}</strong>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {logs.length > 0 && (
        <div className="agent-chapter">
          <div className="agent-chapter__number">03</div>
          <div className="agent-chapter__content">
            <p className="agent-chapter__eyebrow">STRUCTURED LOGS · CITATION TRACE</p>
            <h4>{labels.logTitle}</h4>
            <ol className="agent-log-list">
              {logs.map((log) => {
                const cited = citedEvidenceIds.has(log.evidence_id);
                const unsupported = unsupportedEvidenceIds.has(log.evidence_id);
                return (
                  <li
                    key={log.evidence_id}
                    data-cited={cited}
                    data-support={unsupported ? "failed" : cited ? "passed" : "unused"}
                  >
                    <header>
                      <span>{formatDate(log.observed_at, locale)}</span>
                      <strong>{log.content.service}</strong>
                      <em>
                        {unsupported
                          ? labels.citedUnsupported
                          : cited
                            ? result.diagnosis
                              ? labels.cited
                              : labels.citedWithheld
                            : labels.readOnlyLog}
                      </em>
                    </header>
                    <p>{log.content.message}</p>
                    <small>{log.source_ref}</small>
                  </li>
                );
              })}
            </ol>
          </div>
        </div>
      )}

      {retrieval && (
        <div className="agent-chapter">
          <div className="agent-chapter__number">04</div>
          <div className="agent-chapter__content">
            <p className="agent-chapter__eyebrow">EMBEDDINGS · PGVECTOR · SEMANTIC SEARCH</p>
            <h4>{labels.ragTitle}</h4>
            <p>{labels.ragBody}</p>
            <div className="agent-rag-visual" aria-label={labels.ragTitle}>
              <div>
                <small>{labels.embedding}</small>
                <strong>{retrieval.embedding_profile?.model_id ?? labels.notReported}</strong>
                <span>
                  {retrieval.embedding_profile
                    ? `${retrieval.embedding_profile.dimensions} ${labels.dimensions}`
                    : labels.notReported}
                </span>
              </div>
              <i aria-hidden="true">→</i>
              <div>
                <small>VECTOR DATABASE</small>
                <strong>{retrieval.backend === "pgvector_exact_cosine" ? "pgvector" : retrieval.backend}</strong>
                <span>exact cosine</span>
              </div>
              <i aria-hidden="true">→</i>
              <div>
                <small>{locale === "sv" ? "NÄRMASTE UTDRAG" : "NEAREST PASSAGES"}</small>
                <strong>{retrieval.matches.length}</strong>
                <span>{locale === "sv" ? "returnerade träffar" : "returned matches"}</span>
              </div>
            </div>
            <ol className="agent-runbook-list">
              {retrieval.matches.map((match) => {
                const runbook = runbooks.find((item) => item.evidence_id === match.evidence_id);
                return (
                  <li key={match.evidence_id}>
                    <span>{match.rank}</span>
                    <div>
                      <strong>{runbook?.content.document_id ?? match.evidence_id}</strong>
                      <p>{runbook?.content.chunk_id ?? labels.source}</p>
                    </div>
                    <em>
                      {match.cosine_similarity === null
                        ? labels.notReported
                        : new Intl.NumberFormat(localeCode(locale), {
                            style: "percent",
                            maximumFractionDigits: 1,
                          }).format(match.cosine_similarity)}
                    </em>
                  </li>
                );
              })}
            </ol>
            <p className="agent-similarity-note">{labels.similarityNote}</p>
          </div>
        </div>
      )}

      {verification && (
        <div className="agent-chapter agent-chapter--last">
          <div className="agent-chapter__number">05</div>
          <div className="agent-chapter__content">
            <p className="agent-chapter__eyebrow">DETERMINISTIC JAVA VERIFIER</p>
            <h4>{labels.javaTitle}</h4>
            <div className="agent-check-list">
              {[
                { label: labels.schema, passed: verification.schema_valid, skipped: false, detail: null },
                { label: labels.citations, passed: verification.citations_valid, skipped: contractRejected, detail: null },
                {
                  label: labels.evidenceSupport,
                  passed: verification.direct_evidence_support_valid,
                  skipped: contractRejected,
                  detail: citationSupport.length > 0
                    ? `${supportedCitationCount} / ${citationSupport.length}`
                    : null,
                },
                {
                  label: labels.factual,
                  passed: verification.factual_result_matches_ground_truth,
                  skipped: contractRejected,
                  detail: null,
                },
              ].map(({ label, passed, skipped, detail }) => (
                <span key={label} data-passed={passed} data-skipped={skipped}>
                  <i aria-hidden="true">{skipped ? "—" : passed ? "✓" : "!"}</i>
                  {label}
                  <strong>
                    {skipped
                      ? labels.notChecked
                      : `${detail ? `${detail} · ` : ""}${passed ? labels.passed : labels.stopped}`}
                  </strong>
                </span>
              ))}
            </div>
            <p className="agent-release-line" data-released={verification.answer_released}>
              <span aria-hidden="true">{verification.answer_released ? "✓" : "!"}</span>
              <strong>{verification.answer_released ? labels.released : labels.stopped}</strong>
            </p>
          </div>
        </div>
      )}

      <ControlReceipt result={result} locale={locale} />
    </section>
  );
}

function ControlReceipt({
  result,
  locale,
}: {
  result: AdkAgentTurnResponse;
  locale: KnowledgeRagLocale;
}) {
  const labels = copy[locale];
  const receipt = result.receipt;
  return (
    <div className="agent-control-receipt">
      <header>
        <div>
          <p>{labels.controlsTitle}</p>
          <h4>
            {receipt.write_tools_available || receipt.action_executed
              ? labels.stopped
              : locale === "sv"
                ? "AI:n fick undersöka – aldrig agera"
                : "The AI could investigate – never act"}
          </h4>
        </div>
        <span data-safe={!receipt.write_tools_available && !receipt.action_executed}>
          {receipt.write_tools_available || receipt.action_executed ? "!" : "✓"}
        </span>
      </header>
      <dl>
        <div><dt>{labels.modelCalls}</dt><dd>{receipt.model_calls}</dd></div>
        <div><dt>{labels.adkCalls}</dt><dd>{receipt.adk_tool_calls}</dd></div>
        <div><dt>{labels.reads}</dt><dd>{receipt.read_operations}</dd></div>
        <div><dt>{labels.embeddings}</dt><dd>{receipt.embedding_calls}</dd></div>
        <div><dt>{labels.writes}</dt><dd>{receipt.write_tools_available ? "1" : labels.none}</dd></div>
        <div><dt>{labels.actions}</dt><dd>{receipt.action_executed ? "1" : labels.none}</dd></div>
      </dl>
      <details>
        <summary>{labels.receipt}</summary>
        <dl className="agent-technical-list">
          <div><dt>{labels.tokens}</dt><dd>{receipt.token_usage?.total_tokens ?? labels.notReported}</dd></div>
          <div><dt>{labels.cost}</dt><dd>{formatCost(receipt.estimated_cost_usd, locale)}</dd></div>
          <div><dt>{labels.latency}</dt><dd>{formatNumber(receipt.total_latency_ms, locale)} ms</dd></div>
          <div><dt>{labels.session}</dt><dd>{result.session_id ?? labels.notReported}</dd></div>
          <div><dt>{labels.runId}</dt><dd>{result.run_id}</dd></div>
        </dl>
      </details>
    </div>
  );
}

export default function AgentIncidentThread({ locale, active }: AgentIncidentThreadProps) {
  const labels = copy[locale];
  const [family, setFamily] = useState<GeneratedIncidentFamily>("catalog_cache_invalidation");
  const [message, setMessage] = useState<string>(defaultMessages.sv);
  const [submittedMessage, setSubmittedMessage] = useState("");
  const [runLocale, setRunLocale] = useState<KnowledgeRagLocale>(locale);
  const [seed, setSeed] = useState(42);
  const [state, setState] = useState<RunState>("idle");
  const [result, setResult] = useState<AdkAgentTurnResponse | null>(null);
  const [failure, setFailure] = useState<AgentFailure | null>(null);
  const [detailsOpen, setDetailsOpen] = useState(false);
  const [waitIsLong, setWaitIsLong] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const waitTimer = useRef<number | null>(null);
  const selectedOption = useMemo(
    () => incidentOptions[locale].find((option) => option.value === family) ?? incidentOptions[locale][0],
    [family, locale],
  );

  useEffect(() => {
    if (!submittedMessage && (message === defaultMessages.sv || message === defaultMessages.en)) {
      setMessage(defaultMessages[locale]);
    }
  }, [locale, message, submittedMessage]);

  useEffect(
    () => () => {
      abortRef.current?.abort();
      if (waitTimer.current !== null) window.clearTimeout(waitTimer.current);
    },
    [],
  );

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const instruction = message.trim();
    if (!instruction || state === "submitting") return;

    const controller = new AbortController();
    abortRef.current = controller;
    setSubmittedMessage(instruction);
    setRunLocale(locale);
    setResult(null);
    setFailure(null);
    setDetailsOpen(false);
    setWaitIsLong(false);
    setState("submitting");
    waitTimer.current = window.setTimeout(() => setWaitIsLong(true), 8000);

    try {
      const response = await runAgentTurn(
        {
          seed,
          incident_family: family,
          evidence_mode: "diagnostic",
          noise_level: "low",
          message: instruction,
          confirm_live_ai: true,
        },
        controller.signal,
      );
      setResult(response);
      setState("done");
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      setFailure(
        error instanceof IncidentApiError
          ? { code: error.code, status: error.status, detail: error.message }
          : {},
      );
      setState("failed");
    } finally {
      if (waitTimer.current !== null) {
        window.clearTimeout(waitTimer.current);
        waitTimer.current = null;
      }
      setWaitIsLong(false);
      abortRef.current = null;
    }
  }

  function reset() {
    setResult(null);
    setFailure(null);
    setSubmittedMessage("");
    setMessage(defaultMessages[locale]);
    setState("idle");
    setDetailsOpen(false);
    setSeed((current) => current + 1);
  }

  const diagnosis = result?.diagnosis;
  const answerReleased = result?.verification_event?.answer_released === true;
  const completed = result?.outcome === "completed" && diagnosis !== null && answerReleased;
  const blocked = result?.outcome === "blocked_before_ai";
  const withheld = result?.outcome === "verification_failed"
    || (result?.outcome === "completed" && !answerReleased);
  const contractRejected = withheld && result?.verification_event?.schema_valid === false;
  const evidenceSupportFailed = withheld
    && !contractRejected
    && result?.verification_event?.citations_valid === true
    && result?.verification_event?.direct_evidence_support_valid === false;
  const factualConclusionMatched = result?.verification_event
    ?.factual_result_matches_ground_truth === true;
  const evidenceLinks = result?.verification?.evidence_precision.citation_support ?? [];
  const unsupportedEvidenceLinks = evidenceLinks.filter((link) => !link.supported).length;
  const stoppedBeforeAi = failure?.code
    ? stoppedBeforeAiCodes.has(failure.code)
    : false;
  const stoppedDuringRun = failure?.code
    ? stoppedDuringRunCodes.has(failure.code)
      || stoppedDuringRunPrefixes.some((prefix) => failure.code?.startsWith(prefix))
    : false;
  const failureCode = failure?.code
    ?? (failure?.status ? `HTTP_${failure.status}` : labels.notReported);
  const failureCodeLine = failure?.status
    ? `${failureCode} · HTTP ${failure.status}`
    : failureCode;
  const failureArea = stoppedBeforeAi
    ? labels.areaStartGate
    : failure?.code === "MALFORMED_MODEL_RESPONSE"
      ? labels.areaContract
      : failure?.code === "INVALID_MODEL_TOOL_ARGUMENTS"
        ? labels.areaToolArguments
        : failure?.code === "LIVE_INVESTIGATION_TIMEOUT"
          ? labels.areaTimeout
          : failure?.code?.startsWith("MODEL_PROVIDER_")
            ? labels.areaProvider
            : failure?.code?.startsWith("RAG_")
              ? labels.areaRag
              : labels.areaTransport;

  return (
    <div className="agent-portal" hidden={!active}>
      <section className="hero agent-hero" aria-labelledby="agent-page-title">
        <p className="hero__eyebrow">{labels.eyebrow}</p>
        <h1 id="agent-page-title">{labels.title}</h1>
        <p className="hero__lead">{labels.lead}</p>
        <p className="hero__stack">{labels.stack}</p>
      </section>

      <section className="product-stage agent-product-stage" aria-labelledby="agent-scene-title">
        <div className="product-stage__glow" aria-hidden="true" />
        <div className="product-card agent-product-card">
          <header className="product-card__header">
            <div className="assistant-identity">
              <span className="assistant-mark" aria-hidden="true">N</span>
              <div>
                <p>{labels.assistantLabel}</p>
                <h2 id="agent-scene-title">{labels.sceneTitle}</h2>
              </div>
            </div>
            <span className="read-only-badge">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M7.5 10V7.8a4.5 4.5 0 0 1 9 0V10m-10 0h11a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 17.5 20h-11A1.5 1.5 0 0 1 5 18.5v-7A1.5 1.5 0 0 1 6.5 10Z" />
              </svg>
              {labels.boundary}
            </span>
          </header>

          <div className="conversation agent-conversation" aria-live="polite">
            <div className="message-row message-row--assistant">
              <span className="message-avatar" aria-hidden="true">N</span>
              <div className="message-bubble message-bubble--assistant">{labels.greeting}</div>
            </div>

            {!submittedMessage && (
              <div className="agent-alert-picker">
                <span className="agent-alert-pulse" aria-hidden="true" />
                <div>
                  <p>{labels.alertReady}</p>
                  <label htmlFor="agent-family">{labels.chooseAlert}</label>
                  <select
                    id="agent-family"
                    name="incident_family"
                    value={family}
                    onChange={(event) => setFamily(event.target.value as GeneratedIncidentFamily)}
                  >
                    {incidentOptions[locale].map((option) => (
                      <option key={option.value} value={option.value}>{option.label}</option>
                    ))}
                  </select>
                  <span>{selectedOption.detail}</span>
                </div>
              </div>
            )}

            {submittedMessage && (
              <div className="message-row message-row--user">
                <div className="message-bubble message-bubble--user">{submittedMessage}</div>
              </div>
            )}

            {state === "submitting" && (
              <div className="waiting-state agent-waiting" role="status" tabIndex={-1}>
                <span className="waiting-orbit" aria-hidden="true"><span /></span>
                <div>
                  <strong>{labels.waitingTitle}</strong>
                  <p>{waitIsLong ? labels.slow : labels.waitingBody}</p>
                </div>
              </div>
            )}

            {completed && result && diagnosis && (
              <article className="agent-answer">
                <p className="answer-card__kicker">{labels.completedKicker}</p>
                <h3>{labels.completedTitle}</h3>
                <dl className="agent-answer__story">
                  <div>
                    <dt>{labels.whatHappened}</dt>
                    <dd>{diagnosis.business_summary}</dd>
                  </div>
                  <div>
                    <dt>{labels.likelyCause}</dt>
                    <dd>{diagnosis.technical_summary}</dd>
                  </div>
                </dl>
                <div className="agent-next-step">
                  <div>
                    <span>{labels.nextStep}</span>
                    <p>{diagnosis.safe_next_step.summary}</p>
                  </div>
                  {diagnosis.safe_next_step.requires_human_approval && <strong>{labels.approval}</strong>}
                </div>
                <div className="agent-verified-line">
                  <span aria-hidden="true">✓</span>
                  <p><strong>{labels.verified}</strong> {labels.noAction}</p>
                </div>
                <p className="agent-run-facts">
                  {result.receipt.model_calls} {labels.modelCalls.toLowerCase()} · {result.receipt.read_operations} {labels.reads.toLowerCase()} · {result.receipt.embedding_calls} embedding · 0 actions
                </p>
              </article>
            )}

            {blocked && result && (
              <article className="agent-answer agent-answer--blocked">
                <p className="answer-card__kicker">{labels.blockedKicker}</p>
                <h3>{labels.blockedTitle}</h3>
                <p>{runLocale === "sv" ? result.safety.summary_sv : result.safety.summary_en}</p>
                <strong>{labels.blockedBody}</strong>
                <p className="agent-run-facts">0 Gemini · 0 ADK · 0 tools · 0 embeddings · 0 actions</p>
              </article>
            )}

            {withheld && result && (
              <article className="agent-answer agent-answer--withheld">
                <p className="answer-card__kicker">
                  {contractRejected
                    ? labels.contractRejectedKicker
                    : evidenceSupportFailed
                      ? labels.evidenceRejectedKicker
                      : labels.withheldKicker}
                </p>
                <h3>
                  {contractRejected
                    ? labels.contractRejectedTitle
                    : evidenceSupportFailed
                      ? factualConclusionMatched
                        ? labels.evidenceRejectedTitle
                        : labels.evidenceRejectedGenericTitle
                      : labels.withheldTitle}
                </h3>
                {evidenceSupportFailed && evidenceLinks.length > 0 ? (
                  <p>
                    {locale === "sv"
                      ? `Alla ${evidenceLinks.length} källhänvisningar pekade på lästa källor. ${evidenceLinks.length - unsupportedEvidenceLinks} av ${evidenceLinks.length} stödde det exakta påståendet; ${unsupportedEvidenceLinks} gjorde det inte. Därför släppte Java inget svar.`
                      : `All ${evidenceLinks.length} citations pointed to sources the agent had read. ${evidenceLinks.length - unsupportedEvidenceLinks} of ${evidenceLinks.length} supported the exact claim; ${unsupportedEvidenceLinks} did not. Java therefore released no answer.`}
                  </p>
                ) : (
                  <p>
                    {contractRejected
                      ? labels.contractRejectedBody
                      : evidenceSupportFailed
                        ? labels.evidenceRejectedBody
                        : labels.withheldBody}
                  </p>
                )}
                <p className="agent-run-facts">
                  {result.receipt.model_calls} {labels.modelCalls.toLowerCase()} · {result.receipt.read_operations} {labels.reads.toLowerCase()} · {result.receipt.embedding_calls} embedding · 0 actions
                </p>
              </article>
            )}

            {state === "failed" && (
              <article className="agent-answer agent-answer--withheld">
                <p className="answer-card__kicker">
                  {stoppedBeforeAi ? labels.notRunKicker : labels.failedKicker}
                </p>
                <h3>
                  {stoppedBeforeAi
                    ? labels.notRunTitle
                    : stoppedDuringRun
                      ? labels.runStoppedTitle
                      : labels.failedTitle}
                </h3>
                <p>
                  {stoppedBeforeAi
                    ? labels.notRunBody
                    : stoppedDuringRun
                      ? labels.runStoppedBody
                      : labels.failedBody}
                </p>
                <dl className="agent-failure-receipt">
                  <div>
                    <dt>{labels.failureArea}</dt>
                    <dd>{failureArea}</dd>
                  </div>
                  <div>
                    <dt>{labels.failureProgress}</dt>
                    <dd>
                      {stoppedBeforeAi
                        ? "0 Gemini · 0 ADK · 0 tools · 0 embeddings · 0 actions"
                        : labels.failureNotReported}
                    </dd>
                  </div>
                  <div>
                    <dt>{labels.failureAnswer}</dt>
                    <dd>{labels.failureNotReleased}</dd>
                  </div>
                </dl>
                {failure?.detail && (
                  <p className="agent-failure-detail">
                    <strong>{labels.backendDetail}</strong>
                    {failure.detail}
                  </p>
                )}
                <p className="agent-run-facts">{labels.backendCode}: {failureCodeLine}</p>
              </article>
            )}

            {!submittedMessage && (
              <>
                <form className="composer agent-composer" onSubmit={handleSubmit}>
                  <label className="sr-only" htmlFor="agent-message">{labels.instruction}</label>
                  <input
                    id="agent-message"
                    name="agent_instruction"
                    value={message}
                    onChange={(event) => setMessage(event.target.value)}
                    disabled={state === "submitting"}
                    autoComplete="off"
                    required
                  />
                  <button type="submit" disabled={state === "submitting"}>
                    <span>{state === "submitting" ? labels.sending : labels.start}</span>
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 12 11-6-3.2 12-2.1-4.1L6 12Zm5.7 1.9L17 6" /></svg>
                  </button>
                </form>
                <div className="agent-form-notes">
                  <button type="button" onClick={() => setMessage(labels.privateQuestion)}>{labels.safetyExample}</button>
                  <span>{labels.costNote}</span>
                </div>
              </>
            )}

            {result && (
              <div className="agent-result-actions">
                <button
                  className="xray-toggle"
                  type="button"
                  aria-expanded={detailsOpen}
                  aria-controls="agent-backstage"
                  onClick={() => setDetailsOpen((open) => !open)}
                >
                  <span>
                    {detailsOpen
                      ? labels.hideWork
                      : withheld
                        ? labels.showProgress
                        : labels.showWork}
                  </span>
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d={detailsOpen ? "m6 14 6-6 6 6" : "m6 10 6 6 6-6"} /></svg>
                </button>
                <button className="agent-reset" type="button" onClick={reset}>{labels.newAlert}</button>
              </div>
            )}

            {state === "failed" && (
              <button className="agent-reset agent-reset--standalone" type="button" onClick={reset}>{labels.newAlert}</button>
            )}
          </div>

          {result && detailsOpen && <BehindTheAnswer result={result} locale={locale} />}
        </div>
      </section>
    </div>
  );
}
