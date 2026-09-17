import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import {
  getDemoWorld,
  getKnowledgeDocuments,
  IncidentApiError,
  runDemoCustomerChatTurn,
} from "./api/client";
import IncidentLabPortal from "./IncidentLabPortal";
import {
  buildCustomerChatConversation,
  clearCustomerChatSession,
  loadCustomerChatSession,
  saveCustomerChatSession,
} from "./customerChatSession";
import type { CustomerChatTurn } from "./customerChatSession";
import type {
  DemoOrderCatalogResponse,
  DemoOrderLookupResponse,
  DemoCustomerChatSource,
  DemoCustomerChatTurnResponse,
  DemoWorldRagExample,
  DemoWorldResponse,
  KnowledgeDocumentLibraryDocument,
  KnowledgeDocumentLibraryResponse,
  KnowledgeRagLocale,
  KnowledgeRagPhase,
  KnowledgeRagPhaseId,
  KnowledgeRagResponse,
  KnowledgeRankedMatch,
  KnowledgeSafetyReason,
} from "./api/generated";

type Locale = KnowledgeRagLocale;
type Scene = "help" | "drift" | "documents";
type DocumentGroup = "eligible" | "protected" | "excluded";
type SelectedDocument = {
  documentId: string;
  chunkId: string;
};

