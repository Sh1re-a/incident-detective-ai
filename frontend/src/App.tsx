import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { getDemoWorld, runKnowledgeRag } from "./api/client";
import AgentIncidentThread from "./AgentIncidentThread";
import type {
  DemoWorldRagExample,
  DemoWorldResponse,
  KnowledgeRagLocale,
  KnowledgeRagPhase,
  KnowledgeRagPhaseId,
  KnowledgeRagResponse,
  KnowledgeRankedMatch,
  KnowledgeSafetyReason,
} from "./api/generated";

type Locale = KnowledgeRagLocale;
type RunStage = "idle" | "screening" | "live";
type Scene = "help" | "drift";

const copy = {
  sv: {
    nav: "Kundhjälp",
    lab: "Driftlabb",
    labStatus: "Riktig AI",
    eyebrow: "FIKTIV E-HANDEL · VERKLIG, KONTROLLERAD BACKEND",
    title: "AI som visar vad den vet – och när den måste stanna.",
    lead:
      "Nordly svarar från godkända företagsdokument. Vissa kända typer av känsliga frågor stoppas innan AI:n anropas.",
    assistantLabel: "Nordly assistent",
    sceneTitle: "Fråga Nordly.",
    readOnly: "Kan läsa · kan inte ändra",
    hello:
      "Hej! Jag svarar bara från Nordlys godkända dokument. Fråga om retur eller betalning – eller testa en fråga jag ska stoppa.",
    exampleLead: "Testa en fråga",
    placeholder: "Skriv en fråga till Nordly…",
    send: "Skicka",
    sending: "Skickar",
    screeningWaiting: "Nordly skickar frågan…",
    screeningDetail: "Frågan skickas till backendens säkerhetsgräns.",
    liveWaiting: "Det bekräftade backendanropet är skickat…",
    liveWaitingDetail:
      "Vi väntar på backendens fullständiga kvitto. Inga steg markeras som klara innan det har kommit tillbaka.",
    liveSlow:
      "Det tar lite längre än vanligt – vi väntar fortfarande på backendens kvitto.",
    blockedKicker: "STOPPAD FÖRE AI",
    blockedTitle: "Bra. Systemet stannade.",
    blockedExplain: "Inga dokument, vectorsökningar eller AI-modeller användes.",
    blockedProofTitle: "Säkerhetsgränsen stoppade frågan.",
    proofLead: "Verkligt backendkvitto",
    notRun: "Inte kört",
    notReported: "Inte rapporterat",
    completed: "Klar",
    blocked: "Stoppad",
    failed: "Misslyckades",
    aiCalls: "AI-anrop",
    embeddingCalls: "Embeddings",
    vectorSearch: "Vectorsökningar",
    actions: "Åtgärder",
    cost: "Kostnad",
    none: "Ingen",
    details: "Se var frågan stoppades",
    runId: "Körnings-id",
    latency: "Svarstid",
    askAgain: "Ställ en ny fråga",
    confirmationKicker: "SÄKERHETSGRÄNS GODKÄND",
    confirmationTitle: "Frågan får försöka gå vidare.",
    confirmationSummary:
      "Säkerhetsgrinden tillåter nästa försök. Backenden kontrollerar då live-AI och kunskapsindex innan den kan skapa en embedding, hitta liknande betydelse och be Gemini formulera ett källbundet svar.",
    confirmationBoundary: "Högst 1 embedding · högst 1 AI-svar · 0 skrivverktyg",
    confirmationProof: "0 AI-anrop hittills · ingen AI-kostnad uppstod",
    liveCta: "Kör hela AI-flödet",
    liveNote: "Tar ofta några sekunder och kan medföra en liten providerkostnad.",
    answeredKicker: "SVAR MED VERIFIERADE KÄLLHÄNVISNINGAR",
    answeredTitle: "Nordly svarade från hämtad företagskunskap.",
    claimsTitle: "Det här säger svaret",
    sourceTitle: "HÄMTADE KÄLLOR SOM SVARET HÄNVISAR TILL",
    approvedSource: "Godkänd för kundhjälpen",
    sourceSimilarity: "Semantisk likhet",
    showSource: "Visa källtexten",
    sourceReference: "Källreferens",
    showBehind: "Se hur svaret togs fram",
    hideBehind: "Dölj bakom kulisserna",
    semanticDone: "semantisk sökning utförd",
    aiAnswer: "AI-svar",
    noActions: "0 åtgärder",
    behindKicker: "BAKOM SVARET",
    behindTitle: "Från fråga till kontrollerat svar.",
    behindIntro:
      "Det här visar registrerade backendsteg och returnerade bevis – inte AI:ns dolda tankar.",
    checksTitle: "Kontroller före visning",
    schemaCheck: "Svarformat giltigt",
    citationCheck: "Hänvisningar finns i hämtad kontext",
    documentCheck: "Endast godkända dokument",
    piiCheck: "Persondatakontroll godkänd",
    policyCheck: "Policykontroll godkänd",
    noWriteCheck: "Inga skrivfunktioner",
    receiptTitle: "Tekniskt körningskvitto",
    corpus: "Korpus",
    embedding: "Embedding",
    search: "Sökning",
    topSimilarity: "Bästa semantiska likhet",
    generationModel: "Genereringsmodell",
    providerCalls: "Provideranrop",
    tokens: "Tokens",
    writeTools: "Skrivverktyg",
    verification: "Verifiering",
    providerResponseId: "Provider response-id",
    result: "träff",
    results: "träffar",
    zero: "0",
    costEstimateNote:
      "Listprisestimat för genereringen · embeddingkostnaden ingår inte",
    costNotReported: "Inte rapporterat av providern",
    costNotIncurred: "Ingen AI-kostnad uppstod",
    genericKicker: "BACKENDRESULTAT",
    insufficientTitle: "Nordly hittade inte tillräckligt stöd.",
    insufficientSummary:
      "Systemet avstod från att gissa eftersom de godkända dokumenten inte gav tillräckligt bevis.",
    genericTitle: "Nordly kunde inte visa ett källbundet svar.",
    genericExplain: "Backendens kvitto avgör vad som får visas.",
    loadError: "Nordlys exempel kunde inte hämtas. Du kan fortfarande skriva en fråga.",
    inputBoundary: "Använd bara fiktiva frågor – skriv inga riktiga personuppgifter eller företagshemligheter.",
    runError: "Backenden svarade inte. Försök igen när tjänsten är igång.",
    footer: "Koncept, produktdesign och implementation av Shirwac Abib",
    language: "Språk",
  },
  en: {
    nav: "Customer help",
    lab: "Incident lab",
    labStatus: "Real AI",
    eyebrow: "FICTIONAL COMMERCE · REAL, CONTROLLED BACKEND",
    title: "AI that shows what it knows – and when it must stop.",
    lead:
      "Nordly answers from approved company documents. Some known types of sensitive questions are stopped before AI is called.",
    assistantLabel: "Nordly assistant",
    sceneTitle: "Ask Nordly.",
    readOnly: "Can read · cannot change",
    hello:
      "Hi! I only answer from Nordly’s approved documents. Ask about returns or payments – or try a question I should stop.",
    exampleLead: "Try a question",
    placeholder: "Ask Nordly a question…",
    send: "Send",
    sending: "Sending",
    screeningWaiting: "Nordly is sending the question…",
    screeningDetail: "The question is being sent to the backend safety boundary.",
    liveWaiting: "The confirmed backend request has been sent…",
    liveWaitingDetail:
      "We are waiting for the complete backend receipt. No step is marked complete before it returns.",
    liveSlow:
      "This is taking a little longer than usual – we are still waiting for the backend receipt.",
    blockedKicker: "STOPPED BEFORE AI",
    blockedTitle: "Good. The system stopped.",
    blockedExplain: "No documents, vector searches or AI models were used.",
    blockedProofTitle: "The safety boundary stopped the question.",
    proofLead: "Real backend receipt",
    notRun: "Not run",
    notReported: "Not reported",
    completed: "Done",
    blocked: "Stopped",
    failed: "Failed",
    aiCalls: "AI calls",
    embeddingCalls: "Embeddings",
    vectorSearch: "Vector searches",
    actions: "Actions",
    cost: "Cost",
    none: "None",
    details: "See where the question stopped",
    runId: "Run ID",
    latency: "Response time",
    askAgain: "Ask another question",
    confirmationKicker: "SAFETY CHECK PASSED",
    confirmationTitle: "The question may attempt to continue.",
    confirmationSummary:
      "The safety gate permits the next attempt. The backend then checks live AI and the knowledge index before it can create an embedding, find similar meaning, and ask Gemini for a source-grounded answer.",
    confirmationBoundary: "At most 1 embedding · at most 1 AI answer · 0 write tools",
    confirmationProof: "0 AI calls so far · no AI cost incurred",
    liveCta: "Run the full AI flow",
    liveNote: "Usually takes a few seconds and may incur a small provider cost.",
    answeredKicker: "ANSWER WITH VERIFIED CITATIONS",
    answeredTitle: "Nordly answered from retrieved company knowledge.",
    claimsTitle: "What the answer says",
    sourceTitle: "RETRIEVED SOURCES CITED BY THE ANSWER",
    approvedSource: "Approved for customer help",
    sourceSimilarity: "Semantic similarity",
    showSource: "View source text",
    sourceReference: "Source reference",
    showBehind: "See how this answer was produced",
    hideBehind: "Hide behind the scenes",
    semanticDone: "semantic search completed",
    aiAnswer: "AI answer",
    noActions: "0 actions",
    behindKicker: "BEHIND THE ANSWER",
    behindTitle: "From question to controlled answer.",
    behindIntro:
      "This shows recorded backend steps and returned evidence – not the AI’s hidden thoughts.",
    checksTitle: "Checks before display",
    schemaCheck: "Answer format valid",
    citationCheck: "Citations exist in retrieved context",
    documentCheck: "Approved documents only",
    piiCheck: "Personal-data scan passed",
    policyCheck: "Policy scan passed",
    noWriteCheck: "No write capability",
    receiptTitle: "Technical run receipt",
    corpus: "Corpus",
    embedding: "Embedding",
    search: "Search",
    topSimilarity: "Top semantic similarity",
    generationModel: "Generation model",
    providerCalls: "Provider calls",
    tokens: "Tokens",
    writeTools: "Write tools",
    verification: "Verification",
    providerResponseId: "Provider response ID",
    result: "result",
    results: "results",
    zero: "0",
    costEstimateNote: "List-price estimate for generation · embedding cost is excluded",
    costNotReported: "Not reported by the provider",
    costNotIncurred: "No AI cost was incurred",
    genericKicker: "BACKEND RESULT",
    insufficientTitle: "Nordly did not find enough support.",
    insufficientSummary:
      "The system declined to guess because the approved documents did not provide enough evidence.",
    genericTitle: "Nordly could not show a source-grounded answer.",
    genericExplain: "The backend receipt decides what may be displayed.",
    loadError: "Nordly’s examples could not be loaded. You can still type a question.",
    inputBoundary: "Use fictional questions only – do not enter real personal data or company secrets.",
    runError: "The backend did not respond. Try again when the service is running.",
    footer: "Concept, product design and implementation by Shirwac Abib",
    language: "Language",
  },
} as const;

const phaseLabels: Record<Locale, Record<KnowledgeRagPhaseId, string>> = {
  sv: {
    safety: "Säkerhetsgrind",
    eligibility_filter: "Godkänd kunskap",
    query_embedding: "Embedding",
    vector_search: "Semantisk sökning",
    bounded_context: "RAG-källpaket",
    generation: "AI-svar",
    java_verification: "Java-verifiering",
  },
  en: {
    safety: "Safety gate",
    eligibility_filter: "Approved knowledge",
    query_embedding: "Embedding",
    vector_search: "Semantic search",
    bounded_context: "RAG source package",
    generation: "AI answer",
    java_verification: "Java verification",
  },
};

const safetyGateCategories: Record<
  Locale,
  readonly { label: string; reasons: readonly KnowledgeSafetyReason[] }[]
> = {
  sv: [
    { label: "Personuppgifter", reasons: ["PII_REQUEST"] },
    { label: "Privat lön", reasons: ["EMPLOYEE_COMPENSATION_REQUEST"] },
    { label: "Hemligheter & inloggning", reasons: ["SECRET_REQUEST"] },
    { label: "Försök att kringgå regler", reasons: ["PROMPT_INJECTION"] },
    { label: "Begäran att utföra betalningar eller systemändringar", reasons: ["FINANCIAL_ACTION", "WRITE_ACTION"] },
  ],
  en: [
    { label: "Personal data", reasons: ["PII_REQUEST"] },
    { label: "Private pay", reasons: ["EMPLOYEE_COMPENSATION_REQUEST"] },
    { label: "Secrets & credentials", reasons: ["SECRET_REQUEST"] },
    { label: "Rule-bypass attempts", reasons: ["PROMPT_INJECTION"] },
    { label: "Requests to make payments or system changes", reasons: ["FINANCIAL_ACTION", "WRITE_ACTION"] },
  ],
};