const copy = {
  sv: {
    creatorName: "Shirwac Abib",
    creatorRole: "Applied AI",
    nav: "Fråga Nordly",
    navStatus: "RAG",
    lab: "Utred larm",
    labStatus: "ADK",
    documents: "Dokumentarkiv",
    documentsStatus: "DOK",
    eyebrow: "PERSONLIGT APPLIED AI-ARBETSPROV",
    title: "AI som visar vad den vet – och när den måste stanna.",
    lead:
      "Jag byggde Nordly Commerce som en fiktiv företagsmiljö där RAG hittar rätt kunskap och visar sina källor. Kända högriskfrågor stoppas före AI, och affärsändringar är tekniskt omöjliga eftersom skrivverktyg saknas.",
    stack: "Java · Spring Boot · Gemini embeddings · semantic search · PostgreSQL + pgvector",
    assistantLabel: "Nordly Kundhjälp",
    sceneTitle: "Fråga Nordly.",
    readOnly: "Endast läsning",
    hello:
      "Hej! Jag kan kontrollera din order och förklara Nordlys regler för leverans, retur och återbetalning. Jag kan läsa information, men aldrig ändra en order.",
    orderExample: "Var är min order?",
    orderExampleType: "Orderstatus från backend",
    cancelExample: "Jag vill avbryta min order",
    cancelExampleType: "Testa assistentens befogenhet",
    refundExample: "Kan jag få pengarna tillbaka?",
    refundExampleType: "Policy med RAG",
    orderWaiting: "Jag hämtar ordern…",
    orderWaitingDetail:
      "Säkerhetskontrollen är klar. Nu görs en exakt, skrivskyddad läsning – ingen AI behöver gissa.",
    orderKicker: "SYNTETISK DEMOORDER · DIREKT FRÅN BACKEND",
    orderUpdated: "Senast uppdaterad",
    orderDelivery: "Beräknad leverans",
    orderNext: "Vad händer nu?",
    orderTruth: "Fiktiv order · inga personuppgifter · inga ändringar",
    orderNotFoundTitle: "Jag hittar inte den demoordern.",
    orderNotFoundBody:
      "Kontrollera numret eller prova NORD-2048. Jag vill inte gissa på en orderstatus.",
    orderUnavailableTitle: "Jag kommer inte åt ordertjänsten just nu.",
    orderUnavailableBody: "Försök igen om en stund.",
    orderBackstageKicker: "BAKOM ORDERSTATUSEN",
    orderBackstageTitle: "Så behandlades orderfrågan.",
    orderBackstageIntro:
      "Frågan kontrollerades först. Därefter hämtades exakt orderdata från en versionshanterad, syntetisk backendkälla.",
    orderBackstageErrorIntro:
      "Frågan kontrollerades, men ordertjänsten kunde inte lämna ett godkänt svar.",
    orderSafetyStep: "Java kontrollerade frågan",
    orderLookupStep: "Read-only tool hämtade ordern",
    orderResponseStep: "UI översatte backenddata",
    orderReceipt: "Körningskvitto",
    orderSource: "Backendkälla",
    orderSnapshot: "Fryst ögonblicksbild",
    orderReadSingular: "läsning",
    orderReadPlural: "läsningar",
    orderWriteSingular: "skrivning",
    orderWritePlural: "skrivningar",
    orderActionExecuted: "åtgärd utförd",
    exampleLead: "Välj ett sätt att utmana systemet",
    exampleGrounded: "Källbelagt svar",
    exampleNoMatch: "Inget svarsunderlag",
    exampleBlocked: "Säkerhetsstopp",
    libraryLabel: "Företagets kunskapsbas",
    libraryFallback: "Dokumentbiblioteket kunde inte hämtas",
    approvedDocuments: "godkända dokument",
    searchablePassages: "sökbara avsnitt",
    placeholder: "Skriv en fråga till Nordly…",
    send: "Skicka",
    sending: "Skickar",
    screeningWaiting: "Jag kontrollerar frågan…",
    screeningDetail: "Frågan skickas till backendens säkerhetsgräns.",
    liveWaiting: "Jag tolkar frågan och hämtar rätt underlag…",
    liveWaitingDetail:
      "Vi väntar på backendens fullständiga kvitto. Inga steg markeras som klara innan det har kommit tillbaka.",
    liveSlow:
      "Det tar lite längre än vanligt – vi väntar fortfarande på backendens kvitto.",
    blockedKicker: "SÄKERHETSSTOPP",
    blockedTitle: "Jag kan inte hjälpa till med det.",
    blockedExplain: "Frågan stoppades innan dokument eller AI användes, så inga känsliga uppgifter lämnades ut.",
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
    confirmationKicker: "TILLÅTEN FRÅGA",
    confirmationTitle: "Jag kan söka i Nordlys godkända dokument. Vill du att jag gör det?",
    confirmationSummary:
      "Sökningen använder bara företagets godkända källor.",
    confirmationBoundary: "Nordly får läsa och svara – aldrig ändra något.",
    confirmationProof: "0 AI-anrop hittills · ingen AI-kostnad uppstod",
    liveCta: "Ja, sök",
    liveNote: "Det gör 1 avgränsat AI-anrop.",
    answeredKicker: "DOKUMENTSTÖD HITTAT",
    answeredTitle: "Nordly hittade rätt företagsdokument.",
    claimsTitle: "Det här säger svaret",
    sourceTitle: "HÄMTADE KÄLLOR SOM SVARET HÄNVISAR TILL",
    approvedSource: "Godkänd för kundhjälpen",
    sourceSimilarity: "Semantisk likhet",
    similarityNote: "0–1 rangordning · inte ett sannings- eller konfidensmått",
    showSource: "Visa källtexten",
    sourceReference: "Källreferens",
    openDocument: "Öppna hela dokumentet",
    unavailableDocument: "Dokumentet är inte tillgängligt",
    retrievedPassage: "RAG hämtade denna passage",
    documentOriginal: "Originaldokument · backendägt innehåll",
    syntheticDocument: "AI-SKAPAT DEMODOKUMENT · FIKTIVT FÖRETAG",
    closeDocument: "Stäng dokumentet",
    documentVersion: "Version",
    documentOwner: "Dokumentägare",
    documentEffective: "Gäller från",
    documentAccess: "Åtkomst",
    documentHash: "Innehållshash",
    documentUnavailableBody:
      "Det exakta dokumentet kunde inte kopplas till biblioteket. Ingen ersättningstext visas.",
    flowLabel: "Registrerade backendsteg",
    flowSafety: "Kontrollera fråga",
    flowSafetyTech: "Java guardrail",
    flowEmbedding: "Tolka innebörd",
    flowEmbeddingTech: "Gemini embedding",
    flowSearch: "Hitta passage",
    flowSearchTech: "pgvector",
    flowContext: "Avgränsa källor",
    flowContextTech: "RAG",
    flowGeneration: "Formulera svar",
    flowGenerationTech: "Gemini",
    flowVerification: "Verifiera svaret",
    flowVerificationTech: "Java-kontroll",
    allowedNoMatchKicker: "INGET SVARSUNDERLAG",
    allowedNoMatchTitle: "Jag hittar inget tillräckligt säkert svar i Nordlys godkända dokument, så jag vill inte gissa.",
    allowedNoMatchExplain:
      "Frågan var tillåten, men ingen godkänd passage nådde backendens gräns. Därför skapades inget AI-svar.",
    counterEmbedding: "Embedding",
    counterSearch: "Vectorsökning",
    counterGeneration: "AI-svar",
    counterActions: "Åtgärder",
    yes: "Ja",
    no: "Nej",
    backendReceipt: "Backendkvitto",
    safetyReason: "Orsak",
    backendCode: "Backendkod",
    noHiddenThoughts: "Registrerade steg · inte AI:ns dolda tankar",
    showBehind: "Så gick det till",
    hideBehind: "Dölj förklaringen",
    customerBehindTitle: "Så kom svaret fram.",
    customerBehindIntro:
      "Det här är backendens registrerade steg, källor och kvitto – inte AI:ns dolda tankar.",
    customerSteps: "Registrerade steg",
    customerSources: "Källor som användes",
    customerReceipt: "Körningskvitto",
    customerReadOperations: "Backendläsningar",
    customerProviderCalls: "AI-anrop",
    customerVectorSearches: "Vectorsökningar",
    customerBusinessWrites: "Ändringar",
    customerLatency: "Svarstid",
    customerNoRag: "RAG behövdes inte – exakt backenddata lästes direkt.",
    customerRagPending: "RAG väntar på ditt godkännande och har inte körts ännu.",
    customerRagUsed: "RAG sökte semantiskt i godkända företagsdokument.",
    customerActionStopped:
      "Åtgärden stoppades, men systemet fortsatte med säkra läsningar för att ge ett användbart svar.",
    customerNoWrites: "Ingen order, retur eller betalning ändrades.",
    customerOpenSource: "Öppna dokumentet",
    customerSourceUnavailable: "Källan finns i kvittot men kan inte öppnas här.",
    customerConfirmationCta: "Sök i dokumenten",
    customerConfirmationNote: "Ett avgränsat live-RAG-anrop körs först när du godkänner.",
    customerOrder: "Order",
    customerStatus: "Status",
    customerDelivery: "Beräknad leverans",
    customerTranscript: "Konversation med Nordly Kundhjälp",
    customerLocalSession: "Lokal chattsession · sparas i den här fliken i 2 timmar",
    customerNewConversation: "Ny konversation",
    customerAppliesTo: "Gäller",
    customerTryNext: "Fortsätt fråga",
    customerStoppedBeforeData:
      "Frågan stoppades innan kunddata, dokument eller AI användes.",
    customerNoTools:
      "Inga verktyg eller AI behövdes för att avgränsa svaret.",
    customerDownstreamStopped:
      "AI:n tolkade frågan. Därefter stoppade systemet den valda informationshämtningen innan något verktyg hann läsa data.",
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
    runError: "Jag kommer inte åt Nordlys tjänst just nu. Försök igen om en stund.",
    footer: "Skapat av Shirwac Abib · Personligt Applied AI-arbetsprov",
    fictionalWorld: "Fiktiv miljö · endast syntetisk data",
    language: "Språk",
    archiveTitle: "Nordlys dokumentarkiv.",
    archiveLead:
      "Här finns källorna som assistenten får använda för att svara – och de källor som systemet håller utanför AI.",
    archiveStatusEligible: "Används av AI",
    archiveStatusProtected: "Skyddad källa",
    archiveStatusExcluded: "Ej aktiv källa",
    archiveOpen: "Öppna",
    archiveOpenAria: "Öppna dokument",
    archiveLoading: "Hämtar dokumentregistret direkt från backenden…",
    archiveLoadError: "Dokumentregistret kunde inte hämtas från backenden.",
    archiveLoadErrorDetail: "Ingen lokal eller hårdkodad reservlista visas.",
  },
  en: {
    creatorName: "Shirwac Abib",
    creatorRole: "Applied AI",
    nav: "Ask Nordly",
    navStatus: "RAG",
    lab: "Investigate",
    labStatus: "ADK",
    documents: "Document archive",
    documentsStatus: "DOCS",
    eyebrow: "PERSONAL APPLIED AI CASE STUDY",
    title: "AI that shows what it knows – and when it must stop.",
    lead:
      "I built Nordly Commerce as a fictional company environment where RAG finds the right knowledge and shows its sources. Known high-risk requests stop before AI, and business changes are technically impossible because no write tools exist.",
    stack: "Java · Spring Boot · Gemini embeddings · semantic search · PostgreSQL + pgvector",
    assistantLabel: "Nordly Customer Care",
    sceneTitle: "Ask Nordly.",
    readOnly: "Read only",
    hello:
      "Hi! I can check your order and explain Nordly's delivery, return and refund rules. I can read information, but I can never change an order.",
    orderExample: "Where is my order?",
    orderExampleType: "Order status from backend",
    cancelExample: "I want to cancel my order",
    cancelExampleType: "Test the assistant's authority",
    refundExample: "Can I get my money back?",
    refundExampleType: "Policy with RAG",
    orderWaiting: "I’m retrieving the order…",
    orderWaitingDetail:
      "The safety check is complete. An exact read-only lookup is running – no AI needs to guess.",
    orderKicker: "SYNTHETIC DEMO ORDER · DIRECTLY FROM THE BACKEND",
    orderUpdated: "Last updated",
    orderDelivery: "Estimated delivery",
    orderNext: "What happens next?",
    orderTruth: "Fictional order · no personal data · no changes",
    orderNotFoundTitle: "I cannot find that demo order.",
    orderNotFoundBody:
      "Check the number or try NORD-2048. I will not guess an order status.",
    orderUnavailableTitle: "I cannot reach the order service right now.",
    orderUnavailableBody: "Please try again in a moment.",
    orderBackstageKicker: "BEHIND THE ORDER STATUS",
    orderBackstageTitle: "How the order question was handled.",
    orderBackstageIntro:
      "The question was checked first. Exact order data was then read from a versioned, synthetic backend source.",
    orderBackstageErrorIntro:
      "The question was checked, but the order service could not return an approved answer.",
    orderSafetyStep: "Java checked the question",
    orderLookupStep: "Read-only tool retrieved the order",
    orderResponseStep: "UI translated backend data",
    orderReceipt: "Run receipt",
    orderSource: "Backend source",
    orderSnapshot: "Frozen snapshot",
    orderReadSingular: "read",
    orderReadPlural: "reads",
    orderWriteSingular: "write",
    orderWritePlural: "writes",
    orderActionExecuted: "action executed",
    exampleLead: "Choose how to challenge the system",
    exampleGrounded: "Source-grounded answer",
    exampleNoMatch: "No answer evidence",
    exampleBlocked: "Safety stop",
    libraryLabel: "Company knowledge base",
    libraryFallback: "The document library could not be loaded",
    approvedDocuments: "approved documents",
    searchablePassages: "searchable passages",
    placeholder: "Ask Nordly a question…",
    send: "Send",
    sending: "Sending",
    screeningWaiting: "I’m checking the question…",
    screeningDetail: "The question is being sent to the backend safety boundary.",
    liveWaiting: "I’m interpreting the question and retrieving the right evidence…",
    liveWaitingDetail:
      "We are waiting for the complete backend receipt. No step is marked complete before it returns.",
    liveSlow:
      "This is taking a little longer than usual – we are still waiting for the backend receipt.",
    blockedKicker: "SAFETY STOP",
    blockedTitle: "I can’t help with that.",
    blockedExplain: "The question was stopped before documents or AI were used, so no sensitive information was disclosed.",
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
    confirmationKicker: "ALLOWED QUESTION",
    confirmationTitle: "I can search Nordly’s approved documents. Would you like me to do that?",
    confirmationSummary:
      "The search only uses the company’s approved sources.",
    confirmationBoundary: "Nordly may read and answer – never change anything.",
    confirmationProof: "0 AI calls so far · no AI cost incurred",
    liveCta: "Yes, search",
    liveNote: "This makes 1 bounded AI request.",
    answeredKicker: "DOCUMENT SUPPORT FOUND",
    answeredTitle: "Nordly found the right company document.",
    claimsTitle: "What the answer says",
    sourceTitle: "RETRIEVED SOURCES CITED BY THE ANSWER",
    approvedSource: "Approved for customer help",
    sourceSimilarity: "Semantic similarity",
    similarityNote: "0–1 ranking · not a truth or confidence measure",
    showSource: "View source text",
    sourceReference: "Source reference",
    openDocument: "Open the full document",
    unavailableDocument: "Document unavailable",
    retrievedPassage: "RAG retrieved this passage",
    documentOriginal: "Original document · backend-owned content",
    syntheticDocument: "AI-GENERATED DEMO DOCUMENT · FICTIONAL COMPANY",
    closeDocument: "Close document",
    documentVersion: "Version",
    documentOwner: "Document owner",
    documentEffective: "Effective from",
    documentAccess: "Access",
    documentHash: "Content hash",
    documentUnavailableBody:
      "The exact document could not be linked to the library. No replacement text is shown.",
    flowLabel: "Recorded backend steps",
    flowSafety: "Check question",
    flowSafetyTech: "Java guardrail",
    flowEmbedding: "Interpret meaning",
    flowEmbeddingTech: "Gemini embedding",
    flowSearch: "Find passage",
    flowSearchTech: "pgvector",
    flowContext: "Bound the sources",
    flowContextTech: "RAG",
    flowGeneration: "Formulate answer",
    flowGenerationTech: "Gemini",
    flowVerification: "Verify the answer",
    flowVerificationTech: "Java check",
    allowedNoMatchKicker: "NO ANSWER EVIDENCE",
    allowedNoMatchTitle: "I cannot find a sufficiently supported answer in Nordly’s approved documents, so I will not guess.",
    allowedNoMatchExplain:
      "The question was allowed, but no approved passage reached the backend threshold. No AI answer was generated.",
    counterEmbedding: "Embedding",
    counterSearch: "Vector search",
    counterGeneration: "AI answer",
    counterActions: "Actions",
    yes: "Yes",
    no: "No",
    backendReceipt: "Backend receipt",
    safetyReason: "Reason",
    backendCode: "Backend code",
    noHiddenThoughts: "Recorded steps · not the AI’s hidden thoughts",
    showBehind: "How it worked",
    hideBehind: "Hide explanation",
    customerBehindTitle: "How the answer was produced.",
    customerBehindIntro:
      "These are recorded backend steps, sources and receipts – not the AI's hidden thoughts.",
    customerSteps: "Recorded steps",
    customerSources: "Sources used",
    customerReceipt: "Run receipt",
    customerReadOperations: "Backend reads",
    customerProviderCalls: "AI calls",
    customerVectorSearches: "Vector searches",
    customerBusinessWrites: "Changes",
    customerLatency: "Response time",
    customerNoRag: "RAG was not needed – exact backend data was read directly.",
    customerRagPending: "RAG is waiting for your approval and has not run yet.",
    customerRagUsed: "RAG searched approved company documents semantically.",
    customerActionStopped:
      "The action was stopped, but the system continued with safe reads to provide a useful answer.",
    customerNoWrites: "No order, return or payment was changed.",
    customerOpenSource: "Open document",
    customerSourceUnavailable: "The source is in the receipt but cannot be opened here.",
    customerConfirmationCta: "Search the documents",
    customerConfirmationNote: "One bounded live RAG request runs only after you approve it.",
    customerOrder: "Order",
    customerStatus: "Status",
    customerDelivery: "Estimated delivery",
    customerTranscript: "Conversation with Nordly Customer Care",
    customerLocalSession: "Local chat session · saved in this tab for 2 hours",
    customerNewConversation: "New conversation",
    customerAppliesTo: "Applies to",
    customerTryNext: "Keep asking",
    customerStoppedBeforeData:
      "The question was stopped before customer data, documents or AI were used.",
    customerNoTools:
      "No tools or AI were needed to scope this answer.",
    customerDownstreamStopped:
      "AI interpreted the question. The system then stopped the selected information retrieval before any tool could read data.",
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
    runError: "I cannot reach Nordly’s service right now. Please try again in a moment.",
    footer: "Created by Shirwac Abib · Personal Applied AI case study",
    fictionalWorld: "Fictional environment · synthetic data only",
    language: "Language",
    archiveTitle: "Nordly's document archive.",
    archiveLead:
      "These are the sources the assistant may use to answer – and the sources the system keeps outside AI.",
    archiveStatusEligible: "Used by AI",
    archiveStatusProtected: "Protected source",
    archiveStatusExcluded: "Inactive source",
    archiveOpen: "Open",
    archiveOpenAria: "Open document",
    archiveLoading: "Loading the document register directly from the backend…",
    archiveLoadError: "The document register could not be loaded from the backend.",
    archiveLoadErrorDetail: "No local or hard-coded fallback list is shown.",
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

const preferredExamples = ["SAFE", "NO_MATCH", "EMPLOYEE_COMPENSATION"] as const;

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
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  }).format(value);
}