const preferredExamples = ["SAFE", "EMPLOYEE_COMPENSATION"] as const;

function selectExamples(world: DemoWorldResponse | null) {
  if (!world) return [];

  return preferredExamples
    .map((category) =>
      world.rag_examples.find((example) => example.category === category),
    )
    .filter((example): example is DemoWorldRagExample => Boolean(example));
}

function phaseStatus(
  phase: KnowledgeRagPhase,
  labels: (typeof copy)[Locale],
) {
  if (!phase.executed || phase.status === "skipped") return labels.notRun;
  if (phase.status === "blocked") return labels.blocked;
  if (phase.status === "failed") return labels.failed;
  return labels.completed;
}

function StatusIcon({ status }: { status: KnowledgeRagPhase["status"] }) {
  if (status === "blocked" || status === "failed") {
    return <span className="status-icon status-icon--blocked">!</span>;
  }

  if (status === "completed") {
    return <span className="status-icon status-icon--done">✓</span>;
  }

  return <span className="status-icon status-icon--idle">–</span>;
}

function localeCode(locale: Locale) {
  return locale === "sv" ? "sv-SE" : "en-US";
}

function formatNumber(value: number | null, locale: Locale) {
  if (value === null) return copy[locale].notReported;
  return new Intl.NumberFormat(localeCode(locale)).format(value);
}

function formatSimilarity(value: number | null, locale: Locale) {
  if (value === null) return copy[locale].notReported;
  return new Intl.NumberFormat(localeCode(locale), {
    style: "percent",
    maximumFractionDigits: 1,
  }).format(value);
}

function formatCost(result: KnowledgeRagResponse, locale: Locale) {
  if (result.receipt.cost_status === "not_incurred") {
    return copy[locale].none;
  }

  if (result.receipt.estimated_cost_usd === null) {
    return copy[locale].notReported;
  }

  return new Intl.NumberFormat(localeCode(locale), {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 5,
    maximumFractionDigits: 8,
  }).format(result.receipt.estimated_cost_usd);
}

function costExplanation(result: KnowledgeRagResponse, locale: Locale) {
  if (result.receipt.cost_status === "estimated_generation_only") {
    return copy[locale].costEstimateNote;
  }

  if (result.receipt.cost_status === "not_incurred") {
    return copy[locale].costNotIncurred;
  }

  return copy[locale].costNotReported;
}

function citationNumber(match: KnowledgeRankedMatch, matches: KnowledgeRankedMatch[]) {
  return matches.findIndex((candidate) => candidate.evidence_id === match.evidence_id) + 1;
}

function citationNumbers(citationIds: string[], matches: KnowledgeRankedMatch[]) {
  return citationIds
    .map((id) => matches.findIndex((match) => match.evidence_id === id) + 1)
    .filter((index) => index > 0);
}

function phaseNarrative(
  phase: KnowledgeRagPhase,
  result: KnowledgeRagResponse,
  locale: Locale,
) {
  const labels = copy[locale];
  const topMatch = result.retrieval.ranked_matches[0];
  const embedding = result.retrieval.query_embedding;
  const count = result.retrieval.ranked_matches.length;
  const latency =
    phase.latency_ms === null
      ? labels.notReported
      : `${formatNumber(phase.latency_ms, locale)} ms`;

  if (locale === "sv") {
    switch (phase.id) {
      case "safety":
        return {
          title: "Deterministisk kontroll före AI.",
          description:
            "Java söker efter kända mönster för persondata, privat lön, hemligheter och inloggningsuppgifter, försök att kringgå regler samt begäran att utföra betalningar eller systemändringar. Godkänd betyder bara att frågan får försöka fortsätta; en människa måste fortfarande bekräfta backendanropet. Detta demoskydd är inte komplett DLP.",
          meta: `Före dokument · före embedding · före AI · ${latency}`,
        };
      case "eligibility_filter":
        return {
          title: "Bara rätt dokument blev sökbara.",
          description: `Backenden valde ${result.retrieval.eligible_document_count} godkända dokument och ${result.retrieval.eligible_chunk_count} textdelar med rätt åtkomst.`,
          meta: latency,
        };
      case "query_embedding":
        return {
          title: "Frågans betydelse blev en vektor.",
          description: `Embeddingmodellen skapade ${formatNumber(embedding.dimensions, locale)} tal som representerar frågans innebörd.`,
          meta: `${embedding.provider ?? labels.notReported} · ${embedding.model_id ?? labels.notReported} · ${latency}`,
        };
      case "vector_search":
        return {
          title: "Liknande betydelse hittades i pgvector.",
          description: topMatch
            ? `Bästa träffen var “${topMatch.title}” med semantisk likhet ${formatSimilarity(topMatch.similarity, locale)}. Likhet är en rangordning, inte en säkerhetsprocent.`
            : "Frågevektorn jämfördes med godkända textdelar, men ingen träff returnerades över gränsen.",
          meta: `${result.retrieval.backend} · ${latency}`,
        };
      case "bounded_context":
        return {
          title: "RAG byggde ett litet källpaket.",
          description: `Gemini fick frågan och endast ${count} hämtade ${count === 1 ? "textdel" : "textdelar"} – inte hela dokumentbiblioteket.`,
          meta: latency,
        };
      case "generation":
        return {
          title: "Gemini formulerade svaret.",
          description:
            "Modellen fick bara det avgränsade källpaketet och hade inga skrivverktyg.",
          meta: `${result.receipt.model_id ?? labels.notReported} · ${latency}`,
        };
      case "java_verification":
        return {
          title: "Java kontrollerade innan visning.",
          description:
            "Schema, källhänvisningar, dokumentstatus, persondata och policy kontrollerades deterministiskt.",
          meta: latency,
        };
    }
  }

  switch (phase.id) {
    case "safety":
      return {
        title: "Deterministic check before AI.",
        description:
          "Java looks for known patterns involving personal data, private pay, secrets and credentials, attempts to bypass rules, and requests to make payments or system changes. A pass only permits the question to attempt to continue; a human must still confirm the backend request. This demo control is not complete DLP.",
        meta: `Before documents · before embedding · before AI · ${latency}`,
      };
    case "eligibility_filter":
      return {
        title: "Only eligible documents became searchable.",
        description: `The backend selected ${result.retrieval.eligible_document_count} approved documents and ${result.retrieval.eligible_chunk_count} passages with the required access.`,
        meta: latency,
      };
    case "query_embedding":
      return {
        title: "The question’s meaning became a vector.",
        description: `The embedding model created ${formatNumber(embedding.dimensions, locale)} numbers representing the meaning of the question.`,
        meta: `${embedding.provider ?? labels.notReported} · ${embedding.model_id ?? labels.notReported} · ${latency}`,
      };
    case "vector_search":
      return {
        title: "Similar meaning was found in pgvector.",
        description: topMatch
          ? `The top result was “${topMatch.title}” with semantic similarity ${formatSimilarity(topMatch.similarity, locale)}. Similarity is ranking, not a confidence score.`
          : "The question vector was compared with approved passages, but no result above the threshold was returned.",
        meta: `${result.retrieval.backend} · ${latency}`,
      };
    case "bounded_context":
      return {
        title: "RAG built a small source package.",
        description: `Gemini received the question and only ${count} retrieved ${count === 1 ? "passage" : "passages"} – not the full document library.`,
        meta: latency,
      };
    case "generation":
      return {
        title: "Gemini formulated the answer.",
        description:
          "The model received only the bounded source package and had no write tools.",
        meta: `${result.receipt.model_id ?? labels.notReported} · ${latency}`,
      };
    case "java_verification":
      return {
        title: "Java checked the answer before display.",
        description:
          "Schema, citations, document status, personal data, and policy were checked deterministically.",
        meta: latency,
      };
  }
}