function formatDocumentDate(value: string, locale: Locale) {
  const parsed = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(localeCode(locale), {
    year: "numeric",
    month: "long",
    day: "numeric",
    timeZone: "UTC",
  }).format(parsed);
}

function formatOrderTimestamp(value: string, locale: Locale) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return new Intl.DateTimeFormat(localeCode(locale), {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "Europe/Stockholm",
  }).format(parsed);
}

function formatDemoOrderReceipt(response: DemoOrderLookupResponse, locale: Locale) {
  const labels = copy[locale];
  const receipt = response.action_receipt;
  const readLabel = receipt.read_operations === 1
    ? labels.orderReadSingular
    : labels.orderReadPlural;
  const writeLabel = labels.orderWritePlural;

  return `${receipt.read_operations} ${readLabel} · ${receipt.ai_calls} ${labels.aiCalls} · ${receipt.write_operations} ${writeLabel} · ${labels.orderActionExecuted}: ${receipt.action_executed ? labels.yes : labels.no}`;
}

function extractDemoOrderId(question: string) {
  return question.match(/\bNORD-\d{4}\b/i)?.[0].toUpperCase() ?? null;
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

const visibleFlowPhases: readonly {
  phaseId: KnowledgeRagPhaseId;
  labelKey:
    | "flowSafety"
    | "flowEmbedding"
    | "flowSearch"
    | "flowContext"
    | "flowGeneration"
    | "flowVerification";
  technologyKey:
    | "flowSafetyTech"
    | "flowEmbeddingTech"
    | "flowSearchTech"
    | "flowContextTech"
    | "flowGenerationTech"
    | "flowVerificationTech";
}[] = [
  { phaseId: "safety", labelKey: "flowSafety", technologyKey: "flowSafetyTech" },
  { phaseId: "query_embedding", labelKey: "flowEmbedding", technologyKey: "flowEmbeddingTech" },
  { phaseId: "vector_search", labelKey: "flowSearch", technologyKey: "flowSearchTech" },
  { phaseId: "bounded_context", labelKey: "flowContext", technologyKey: "flowContextTech" },
  { phaseId: "generation", labelKey: "flowGeneration", technologyKey: "flowGenerationTech" },
  { phaseId: "java_verification", labelKey: "flowVerification", technologyKey: "flowVerificationTech" },
];

function exampleTypeLabel(example: DemoWorldRagExample, locale: Locale) {
  const labels = copy[locale];
  if (example.expected_boundary === "blocked_before_provider") {
    return labels.exampleBlocked;
  }
  if (example.expected_boundary === "insufficient_evidence") {
    return labels.exampleNoMatch;
  }
  return labels.exampleGrounded;
}

function safetyReasonLabel(reason: KnowledgeSafetyReason, locale: Locale) {
  return safetyGateCategories[locale].find((category) => category.reasons.includes(reason))?.label
    ?? copy[locale].notReported;
}

function KnowledgeFlow({
  result,
  locale,
}: {
  result: KnowledgeRagResponse;
  locale: Locale;
}) {
  const labels = copy[locale];
  const phaseById = new Map(result.phases.map((phase) => [phase.id, phase]));

  return (
    <div className="knowledge-flow" aria-label={labels.flowLabel}>
      <div className="knowledge-flow__heading">
        <span>{labels.flowLabel}</span>
        <small>{labels.noHiddenThoughts}</small>
      </div>
      <ol>
        {visibleFlowPhases.map(({ phaseId, labelKey, technologyKey }) => {
          const phase = phaseById.get(phaseId);
          const status = phase?.executed ? phase.status : "skipped";
          return (
            <li key={phaseId} data-status={status}>
              <span aria-hidden="true">
                {status === "completed" ? "✓" : status === "blocked" || status === "failed" ? "!" : "–"}
              </span>
              <div>
                <strong>{labels[labelKey]}</strong>
                <small>{labels[technologyKey]}</small>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

function KnowledgeCounters({
  result,
  locale,
}: {
  result: KnowledgeRagResponse;
  locale: Locale;
}) {
  const labels = copy[locale];
  return (
    <dl className="knowledge-counters" aria-label={labels.backendReceipt}>
      <div>
        <dt>{labels.counterEmbedding}</dt>
        <dd>{result.receipt.embedding_calls}</dd>
      </div>
      <div>
        <dt>{labels.counterSearch}</dt>
        <dd>{result.retrieval.current_vector_search ? 1 : 0}</dd>
      </div>
      <div>
        <dt>{labels.counterGeneration}</dt>
        <dd>{result.receipt.generation_calls}</dd>
      </div>
      <div>
        <dt>{labels.counterActions}</dt>
        <dd>{result.receipt.action_executed ? 1 : 0}</dd>
      </div>
    </dl>
  );
}

function KnowledgeBackstage({
  result,
  locale,
  featuredMatch,
  documentAvailable,
  documentError,
  onOpenDocument,
}: {
  result: KnowledgeRagResponse;
  locale: Locale;
  featuredMatch: KnowledgeRankedMatch | null;
  documentAvailable: boolean;
  documentError: boolean;
  onOpenDocument: (match: KnowledgeRankedMatch, trigger: HTMLButtonElement) => void;
}) {
  const labels = copy[locale];
  const isBlocked = result.outcome === "refused" && result.safety.decision === "BLOCK";
  const isAnswered = result.outcome === "answered" && result.answer !== null;
  const isNoMatch = result.outcome === "insufficient_evidence";

  return (
    <section
      id="knowledge-backstage"
      className={`knowledge-outcome knowledge-outcome--${
        isBlocked ? "blocked" : isNoMatch ? "abstained" : isAnswered ? "answered" : "error"
      }`}
      aria-labelledby="knowledge-backstage-title"
    >
      <KnowledgeFlow result={result} locale={locale} />
      <div className="knowledge-outcome__main">
        <div className="knowledge-outcome__answer">
          <p>{labels.behindKicker}</p>
          <h3 id="knowledge-backstage-title">{labels.behindTitle}</h3>
          <span className="knowledge-outcome__summary">{labels.behindIntro}</span>

          {isBlocked ? (
            <div className="knowledge-boundary-note">
              <strong>{labels.blockedExplain}</strong>
              <span>
                {labels.safetyReason}: {safetyReasonLabel(result.safety.reason_code, locale)} ·{" "}
                {labels.backendCode}: {result.safety.reason_code}
              </span>
            </div>
          ) : null}

          {isNoMatch ? (
            <div className="knowledge-boundary-note">
              <strong>{labels.allowedNoMatchExplain}</strong>
              <span>
                {result.retrieval.ranked_matches.length} {labels.results} ·{" "}
                {labels.counterGeneration} {result.receipt.generation_calls}
              </span>
            </div>
          ) : null}
        </div>

        {isAnswered && featuredMatch ? (
          <article className="knowledge-source-preview">
            <header>
              <span>{labels.retrievedPassage}</span>
              <strong aria-label={`${labels.sourceSimilarity}: ${formatSimilarity(featuredMatch.similarity, locale)}`}>
                {formatSimilarity(featuredMatch.similarity, locale)}
              </strong>
            </header>
            <small className="knowledge-source-preview__similarity-note">
              {labels.sourceSimilarity}: {labels.similarityNote}
            </small>
            <p>{featuredMatch.title}</p>
            <h4>{featuredMatch.section_heading}</h4>
            <blockquote>{featuredMatch.text}</blockquote>
            <button
              type="button"
              disabled={!documentAvailable}
              aria-label={`${labels.openDocument}: ${featuredMatch.title}`}
              onClick={(event) => onOpenDocument(featuredMatch, event.currentTarget)}
            >
              {documentError ? labels.unavailableDocument : labels.openDocument}
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m9 6 6 6-6 6" />
              </svg>
            </button>
          </article>
        ) : (
          <div className="knowledge-boundary-visual" aria-hidden="true">
            <span>{isBlocked ? "0" : result.retrieval.ranked_matches.length}</span>
            <strong>
              {isBlocked
                ? locale === "sv" ? "anrop lämnade grinden" : "calls left the gate"
                : locale === "sv" ? "godkända källpassager" : "approved source passages"}
            </strong>
          </div>
        )}
      </div>

      <KnowledgeCounters result={result} locale={locale} />
      <div className="knowledge-outcome__footer">
        <span>
          {result.truth_label} · {formatNumber(result.receipt.total_latency_ms, locale)} ms
        </span>
      </div>
    </section>
  );
}

function documentGroup(document: KnowledgeDocumentLibraryDocument): DocumentGroup {
  if (document.rag_eligibility.eligible) return "eligible";
  if (document.lifecycle.toUpperCase() === "APPROVED") return "protected";
  return "excluded";
}

function DocumentArchive({
  locale,
  library,
  error,
  onOpen,
}: {
  locale: Locale;
  library: KnowledgeDocumentLibraryResponse | null;
  error: boolean;
  onOpen: (document: KnowledgeDocumentLibraryDocument, trigger: HTMLButtonElement) => void;
}) {
  const labels = copy[locale];

  return (
    <div className="documents-scene">
      <section className="documents-page" aria-labelledby="documents-page-title">
        <header className="documents-page__intro">
          <h1 id="documents-page-title">{labels.archiveTitle}</h1>
          <p>{labels.archiveLead}</p>
        </header>

        <div className="document-library" aria-live="polite">
          {library ? (
            <ul className="document-library__list">
              {library.documents.map((document) => {
                const title = locale === "sv" ? document.title_sv : document.title;
                const summary = locale === "sv" ? document.summary_sv : document.summary_en;
                const group = documentGroup(document);
                const canOpen = group === "eligible" && document.content_visible;
                const status = group === "eligible"
                  ? labels.archiveStatusEligible
                  : group === "protected"
                    ? labels.archiveStatusProtected
                    : labels.archiveStatusExcluded;

                return (
                  <li key={document.id}>
                    <article className="document-library__row" data-status={group}>
                      <span className="document-library__icon" aria-hidden="true">
                        <svg viewBox="0 0 24 24">
                          <path d="M6.5 3.5h7l4 4v13h-11zM13.5 3.5v4h4M9 12h6M9 15.5h6" />
                        </svg>
                      </span>
                      <div className="document-library__copy">
                        <h2>{title}</h2>
                        <p>{summary}</p>
                      </div>
                      <span className="document-library__status">{status}</span>
                      {canOpen ? (
                        <button
                          type="button"
                          aria-label={`${labels.archiveOpenAria}: ${title}`}
                          onClick={(event) => onOpen(document, event.currentTarget)}
                        >
                          {labels.archiveOpen}
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="m9 6 6 6-6 6" />
                          </svg>
                        </button>
                      ) : null}
                    </article>
                  </li>
                );
              })}
            </ul>
          ) : (
            <div className={`archive-loading${error ? " archive-loading--error" : ""}`} role="status">
              <span aria-hidden="true">{error ? "!" : "…"}</span>
              <div>
                <strong>{error ? labels.archiveLoadError : labels.archiveLoading}</strong>
                {error ? <p>{labels.archiveLoadErrorDetail}</p> : null}
              </div>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function sceneFromHash(hash: string): Scene {
  if (hash === "#incident-lab") return "drift";
  if (hash === "#documents") return "documents";
  return "help";
}

function KnowledgeDocumentSheet({
  locale,
  sourceDocument,
  highlightedChunkId,
  match,
  onClose,
}: {
  locale: Locale;
  sourceDocument: KnowledgeDocumentLibraryDocument;
  highlightedChunkId: string;
  match: KnowledgeRankedMatch | null;
  onClose: () => void;
}) {
  const labels = copy[locale];
  const panelRef = useRef<HTMLElement | null>(null);
  const closeRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    const priorOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab" || !panelRef.current) return;

      const focusable = Array.from(
        panelRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
        ),
      );
      if (focusable.length === 0) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = priorOverflow;
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [onClose]);

  const title = locale === "sv" ? sourceDocument.title_sv : sourceDocument.title;
  const summary = locale === "sv" ? sourceDocument.summary_sv : sourceDocument.summary_en;

  return (
    <div
      className="document-sheet-backdrop"
      onMouseDown={(event) => {
        if (event.currentTarget === event.target) onClose();
      }}
    >
      <aside
        className="document-sheet"
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="knowledge-document-title"
        aria-describedby="knowledge-document-summary"
      >
        <header className="document-sheet__bar">
          <div>
            <span>{labels.syntheticDocument}</span>
            <strong>{sourceDocument.display_filename}</strong>
          </div>
          <button ref={closeRef} type="button" onClick={onClose} aria-label={labels.closeDocument}>
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m7 7 10 10M17 7 7 17" />
            </svg>
          </button>
        </header>

        <article className="company-document">
          <p className="company-document__brand">NORDLY COMMERCE AB</p>
          <p className="company-document__type">{labels.documentOriginal}</p>
          <h2 id="knowledge-document-title">{title}</h2>
          <p id="knowledge-document-summary" className="company-document__summary">{summary}</p>

          <dl className="company-document__meta">
            <div>
              <dt>{labels.documentVersion}</dt>
              <dd>{sourceDocument.version}</dd>
            </div>
            <div>
              <dt>{labels.documentOwner}</dt>
              <dd>{sourceDocument.owner_team}</dd>
            </div>
            <div>
              <dt>{labels.documentEffective}</dt>
              <dd>
                <time dateTime={sourceDocument.effective_from}>
                  {formatDocumentDate(sourceDocument.effective_from, locale)}
                </time>
              </dd>
            </div>
            <div>
              <dt>{labels.documentAccess}</dt>
              <dd>{sourceDocument.access_scopes.join(" · ")}</dd>
            </div>
          </dl>

          <div className="company-document__sections">
            {sourceDocument.chunks.map((chunk, index) => {
              const isRetrieved = chunk.id === highlightedChunkId;
              return (
                <section key={chunk.id} data-retrieved={isRetrieved}>
                  <header>
                    <span>{String(index + 1).padStart(2, "0")}</span>
                    <h3>{chunk.section_heading}</h3>
                    {isRetrieved ? <strong>{labels.retrievedPassage}</strong> : null}
                  </header>
                  <p>{chunk.text ?? labels.documentUnavailableBody}</p>
                  {isRetrieved && match ? (
                    <footer>
                      <span>{labels.sourceSimilarity}: {formatSimilarity(match.similarity, locale)}</span>
                      <span>{labels.similarityNote}</span>
                      <span>{labels.documentHash}: {chunk.content_sha256 ?? labels.notReported}</span>
                    </footer>
                  ) : null}
                </section>
              );
            })}
          </div>
        </article>
      </aside>
    </div>
  );
}

function DemoOrderBackstage({
  locale,
  response,
  safety,
  errorCode,
}: {
  locale: Locale;
  response: DemoOrderLookupResponse | null;
  safety: KnowledgeRagResponse | null;
  errorCode: string | null;
}) {
  const labels = copy[locale];
  const safetyPhase = safety?.phases.find((phase) => phase.id === "safety");

  return (
    <section id="knowledge-backstage" className="order-backstage" aria-labelledby="order-backstage-title">
      <header>
        <p>{labels.orderBackstageKicker}</p>
        <h3 id="order-backstage-title">{labels.orderBackstageTitle}</h3>
        <span>{response ? labels.orderBackstageIntro : labels.orderBackstageErrorIntro}</span>
      </header>

      <ol className="order-backstage__flow" aria-label={labels.flowLabel}>
        <li>
          <span aria-hidden="true">✓</span>
          <div>
            <strong>{labels.orderSafetyStep}</strong>
            <small>{safetyPhase ? (locale === "sv" ? safetyPhase.summary_sv : safetyPhase.summary_en) : labels.completed}</small>
          </div>
        </li>
        <li data-status={response ? "completed" : "stopped"}>
          <span aria-hidden="true">{response ? "✓" : "–"}</span>
          <div>
            <strong>{labels.orderLookupStep}</strong>
            <small>{response ? response.order.order_id : errorCode ?? labels.notReported}</small>
          </div>
        </li>
        <li data-status={response ? "completed" : "stopped"}>
          <span aria-hidden="true">{response ? "✓" : "–"}</span>
          <div>
            <strong>{labels.orderResponseStep}</strong>
            <small>{response ? labels.completed : labels.notRun}</small>
          </div>
        </li>
      </ol>

      {response ? (
        <div className="order-backstage__proof">
          <dl aria-label={labels.orderReceipt}>
            <div>
              <dt>{labels.orderReceipt}</dt>
              <dd>{formatDemoOrderReceipt(response, locale)}</dd>
            </div>
            <div>
              <dt>{labels.orderSource}</dt>
              <dd>{response.order.source_ref}</dd>
            </div>
            <div>
              <dt>{labels.orderSnapshot}</dt>
              <dd>{formatOrderTimestamp(response.snapshot_at, locale)}</dd>
            </div>
          </dl>
          <p>{locale === "sv" ? response.truth_label : response.truth_label_en}</p>
        </div>
      ) : null}
    </section>
  );
}

function customerFollowUps(
  response: DemoCustomerChatTurnResponse,
  locale: Locale,
): string[] {
  const sv = locale === "sv";
  if (response.outcome === "confirmation_required") return [];
  if (response.outcome === "refused") {
    return [sv ? "Var är min order?" : "Where is my order?"];
  }
  switch (response.intent.name) {
    case "order_status":
      return sv
        ? ["Jag vill inte ha paketet längre", "Hur fungerar en retur?"]
        : ["I no longer want the parcel", "How does a return work?"];
    case "cancel_order":
    case "return_order":
    case "refund_order":
    case "change_delivery_address":
    case "purchase_item":
      return sv
        ? ["Hur fungerar en retur?", "Var är min order?"]
        : ["How does a return work?", "Where is my order?"];
    case "clarification_required":
      return sv
        ? ["Kontrollera min leverans", "Förklara returreglerna"]
        : ["Check my delivery", "Explain the return rules"];
    case "unsupported":
      return sv
        ? ["Var är min order?", "Hur fungerar återbetalning?"]
        : ["Where is my order?", "How do refunds work?"];
    default:
      return sv
        ? ["Var är min order?", "Jag vill avbryta den"]
        : ["Where is my order?", "I want to cancel it"];
  }
}

function CustomerChatBackstage({
  response,
  locale,
  panelId,
  documentLibrary,
  documentError,
  onOpenSource,
}: {
  response: DemoCustomerChatTurnResponse;
  locale: Locale;
  panelId: string;
  documentLibrary: KnowledgeDocumentLibraryResponse | null;
  documentError: boolean;
  onOpenSource: (source: DemoCustomerChatSource, trigger: HTMLButtonElement) => void;
}) {
  const labels = copy[locale];
  const isAuthorityStop = response.outcome === "outside_authority";
  const isDownstreamStop = response.outcome === "unavailable"
    && response.error?.code.startsWith("CUSTOMER_CHAT_DOWNSTREAM_");
  const ragSummary = isDownstreamStop
    ? labels.customerDownstreamStopped
    : response.rag.current_vector_search
      ? labels.customerRagUsed
      : response.outcome === "confirmation_required"
        ? labels.customerRagPending
        : response.outcome === "refused"
          ? labels.customerStoppedBeforeData
          : response.receipt.read_operations > 0
            ? labels.customerNoRag
            : labels.customerNoTools;

  return (
    <section className="customer-backstage" id={panelId} aria-labelledby={`${panelId}-title`}>
      <header className="customer-backstage__header">
        <div>
          <p>{labels.behindKicker}</p>
          <h3 id={`${panelId}-title`}>{labels.customerBehindTitle}</h3>
          <span>{labels.customerBehindIntro}</span>
        </div>
        <span className="customer-backstage__verified">
          {response.verification.business_action_executed ? "!" : "✓"}
          {labels.customerNoWrites}
        </span>
      </header>

      {isAuthorityStop ? (
        <p className="customer-backstage__boundary">{labels.customerActionStopped}</p>
      ) : null}

      {response.error ? (
        <p className="customer-backstage__boundary" data-tone="system-fallback">
          <strong>{locale === "sv" ? response.error.summary_sv : response.error.summary_en}</strong>
          <small>{response.error.code}</small>
        </p>
      ) : null}

      <div className="customer-backstage__grid">
        <section className="customer-backstage__steps" aria-labelledby={`${panelId}-steps`}>
          <h4 id={`${panelId}-steps`}>{labels.customerSteps}</h4>
          <ol>
            {response.tool_events.map((event) => {
              const stopped = event.status === "blocked" || event.status === "failed";
              return (
                <li key={`${event.sequence}-${event.name}`} data-status={event.status}>
                  <span aria-hidden="true">{event.executed ? stopped ? "!" : "✓" : "–"}</span>
                  <div>
                    <strong>{locale === "sv" ? event.summary_sv : event.summary_en}</strong>
                    <small>{event.name.replaceAll("_", " ")} · {event.status}</small>
                  </div>
                </li>
              );
            })}
          </ol>
        </section>

        <section className="customer-backstage__rag" aria-label="RAG">
          <p>{ragSummary}</p>
          {response.rag.requested ? (
            <dl>
              <div>
                <dt>Embedding</dt>
                <dd>
                  {response.rag.embedding_executed
                    ? `${response.rag.embedding_model_id ?? labels.notReported} · ${response.rag.embedding_dimensions ?? labels.notReported}D`
                    : labels.notRun}
                </dd>
              </div>
              <div>
                <dt>Vector DB</dt>
                <dd>
                  {response.rag.current_vector_search
                    ? `${response.rag.backend ?? "pgvector"} · ${response.rag.vector_match_count} ${labels.results}`
                    : labels.notRun}
                </dd>
              </div>
              <div>
                <dt>{labels.verification}</dt>
                <dd>{response.rag.verification_outcome}</dd>
              </div>
            </dl>
          ) : null}
        </section>
      </div>

      {response.sources.length > 0 ? (
        <section className="customer-backstage__sources" aria-labelledby={`${panelId}-sources`}>
          <h4 id={`${panelId}-sources`}>{labels.customerSources}</h4>
          <ul>
            {response.sources.map((source) => {
              const canOpen = Boolean(
                source.document_id
                  && documentLibrary?.documents.some(
                    (document) => document.id === source.document_id
                      && document.rag_eligibility.eligible
                      && document.content_visible,
                  ),
              );
              return (
                <li key={source.evidence_id}>
                  <div>
                    <strong>{source.title}</strong>
                    <p>{locale === "sv" ? source.display_summary_sv : source.display_summary_en}</p>
                    <small>{source.source_ref}</small>
                  </div>
                  {source.document_id ? (
                    <button
                      type="button"
                      disabled={!canOpen}
                      onClick={(event) => onOpenSource(source, event.currentTarget)}
                    >
                      {canOpen && !documentError
                        ? labels.customerOpenSource
                        : labels.customerSourceUnavailable}
                    </button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </section>
      ) : null}

      <dl className="customer-backstage__receipt" aria-label={labels.customerReceipt}>
        <div>
          <dt>{labels.customerReadOperations}</dt>
          <dd>{response.receipt.read_operations}</dd>
        </div>
        <div>
          <dt>{labels.customerProviderCalls}</dt>
          <dd>{response.receipt.provider_calls}</dd>
        </div>
        <div>
          <dt>{labels.customerVectorSearches}</dt>
          <dd>{response.receipt.vector_searches}</dd>
        </div>
        <div>
          <dt>{labels.customerBusinessWrites}</dt>
          <dd>{response.receipt.business_write_operations}</dd>
        </div>
        <div>
          <dt>{labels.customerLatency}</dt>
          <dd>{formatNumber(response.receipt.total_latency_ms, locale)} ms</dd>
        </div>
      </dl>
    </section>
  );
}

function App() {
  const [locale, setLocale] = useState<Locale>("sv");
  const [scene, setScene] = useState<Scene>(() => sceneFromHash(window.location.hash));
  const [world, setWorld] = useState<DemoWorldResponse | null>(null);
  const [worldError, setWorldError] = useState(false);
  const [documentLibrary, setDocumentLibrary] =
    useState<KnowledgeDocumentLibraryResponse | null>(null);
  const [documentError, setDocumentError] = useState(false);
  const [selectedDocument, setSelectedDocument] =
    useState<SelectedDocument | null>(null);
  const [question, setQuestion] = useState("");
  const [chatTurns, setChatTurns] = useState<CustomerChatTurn[]>(loadCustomerChatSession);
  const [pendingTurnId, setPendingTurnId] = useState<string | null>(null);
  const [pendingMode, setPendingMode] = useState<"message" | "live">("message");
  const [expandedTurnId, setExpandedTurnId] = useState<string | null>(null);
  const chatThreadRef = useRef<HTMLDivElement | null>(null);
  const questionInputRef = useRef<HTMLInputElement | null>(null);
  const activeChatRequestRef = useRef<AbortController | null>(null);
  const documentTriggerRef = useRef<HTMLButtonElement | null>(null);
  const nextChatTurnId = useRef(0);
  const labels = copy[locale];
  const salaryExample = world?.rag_examples.find(
    (example) => example.category === "EMPLOYEE_COMPENSATION",
  ) ?? null;
  const isSubmitting = pendingTurnId !== null;
  const customerExamples = [
    {
      id: "order-status",
      type: labels.orderExampleType,
      prompt: labels.orderExample,
    },
    {
      id: "cancel-order",
      type: labels.cancelExampleType,
      prompt: labels.cancelExample,
    },
    {
      id: "refund-policy",
      type: labels.refundExampleType,
      prompt: labels.refundExample,
    },
    ...(salaryExample
      ? [{
          id: salaryExample.id,
          type: labels.exampleBlocked,
          prompt: locale === "sv" ? salaryExample.prompt_sv : salaryExample.prompt_en,
        }]
      : []),
  ];

  useEffect(() => {
    const controller = new AbortController();

    Promise.allSettled([
      getDemoWorld(controller.signal),
      getKnowledgeDocuments(controller.signal),
    ]).then(([worldResult, documentResult]) => {
      if (controller.signal.aborted) return;
      if (worldResult.status === "fulfilled") {
        setWorld(worldResult.value);
        setWorldError(false);
      } else {
        setWorldError(true);
      }
      if (documentResult.status === "fulfilled") {
        setDocumentLibrary(documentResult.value);
        setDocumentError(false);
      } else {
        setDocumentError(true);
      }
    });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  useEffect(() => {
    const syncSceneFromHash = () => {
      setScene(sceneFromHash(window.location.hash));
    };

    window.addEventListener("hashchange", syncSceneFromHash);
    return () => window.removeEventListener("hashchange", syncSceneFromHash);
  }, []);

  useEffect(() => {
    saveCustomerChatSession(chatTurns);
  }, [chatTurns]);

  useEffect(() => {
    const thread = chatThreadRef.current;
    if (!thread || chatTurns.length === 0) return;
    const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (typeof thread.scrollTo === "function") {
      thread.scrollTo({
        top: thread.scrollHeight,
        behavior: reducedMotion ? "auto" : "smooth",
      });
    } else {
      thread.scrollTop = thread.scrollHeight;
    }
  }, [chatTurns, pendingTurnId]);

  useEffect(() => () => activeChatRequestRef.current?.abort(), []);

  useEffect(() => {
    if (!expandedTurnId) return;
    const reducedMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    document
      .getElementById(`${expandedTurnId}-backstage`)
      ?.scrollIntoView?.({ behavior: reducedMotion ? "auto" : "smooth", block: "nearest" });
  }, [expandedTurnId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion || isSubmitting) return;

    nextChatTurnId.current += 1;
    const clientId = `customer-turn-${Date.now()}-${nextChatTurnId.current}`;
    const turn: CustomerChatTurn = {
      clientId,
      question: trimmedQuestion,
      locale,
      response: null,
      errorCode: null,
    };

    setChatTurns((current) => [...current, turn]);
    setQuestion("");
    setSelectedDocument(null);
    setExpandedTurnId(null);
    setPendingMode("live");
    setPendingTurnId(clientId);
    const controller = new AbortController();
    activeChatRequestRef.current = controller;

    try {
      const response = await runDemoCustomerChatTurn({
        message: trimmedQuestion,
        locale,
        confirm_live_ai: true,
        recent_conversation: buildCustomerChatConversation(chatTurns),
      }, controller.signal);

      setChatTurns((current) => current.map((candidate) =>
        candidate.clientId === clientId
          ? {
              ...candidate,
              question: response.submitted_message.text,
              response,
              errorCode: null,
            }
          : candidate,
      ));
    } catch (error) {
      if (controller.signal.aborted) return;
      const errorCode = error instanceof IncidentApiError
        ? error.code ?? `HTTP_${error.status}`
        : "CUSTOMER_CHAT_UNAVAILABLE";
      setChatTurns((current) => current.map((candidate) =>
        candidate.clientId === clientId
          ? { ...candidate, errorCode }
          : candidate,
      ));
    } finally {
      if (activeChatRequestRef.current === controller) {
        activeChatRequestRef.current = null;
        setPendingTurnId(null);
      }
    }
  }

  async function startLiveRun(turn: CustomerChatTurn) {
    if (isSubmitting || turn.response?.outcome !== "confirmation_required") return;

    setSelectedDocument(null);
    setExpandedTurnId(null);
    setPendingMode("live");
    setPendingTurnId(turn.clientId);
    setChatTurns((current) => current.map((candidate) =>
      candidate.clientId === turn.clientId
        ? { ...candidate, errorCode: null }
        : candidate,
    ));
    const controller = new AbortController();
    activeChatRequestRef.current = controller;

    try {
      const response = await runDemoCustomerChatTurn({
        message: turn.question,
        locale: turn.locale,
        confirm_live_ai: true,
        recent_conversation: buildCustomerChatConversation(
          chatTurns.filter((candidate) => candidate.clientId !== turn.clientId),
        ),
      }, controller.signal);
      setChatTurns((current) => current.map((candidate) =>
        candidate.clientId === turn.clientId
          ? {
              ...candidate,
              question: response.submitted_message.text,
              response,
              errorCode: null,
            }
          : candidate,
      ));
    } catch (error) {
      if (controller.signal.aborted) return;
      const errorCode = error instanceof IncidentApiError
        ? error.code ?? `HTTP_${error.status}`
        : "CUSTOMER_CHAT_UNAVAILABLE";
      setChatTurns((current) => current.map((candidate) =>
        candidate.clientId === turn.clientId
          ? { ...candidate, errorCode }
          : candidate,
      ));
    } finally {
      if (activeChatRequestRef.current === controller) {
        activeChatRequestRef.current = null;
        setPendingTurnId(null);
      }
    }
  }

  function resetCustomerChat() {
    const activeRequest = activeChatRequestRef.current;
    activeChatRequestRef.current = null;
    activeRequest?.abort();
    clearCustomerChatSession();
    setChatTurns([]);
    setQuestion("");
    setPendingTurnId(null);
    setExpandedTurnId(null);
    setSelectedDocument(null);
    window.requestAnimationFrame(() => questionInputRef.current?.focus());
  }

  function chooseCustomerFollowUp(prompt: string) {
    setQuestion(prompt);
    window.requestAnimationFrame(() => questionInputRef.current?.focus());
  }

  const openSourceDocument = selectedDocument
    ? documentLibrary?.documents.find(
        (candidate) => candidate.id === selectedDocument.documentId,
      ) ?? null
    : null;
  const openSourceMatch: KnowledgeRankedMatch | null = null;

  function openCustomerSource(
    source: DemoCustomerChatSource,
    trigger: HTMLButtonElement,
  ) {
    if (!source.document_id) return;
    const exactDocument = documentLibrary?.documents.some(
      (candidate) => candidate.id === source.document_id,
    );
    if (!exactDocument) return;
    documentTriggerRef.current = trigger;
    setSelectedDocument({
      documentId: source.document_id,
      chunkId: source.chunk_id ?? "",
    });
  }

  function openArchiveDocument(
    document: KnowledgeDocumentLibraryDocument,
    trigger: HTMLButtonElement,
  ) {
    if (!document.rag_eligibility.eligible || !document.content_visible) return;
    documentTriggerRef.current = trigger;
    setSelectedDocument({ documentId: document.id, chunkId: "" });
  }

  function closeDocument() {
    setSelectedDocument(null);
    window.requestAnimationFrame(() => documentTriggerRef.current?.focus());
  }

  function selectScene(nextScene: Scene) {
    setSelectedDocument(null);
    setScene(nextScene);
    const hashes: Record<Scene, string> = {
      help: "#customer-help",
      drift: "#incident-lab",
      documents: "#documents",
    };
    window.history.replaceState(
      null,
      "",
      hashes[nextScene],
    );
  }

  return (
    <div className="app-shell" id="top">
      <a className="skip-link" href="#main-content">
        {locale === "sv" ? "Hoppa till innehållet" : "Skip to content"}
      </a>
      <header className="site-header">
        <a
          className="creator-mark"
          href="#top"
          aria-label={`${labels.creatorName}, ${labels.creatorRole}`}
        >
          <span className="creator-mark__name">{labels.creatorName}</span>
          <span className="creator-mark__role">{labels.creatorRole}</span>
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
            <span className="scene-nav__hint">{labels.navStatus}</span>
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
          <a
            className={`scene-nav__item${scene === "documents" ? " scene-nav__item--active" : ""}`}
            href="#documents"
            aria-current={scene === "documents" ? "page" : undefined}
            onClick={(event) => {
              event.preventDefault();
              selectScene("documents");
            }}
          >
            {labels.documents}
            <span className="scene-nav__hint">{labels.documentsStatus}</span>
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
          <section className="hero knowledge-hero" aria-labelledby="page-title">
            <p className="hero__eyebrow">{labels.eyebrow}</p>
            <h1 id="page-title">{labels.title}</h1>
            <p className="hero__lead">{labels.lead}</p>
            <p className="hero__stack">{labels.stack}</p>
          </section>

          <section className="product-stage knowledge-stage" aria-labelledby="scene-title">
            <div className="product-stage__glow" aria-hidden="true" />
            <div className="product-card knowledge-portal">
              <header className="product-card__header knowledge-portal__header">
                <div className="assistant-identity">
                  <span className="assistant-mark" aria-hidden="true">N</span>
                  <div>
                    <p>{labels.assistantLabel}</p>
                    <h2 id="scene-title">{labels.sceneTitle}</h2>
                  </div>
                </div>
                <div className="customer-chat-header-actions">
                  {chatTurns.length > 0 ? (
                    <button
                      className="customer-chat-reset"
                      type="button"
                      onClick={resetCustomerChat}
                    >
                      {labels.customerNewConversation}
                    </button>
                  ) : null}
                  <span className="read-only-badge">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M7.5 10V7.8a4.5 4.5 0 0 1 9 0V10m-10 0h11a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 17.5 20h-11A1.5 1.5 0 0 1 5 18.5v-7A1.5 1.5 0 0 1 6.5 10Z" />
                    </svg>
                    {labels.readOnly}
                  </span>
                </div>
              </header>

              <div className="knowledge-portal__body conversation">
                <div className="message-row message-row--assistant">
                  <span className="message-avatar" aria-hidden="true">N</span>
                  <div className="message-bubble message-bubble--assistant">{labels.hello}</div>
                </div>

                {chatTurns.length > 0 ? (
                  <p className="customer-chat-session-note">
                    <span aria-hidden="true" />
                    {labels.customerLocalSession}
                  </p>
                ) : null}

                {chatTurns.length === 0 ? (
                  <div className="knowledge-start">
                    <div className="examples" aria-label={labels.exampleLead}>
                      <p>{labels.exampleLead}</p>
                      <div className="examples__list">
                        {customerExamples.map((example) => (
                          <button
                            key={example.id}
                            type="button"
                            aria-label={example.prompt}
                            onClick={() => setQuestion(example.prompt)}
                          >
                            <span className="sr-only">{example.type}: </span>
                            {example.prompt}
                          </button>
                        ))}
                      </div>
                      {worldError ? <p className="inline-note">{labels.loadError}</p> : null}
                    </div>
                  </div>
                ) : (
                  <div
                    className={`knowledge-run customer-chat-thread${expandedTurnId ? " is-backstage-open" : ""}`}
                    aria-label={labels.customerTranscript}
                    aria-busy={isSubmitting}
                    ref={chatThreadRef}
                  >
                    {chatTurns.map((turn, index) => {
                      const response = turn.response;
                      const isPending = pendingTurnId === turn.clientId;
                      const isExpanded = expandedTurnId === turn.clientId;
                      const isLastTurn = index === chatTurns.length - 1;
                      const panelId = `${turn.clientId}-backstage`;
                      return (
                        <article className="customer-chat-turn" key={turn.clientId}>
                          <div className="message-row message-row--user">
                            <div className="message-bubble message-bubble--user">{turn.question}</div>
                          </div>

                          {isPending ? (
                            <div className="message-row message-row--assistant">
                              <span className="message-avatar" aria-hidden="true">N</span>
                              <div
                                className="message-bubble message-bubble--assistant knowledge-chat-wait"
                                role="status"
                              >
                                <span className="knowledge-chat-wait__dots" aria-hidden="true">
                                  <span />
                                  <span />
                                  <span />
                                </span>
                                <span>{pendingMode === "live" ? labels.liveWaiting : labels.screeningWaiting}</span>
                              </div>
                            </div>
                          ) : null}

                          {response && !isPending ? (
                            <div
                              className="message-row message-row--assistant"
                              role="status"
                              aria-live="polite"
                              aria-atomic="true"
                            >
                              <span className="message-avatar" aria-hidden="true">N</span>
                              <article
                                className="message-bubble message-bubble--assistant knowledge-chat-answer customer-chat-answer"
                                aria-label={locale === "sv" ? "Svar från Nordly" : "Answer from Nordly"}
                              >
                                <p className="knowledge-chat-answer__lead">
                                  {locale === "sv"
                                    ? response.assistant_message.text_sv
                                    : response.assistant_message.text_en}
                                </p>

                                {response.order && response.intent.name === "order_status" ? (
                                  <dl className="customer-order-strip" aria-label={labels.customerOrder}>
                                    <div>
                                      <dt>{labels.customerOrder}</dt>
                                      <dd>{response.order.order_id}</dd>
                                    </div>
                                    <div>
                                      <dt>{labels.customerStatus}</dt>
                                      <dd>{locale === "sv" ? response.order.status_sv : response.order.status_en}</dd>
                                    </div>
                                    <div>
                                      <dt>{labels.customerDelivery}</dt>
                                      <dd>
                                        {formatDocumentDate(response.order.estimated_delivery_from, locale)}–{formatDocumentDate(response.order.estimated_delivery_through, locale)}
                                      </dd>
                                    </div>
                                  </dl>
                                ) : null}

                                {response.order && response.intent.name !== "order_status" ? (
                                  <small className="customer-turn-context">
                                    {labels.customerAppliesTo} {response.order.order_id} · {locale === "sv"
                                      ? response.order.status_sv
                                      : response.order.status_en}
                                  </small>
                                ) : null}

                                {response.outcome === "confirmation_required" ? (
                                  <>
                                    <button
                                      className="primary-action"
                                      type="button"
                                      onClick={() => startLiveRun(turn)}
                                    >
                                      <span>{labels.customerConfirmationCta}</span>
                                      <svg viewBox="0 0 24 24" aria-hidden="true">
                                        <path d="m9 6 6 6-6 6" />
                                      </svg>
                                    </button>
                                    <small className="action-note">{labels.customerConfirmationNote}</small>
                                  </>
                                ) : null}
                              </article>
                            </div>
                          ) : null}

                          {turn.errorCode && !isPending ? (
                            <div
                              className="message-row message-row--assistant"
                              role="status"
                              aria-live="polite"
                              aria-atomic="true"
                            >
                              <span className="message-avatar" aria-hidden="true">N</span>
                              <article
                                className="message-bubble message-bubble--assistant knowledge-chat-answer"
                                aria-label={locale === "sv" ? "Svar från Nordly" : "Answer from Nordly"}
                              >
                                <p className="knowledge-chat-answer__lead">{labels.runError}</p>
                                <small className="knowledge-chat-answer__truth">{turn.errorCode}</small>
                              </article>
                            </div>
                          ) : null}

                          {response && !isPending ? (
                            <>
                              <div className="agent-result-actions knowledge-result-actions customer-chat-actions">
                                <button
                                  className="xray-toggle"
                                  type="button"
                                  aria-expanded={isExpanded}
                                  aria-controls={panelId}
                                  onClick={() => setExpandedTurnId(isExpanded ? null : turn.clientId)}
                                >
                                  <span>{isExpanded ? labels.hideBehind : labels.showBehind}</span>
                                  <svg viewBox="0 0 24 24" aria-hidden="true">
                                    <path d={isExpanded ? "m6 14 6-6 6 6" : "m6 10 6 6 6-6"} />
                                  </svg>
                                </button>
                              </div>
                              {isExpanded ? (
                                <CustomerChatBackstage
                                  response={response}
                                  locale={locale}
                                  panelId={panelId}
                                  documentLibrary={documentLibrary}
                                  documentError={documentError}
                                  onOpenSource={openCustomerSource}
                                />
                              ) : null}

                              {isLastTurn && response ? (
                                <div className="customer-follow-ups" aria-label={labels.customerTryNext}>
                                  {customerFollowUps(response, locale)
                                    .filter((prompt) => !chatTurns.some(
                                      (candidate) => candidate.question.toLocaleLowerCase(locale)
                                        === prompt.toLocaleLowerCase(locale),
                                    ))
                                    .map((prompt) => (
                                    <button
                                      key={prompt}
                                      type="button"
                                      onClick={() => chooseCustomerFollowUp(prompt)}
                                    >
                                      {prompt}
                                    </button>
                                    ))}
                                </div>
                              ) : null}
                            </>
                          ) : null}
                        </article>
                      );
                    })}
                  </div>
                )}

                <form className="composer knowledge-composer" onSubmit={handleSubmit}>
                  <label className="sr-only" htmlFor="nordly-question">
                    {labels.placeholder}
                  </label>
                  <input
                    id="nordly-question"
                    ref={questionInputRef}
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
                <p className="input-boundary-note">{labels.inputBoundary}</p>
              </div>
            </div>
          </section>

        </div>

        <div hidden={scene !== "documents"}>
          <DocumentArchive
            locale={locale}
            library={documentLibrary}
            error={documentError}
            onOpen={openArchiveDocument}
          />
        </div>

        {selectedDocument && openSourceDocument ? (
          <KnowledgeDocumentSheet
            locale={locale}
            sourceDocument={openSourceDocument}
            highlightedChunkId={selectedDocument.chunkId}
            match={openSourceMatch}
            onClose={closeDocument}
          />
        ) : null}

        <IncidentLabPortal locale={locale} active={scene === "drift"} />
      </main>

      <footer className="site-footer">
        <span>{labels.footer}</span>
        <span>
          {world?.company.display_name ?? "Nordly Commerce AB"} · {labels.fictionalWorld}
        </span>
      </footer>
    </div>
  );
}

export default App;