function App() {
  const [locale, setLocale] = useState<Locale>("sv");
  const [scene, setScene] = useState<Scene>(() =>
    window.location.hash === "#incident-lab" ? "drift" : "help",
  );
  const [world, setWorld] = useState<DemoWorldResponse | null>(null);
  const [worldError, setWorldError] = useState(false);
  const [question, setQuestion] = useState("");
  const [submittedQuestion, setSubmittedQuestion] = useState("");
  const [submittedLocale, setSubmittedLocale] = useState<Locale>("sv");
  const [result, setResult] = useState<KnowledgeRagResponse | null>(null);
  const [runStage, setRunStage] = useState<RunStage>("idle");
  const [runError, setRunError] = useState(false);
  const [waitIsLong, setWaitIsLong] = useState(false);
  const [xrayOpen, setXrayOpen] = useState(false);
  const liveWaitTimer = useRef<number | null>(null);
  const waitingRef = useRef<HTMLDivElement | null>(null);
  const confirmationButtonRef = useRef<HTMLButtonElement | null>(null);
  const resultHeadingRef = useRef<HTMLHeadingElement | null>(null);
  const proofHeadingRef = useRef<HTMLHeadingElement | null>(null);
  const labels = copy[locale];
  const examples = selectExamples(world);
  const isSubmitting = runStage !== "idle";
  const isBlocked =
    result?.outcome === "refused" && result.safety.decision === "BLOCK";
  const isConfirmation =
    result?.outcome === "confirmation_required" &&
    result.safety.decision === "ALLOW";
  const isAnswered = result?.outcome === "answered" && result.answer !== null;
  const showProof = Boolean(result && (isBlocked || (isAnswered && xrayOpen)));

  useEffect(() => {
    const controller = new AbortController();

    getDemoWorld(controller.signal)
      .then((response) => {
        setWorld(response);
        setWorldError(false);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setWorldError(true);
      });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => {
    const syncSceneFromHash = () => {
      setScene(window.location.hash === "#incident-lab" ? "drift" : "help");
    };

    window.addEventListener("hashchange", syncSceneFromHash);
    return () => window.removeEventListener("hashchange", syncSceneFromHash);
  }, []);

  useEffect(
    () => () => {
      if (liveWaitTimer.current !== null) {
        window.clearTimeout(liveWaitTimer.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (runStage === "idle") return;
    const frame = window.requestAnimationFrame(() => waitingRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [runStage]);

  useEffect(() => {
    if (!result && !runError) return;
    const frame = window.requestAnimationFrame(() => {
      if (isConfirmation) {
        confirmationButtonRef.current?.focus();
        return;
      }
      resultHeadingRef.current?.focus();
    });
    return () => window.cancelAnimationFrame(frame);
  }, [isConfirmation, result, runError]);

  useEffect(() => {
    if (!xrayOpen) return;
    const frame = window.requestAnimationFrame(() => proofHeadingRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [xrayOpen]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || isSubmitting) return;

    setSubmittedQuestion(trimmedQuestion);
    setSubmittedLocale(locale);
    setResult(null);
    setRunError(false);
    setXrayOpen(false);
    setRunStage("screening");

    try {
      const response = await runKnowledgeRag({
        question: trimmedQuestion,
        locale,
        confirm_live_ai: false,
      });
      setResult(response);
      if (response.outcome === "refused" && response.question.redacted) {
        setSubmittedQuestion(response.question.text);
        setQuestion("");
      }
    } catch {
      setRunError(true);
    } finally {
      setRunStage("idle");
    }
  }

  async function startLiveRun() {
    if (!isConfirmation || !submittedQuestion || isSubmitting) return;

    setResult(null);
    setRunError(false);
    setXrayOpen(false);
    setWaitIsLong(false);
    setRunStage("live");
    liveWaitTimer.current = window.setTimeout(() => setWaitIsLong(true), 8000);

    try {
      const response = await runKnowledgeRag({
        question: submittedQuestion,
        locale: submittedLocale,
        confirm_live_ai: true,
      });
      setResult(response);
    } catch {
      setRunError(true);
    } finally {
      if (liveWaitTimer.current !== null) {
        window.clearTimeout(liveWaitTimer.current);
        liveWaitTimer.current = null;
      }
      setRunStage("idle");
      setWaitIsLong(false);
    }
  }

  function resetConversation() {
    if (liveWaitTimer.current !== null) {
      window.clearTimeout(liveWaitTimer.current);
      liveWaitTimer.current = null;
    }
    setQuestion("");
    setSubmittedQuestion("");
    setSubmittedLocale(locale);
    setResult(null);
    setRunError(false);
    setRunStage("idle");
    setWaitIsLong(false);
    setXrayOpen(false);
  }

  const prominentPhaseIds: KnowledgeRagPhaseId[] = [
    "safety",
    "query_embedding",
    "vector_search",
    "generation",
  ];
  const blockedPhases =
    result?.phases.filter((phase) => prominentPhaseIds.includes(phase.id)) ?? [];

  const answerSummary =
    isAnswered && result.answer
      ? locale === "sv"
        ? result.answer.summary_sv
        : result.answer.summary_en
      : "";
  const citedEvidenceIds = new Set(
    isAnswered && result.answer
      ? result.answer.claims.flatMap((claim) => claim.citation_ids)
      : [],
  );
  const citedMatches =
    isAnswered && result
      ? result.retrieval.ranked_matches.filter((match) =>
          citedEvidenceIds.has(match.evidence_id),
        )
      : [];

  function selectScene(nextScene: Scene) {
    setScene(nextScene);
    window.history.replaceState(
      null,
      "",
      nextScene === "drift" ? "#incident-lab" : "#customer-help",
    );
  }

  return (
    <div className="app-shell" id="top">
      <a className="skip-link" href="#main-content">
        {locale === "sv" ? "Hoppa till innehållet" : "Skip to content"}
      </a>
      <header className="site-header">
        <a className="wordmark" href="#top" aria-label="Nordly, start" translate="no">
          NORDLY<span aria-hidden="true">°</span>
        </a>

        <nav className="scene-nav" aria-label={locale === "sv" ? "Scener" : "Scenes"}>
          <a
            className={`scene-nav__item${scene === "help" ? " scene-nav__item--active" : ""}`}
            href="#customer-help"
            aria-current={scene === "help" ? "page" : undefined}
            onClick={(event) => {
              event.preventDefault();
              selectScene("help");
            }}
          >
            {labels.nav}
          </a>
          <a
            className={`scene-nav__item${scene === "drift" ? " scene-nav__item--active" : ""}`}
            href="#incident-lab"
            aria-current={scene === "drift" ? "page" : undefined}
            onClick={(event) => {
              event.preventDefault();
              selectScene("drift");
            }}
          >
            {labels.lab}
            <span className="scene-nav__hint">{labels.labStatus}</span>
          </a>
        </nav>

        <div className="language-switch" role="group" aria-label={labels.language}>
          {(["sv", "en"] as const).map((language) => (
            <button
              className={locale === language ? "is-active" : ""}
              key={language}
              type="button"
              aria-pressed={locale === language}
              onClick={() => setLocale(language)}
            >
              {language.toUpperCase()}
            </button>
          ))}
        </div>
      </header>

      <main id="main-content">
        <div hidden={scene !== "help"}>
        <section className="hero" aria-labelledby="page-title">
          <p className="hero__eyebrow">{labels.eyebrow}</p>
          <h1 id="page-title">{labels.title}</h1>
          <p className="hero__lead">{labels.lead}</p>
        </section>

        <section
          className={`product-stage${showProof ? " has-result" : ""}`}
          aria-labelledby="scene-title"
        >
          <div className="product-stage__glow" aria-hidden="true" />
          <div className="product-card">
            <header className="product-card__header">
              <div className="assistant-identity">
                <span className="assistant-mark" aria-hidden="true">N</span>
                <div>
                  <p>{labels.assistantLabel}</p>
                  <h2 id="scene-title">{labels.sceneTitle}</h2>
                </div>
              </div>
              <span className="read-only-badge">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M7.5 10V7.8a4.5 4.5 0 0 1 9 0V10m-10 0h11a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 17.5 20h-11A1.5 1.5 0 0 1 5 18.5v-7A1.5 1.5 0 0 1 6.5 10Z" />
                </svg>
                {labels.readOnly}
              </span>
            </header>

            <div className="experience-grid">
              <div className="conversation">
                <div className="conversation__body" aria-live="polite">
                  <div className="message-row message-row--assistant">
                    <span className="message-avatar" aria-hidden="true">N</span>
                    <div className="message-bubble message-bubble--assistant">
                      {labels.hello}
                    </div>
                  </div>

                  {submittedQuestion && (
                    <div className="message-row message-row--user">
                      <div className="message-bubble message-bubble--user">
                        {submittedQuestion}
                      </div>
                    </div>
                  )}

                  {isSubmitting && (
                    <div className="waiting-state" role="status" ref={waitingRef} tabIndex={-1}>
                      <span className="waiting-orbit" aria-hidden="true">
                        <span />
                      </span>
                      <div>
                        <strong>
                          {runStage === "live" ? labels.liveWaiting : labels.screeningWaiting}
                        </strong>
                        <p>
                          {runStage === "live"
                            ? waitIsLong
                              ? labels.liveSlow
                              : labels.liveWaitingDetail
                            : labels.screeningDetail}
                        </p>
                      </div>
                    </div>
                  )}

                  {result && isBlocked && (
                    <div className="answer-card answer-card--blocked">
                      <p className="answer-card__kicker">{labels.blockedKicker}</p>
                      <h3 ref={resultHeadingRef} tabIndex={-1}>{labels.blockedTitle}</h3>
                      <p className="answer-card__summary">
                        {locale === "sv" ? result.safety.summary_sv : result.safety.summary_en}
                      </p>
                      <p className="answer-card__explain">{labels.blockedExplain}</p>
                    </div>
                  )}

                  {result && isConfirmation && (
                    <div className="answer-card answer-card--confirmation">
                      <p className="answer-card__kicker">{labels.confirmationKicker}</p>
                      <h3>{labels.confirmationTitle}</h3>
                      <p className="answer-card__summary">{labels.confirmationSummary}</p>
                      <div className="run-boundary" aria-label={labels.confirmationBoundary}>
                        <strong>{labels.confirmationBoundary}</strong>
                        <span>{labels.confirmationProof}</span>
                      </div>
                      <button
                        className="primary-action"
                        type="button"
                        ref={confirmationButtonRef}
                        onClick={startLiveRun}
                      >
                        <span>{labels.liveCta}</span>
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                          <path d="m9 6 6 6-6 6" />
                        </svg>
                      </button>
                      <p className="action-note">{labels.liveNote}</p>
                    </div>
                  )}

                  {result && isAnswered && result.answer && (
                    <div className="answer-card answer-card--answered">
                      <p className="answer-card__kicker">{labels.answeredKicker}</p>
                      <h3 ref={resultHeadingRef} tabIndex={-1}>{labels.answeredTitle}</h3>
                      <p className="answer-card__summary answer-card__summary--lead">
                        {answerSummary}
                      </p>

                      {result.answer.claims.length > 0 && (
                        <div className="claim-group">
                          <p>{labels.claimsTitle}</p>
                          <ul>
                            {result.answer.claims.map((claim, index) => (
                              <li key={`${claim.text_sv}-${index}`}>
                                <span>{locale === "sv" ? claim.text_sv : claim.text_en}</span>
                                <span className="citation-set" aria-label={locale === "sv" ? "Källhänvisningar" : "Citations"}>
                                  {citationNumbers(claim.citation_ids, citedMatches).map(
                                    (citation) => (
                                      <a key={citation} href={`#source-${citation}`}>
                                        [{citation}]
                                      </a>
                                    ),
                                  )}
                                </span>
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}

                      <p className="run-summary">
                        {result.receipt.embedding_calls} embedding · {labels.semanticDone} ·{" "}
                        {result.receipt.generation_calls} {labels.aiAnswer} · {labels.noActions}
                      </p>
                      <button
                        className="xray-toggle"
                        type="button"
                        aria-expanded={xrayOpen}
                        aria-controls="proof-panel"
                        onClick={() => setXrayOpen((open) => !open)}
                      >
                        <span>{xrayOpen ? labels.hideBehind : labels.showBehind}</span>
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                          <path d={xrayOpen ? "m6 14 6-6 6 6" : "m6 10 6 6 6-6"} />
                        </svg>
                      </button>

                      {citedMatches.length > 0 && (
                        <div className="source-group">
                          <p className="source-group__label">{labels.sourceTitle}</p>
                          {citedMatches.map((match) => {
                            const sourceNumber = citationNumber(
                              match,
                              citedMatches,
                            );
                            return (
                              <article className="source-card" id={`source-${sourceNumber}`} key={match.evidence_id}>
                                <header>
                                  <span className="source-index">[{sourceNumber}]</span>
                                  <div>
                                    <h4>{match.title}</h4>
                                    <p>{match.section_heading} · v{match.document_version}</p>
                                  </div>
                                  <span className="source-status">{labels.approvedSource}</span>
                                </header>
                                <p className="source-summary">
                                  {locale === "sv" ? match.display_summary_sv : match.display_summary_en}
                                </p>
                                <p className="source-similarity">
                                  {labels.sourceSimilarity}: <strong>{formatSimilarity(match.similarity, locale)}</strong>
                                </p>
                                <details className="source-details">
                                  <summary>{labels.showSource}</summary>
                                  <p>{match.text}</p>
                                  <dl>
                                    <div>
                                      <dt>{labels.sourceReference}</dt>
                                      <dd>{match.source_ref}</dd>
                                    </div>
                                  </dl>
                                </details>
                              </article>
                            );
                          })}
                        </div>
                      )}

                    </div>
                  )}

                  {result && !isBlocked && !isConfirmation && !isAnswered && (
                    <div className="answer-card answer-card--error">
                      <p className="answer-card__kicker">{labels.genericKicker}</p>
                      <h3 ref={resultHeadingRef} tabIndex={-1}>
                        {result.outcome === "insufficient_evidence"
                          ? labels.insufficientTitle
                          : labels.genericTitle}
                      </h3>
                      <p className="answer-card__summary">
                        {result.error
                          ? locale === "sv"
                            ? result.error.summary_sv
                            : result.error.summary_en
                          : result.outcome === "insufficient_evidence"
                            ? labels.insufficientSummary
                            : labels.genericExplain}
                      </p>
                    </div>
                  )}

                  {runError && (
                    <div className="answer-card answer-card--error">
                      <p className="answer-card__kicker">BACKEND</p>
                      <h3 ref={resultHeadingRef} tabIndex={-1}>{labels.runError}</h3>
                    </div>
                  )}
                </div>

                {!submittedQuestion && (
                  <div className="examples" aria-label={labels.exampleLead}>
                    <p>{labels.exampleLead}</p>
                    <div className="examples__list">
                      {examples.map((example) => (
                        <button
                          key={example.id}
                          type="button"
                          onClick={() =>
                            setQuestion(locale === "sv" ? example.prompt_sv : example.prompt_en)
                          }
                        >
                          {locale === "sv" ? example.prompt_sv : example.prompt_en}
                        </button>
                      ))}
                    </div>
                    {worldError && <p className="inline-note">{labels.loadError}</p>}
                  </div>
                )}

                {!submittedQuestion && (
                  <form className="composer" onSubmit={handleSubmit}>
                    <label className="sr-only" htmlFor="nordly-question">
                      {labels.placeholder}
                    </label>
                    <input
                      id="nordly-question"
                      name="question"
                      type="text"
                      value={question}
                      onChange={(event) => setQuestion(event.target.value)}
                      placeholder={labels.placeholder}
                      disabled={isSubmitting}
                      autoComplete="off"
                      required
                    />
                    <button type="submit" disabled={isSubmitting}>
                      <span>{isSubmitting ? labels.sending : labels.send}</span>
                      <svg viewBox="0 0 24 24" aria-hidden="true">
                        <path d="m6 12 11-6-3.2 12-2.1-4.1L6 12Zm5.7 1.9L17 6" />
                      </svg>
                    </button>
                  </form>
                )}

                {!submittedQuestion && (
                  <p className="input-boundary-note">{labels.inputBoundary}</p>
                )}

                {submittedQuestion && !isSubmitting && (
                  <button className="reset-button" type="button" onClick={resetConversation}>
                    {labels.askAgain}
                  </button>
                )}
              </div>

              {result && isBlocked && showProof && (
                <aside className="proof-panel" id="proof-panel" aria-labelledby="proof-title">
                  <header>
                    <p>{labels.proofLead}</p>
                    <h3 id="proof-title">{labels.blockedProofTitle}</h3>
                  </header>

                  <div className="safety-gate-map" aria-label={locale === "sv" ? "Det säkerhetsgrinden kan stoppa" : "What the safety gate can stop"}>
                    <p>{locale === "sv" ? "STOPPAR FÖRE DOKUMENT, EMBEDDING OCH AI" : "STOPS BEFORE DOCUMENTS, EMBEDDING AND AI"}</p>
                    <div>
                      {safetyGateCategories[locale].map((category) => (
                        <span
                          key={category.label}
                          data-hit={category.reasons.includes(result.safety.reason_code)}
                        >
                          <i aria-hidden="true">{category.reasons.includes(result.safety.reason_code) ? "!" : "·"}</i>
                          {category.label}
                        </span>
                      ))}
                    </div>
                  </div>

                  <ol className="phase-list phase-list--compact">
                    {blockedPhases.map((phase) => (
                      <li key={phase.id} data-status={phase.status}>
                        <StatusIcon status={phase.executed ? phase.status : "skipped"} />
                        <span>{phaseLabels[locale][phase.id]}</span>
                        <strong>{phaseStatus(phase, labels)}</strong>
                      </li>
                    ))}
                  </ol>

                  <dl className="receipt-metrics">
                    <div>
                      <dt>{labels.aiCalls}</dt>
                      <dd>{result.receipt.provider_calls}</dd>
                    </div>
                    <div>
                      <dt>{labels.embeddingCalls}</dt>
                      <dd>{result.receipt.embedding_calls}</dd>
                    </div>
                    <div>
                      <dt>{labels.vectorSearch}</dt>
                      <dd>{result.retrieval.current_vector_search ? "1" : "0"}</dd>
                    </div>
                    <div>
                      <dt>{labels.actions}</dt>
                      <dd>{result.receipt.action_executed ? "1" : "0"}</dd>
                    </div>
                    <div>
                      <dt>{labels.cost}</dt>
                      <dd>{formatCost(result, locale)}</dd>
                    </div>
                  </dl>

                  <details className="receipt-details">
                    <summary>{labels.details}</summary>
                    <ol>
                      {result.phases.map((phase) => (
                        <li key={phase.id}>
                          <span>{phaseLabels[locale][phase.id]}</span>
                          <strong>{phaseStatus(phase, labels)}</strong>
                        </li>
                      ))}
                    </ol>
                    <dl>
                      <div>
                        <dt>{labels.runId}</dt>
                        <dd>{result.run_id}</dd>
                      </div>
                      <div>
                        <dt>{labels.latency}</dt>
                        <dd>{formatNumber(result.receipt.total_latency_ms, locale)} ms</dd>
                      </div>
                    </dl>
                  </details>
                </aside>
              )}

              {result && isAnswered && showProof && (
                <aside className="proof-panel proof-panel--xray" id="proof-panel" aria-labelledby="proof-title">
                  <header>
                    <p>{labels.behindKicker}</p>
                    <h3 id="proof-title" ref={proofHeadingRef} tabIndex={-1}>{labels.behindTitle}</h3>
                    <span>{labels.behindIntro}</span>
                  </header>

                  <ol className="phase-list phase-list--expanded">
                    {result.phases.map((phase) => {
                      const narrative = phaseNarrative(phase, result, locale);
                      return (
                        <li key={phase.id} data-status={phase.status}>
                          <StatusIcon status={phase.executed ? phase.status : "skipped"} />
                          <div>
                            <p>{phaseLabels[locale][phase.id]}</p>
                            <h4>{narrative.title}</h4>
                            <span>{narrative.description}</span>
                            {phase.id === "safety" && (
                              <div className="safety-gate-map safety-gate-map--inline">
                                <p>{locale === "sv" ? "STOPPAR FÖRE AI" : "STOPS BEFORE AI"}</p>
                                <div>
                                  {safetyGateCategories[locale].map((category) => (
                                    <span key={category.label}>
                                      <i aria-hidden="true">·</i>
                                      {category.label}
                                    </span>
                                  ))}
                                </div>
                              </div>
                            )}
                            <small>{narrative.meta}</small>
                          </div>
                          <strong>{phaseStatus(phase, labels)}</strong>
                        </li>
                      );
                    })}
                  </ol>

                  <div className="verification-block">
                    <h4>{labels.checksTitle}</h4>
                    <ul>
                      {[
                        [labels.schemaCheck, result.verification.schema_pass],
                        [labels.citationCheck, result.verification.citations_within_retrieved_context],
                        [labels.documentCheck, result.verification.approved_documents_only],
                        [labels.piiCheck, result.verification.output_pii_scan_pass],
                        [labels.policyCheck, result.verification.output_policy_scan_pass],
                        [labels.noWriteCheck, result.verification.no_write_capability],
                      ].map(([label, passed]) => (
                        <li key={String(label)} data-passed={String(passed)}>
                          <span aria-hidden="true">{passed ? "✓" : "!"}</span>
                          {label}
                        </li>
                      ))}
                    </ul>
                  </div>

                  <details className="receipt-details receipt-details--technical">
                    <summary>{labels.receiptTitle}</summary>
                    <dl>
                      <div>
                        <dt>{labels.runId}</dt>
                        <dd>{result.run_id}</dd>
                      </div>
                      <div>
                        <dt>{labels.corpus}</dt>
                        <dd>{result.retrieval.corpus_version}</dd>
                      </div>
                      <div>
                        <dt>{labels.embedding}</dt>
                        <dd>
                          {result.retrieval.query_embedding.model_id ?? labels.notReported} ·{" "}
                          {result.retrieval.query_embedding.dimensions === null
                            ? labels.notReported
                            : `${result.retrieval.query_embedding.dimensions}D`}
                        </dd>
                      </div>
                      <div>
                        <dt>{labels.search}</dt>
                        <dd>
                          pgvector · exact cosine · {result.retrieval.ranked_matches.length}{" "}
                          {result.retrieval.ranked_matches.length === 1
                            ? labels.result
                            : labels.results}
                        </dd>
                      </div>
                      <div>
                        <dt>{labels.topSimilarity}</dt>
                        <dd>
                          {formatSimilarity(
                            result.retrieval.ranked_matches[0]?.similarity ?? null,
                            locale,
                          )}
                        </dd>
                      </div>
                      <div>
                        <dt>{labels.generationModel}</dt>
                        <dd>{result.receipt.model_id ?? labels.notReported}</dd>
                      </div>
                      <div>
                        <dt>{labels.providerCalls}</dt>
                        <dd>{result.receipt.provider_calls}</dd>
                      </div>
                      <div>
                        <dt>{labels.tokens}</dt>
                        <dd>{formatNumber(result.receipt.token_usage?.total_tokens ?? null, locale)}</dd>
                      </div>
                      <div>
                        <dt>{labels.latency}</dt>
                        <dd>{formatNumber(result.receipt.total_latency_ms, locale)} ms</dd>
                      </div>
                      <div>
                        <dt>{labels.writeTools}</dt>
                        <dd>{labels.zero}</dd>
                      </div>
                      <div>
                        <dt>{labels.actions}</dt>
                        <dd>{labels.zero}</dd>
                      </div>
                      <div>
                        <dt>{labels.verification}</dt>
                        <dd>{result.verification.overall_outcome}</dd>
                      </div>
                      <div>
                        <dt>{labels.providerResponseId}</dt>
                        <dd>{result.receipt.provider_response_id ?? labels.notReported}</dd>
                      </div>
                      <div>
                        <dt>{labels.cost}</dt>
                        <dd>{formatCost(result, locale)}</dd>
                      </div>
                    </dl>
                    <p className="cost-note">{costExplanation(result, locale)}</p>
                  </details>
                </aside>
              )}
            </div>
          </div>
        </section>
        </div>

        <AgentIncidentThread locale={locale} active={scene === "drift"} />
      </main>

      <footer className="site-footer">
        <span>{labels.footer}</span>
        <span>{world?.company.display_name ?? "Nordly Commerce AB"} · 2026</span>
      </footer>
    </div>
  );
}

export default App;
