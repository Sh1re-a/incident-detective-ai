# Tekniska beslut

- **Status:** levande beslutslogg; varje beslut har egen status
- **Senast uppdaterad:** 10 september 2026

Besluten ska hålla Incident Detective litet, förklarbart och mätbart. En föreslagen riktning blir accepterad när den är granskad eller implementerad. Därefter ändras den bara när ny evidens eller ett verkligt blockerande problem motiverar det, och ändringen dokumenteras här i stället för att döljas i implementationen.

## DEC-001 – Syntetisk incident, verklig utredning

**Status:** Accepted, 25 augusti 2026

All incidentdata är syntetisk. Tool calling, modellbeteende, verifiering, evals, mätning och deployment ska däremot vara verkliga i slutprodukten. Live och replay har olika, synliga truth labels.

**Varför:** Projektet kan visa verklig engineering utan att röra kunddata eller låtsas vara ett produktionssystem.

**Konsekvens:** Realism måste skapas i fixtures och förklaras tydligt. Inga kund-, besparings- eller accuracyclaims får hittas på.

## DEC-002 – Monorepo och en deploybar container

**Status:** Accepted, 25 augusti 2026

Frontend, backend, scenariofixtures, verifierare och små opt-in-testspår ligger i ett monorepo. Slutleveransen ska kunna köras som en container på Cloud Run.

**Varför:** Ett litet individuellt demoprojekt behöver enkel lokal reproduktion och en tydlig deployväg.

**Konsekvens:** Ingen microservice-fleet, Kubernetes eller Terraform krävs för kärnan.

## DEC-003 – React/Vite och Java/Spring Boot

**Status:** Accepted, 25 augusti 2026

Det mänskliga portalgränssnittet byggs med React, TypeScript och Vite. API, tool contracts, ADK-integration och verifiering byggs med Java 21, Spring Boot, Spring MVC, Jakarta Validation och Jackson.

**Varför:** Shirwac kan redan Java och vill använda projektet för att bli bättre på Spring Boot samtidigt som AI-systemet byggs. Den officiella Gemini-SDK:n fungerar i Java, så ett extra backend-runtime behövs inte i kärnan.

**Konsekvens:** Delade begrepp måste kontraktstestas mellan TypeScript och Java; de får inte utvecklas som två oberoende sanningar. Spring Boot 4 använder Jackson 3 och den sealed `Evidence`-hierarkin round-trip-testas. Google ADK används genom Java-SDK:n. Python ingår inte i kärnarkitekturen och läggs bara till om en senare, konkret deploymentförmåga kräver det.

## DEC-004 – Fyra separata domänkontrakt

**Status:** Accepted, 25 augusti 2026

`Scenario`, `Evidence`, `Diagnosis` och `GroundTruth` hålls isär. `GroundTruth` är dolt för modellen och alla read-only tools.

**Varför:** Facitläckage skulle göra både demon och evalresultaten meningslösa.

**Konsekvens:** Scenario-API:t får aldrig serialisera evalfacit. Gränsen testas deterministiskt.

## DEC-005 – Typade read-only tools

**Status:** Accepted, 25 augusti 2026

Endast `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks` exponeras för modellen. De returnerar strukturerad data med stabila evidence IDs och kan inte skriva eller genomföra remediation.

**Varför:** Modellen ska undersöka ett avgränsat system och lämna en rekommendation till en människa.

**Konsekvens:** Rollback, deploy, terminalåtkomst och externa driftintegrationer är CUT.

## DEC-006 – Explicit och begränsad state machine

**Status:** Accepted, 25 augusti 2026

Flödet är `COLLECT → SYNTHESIZE → VERIFY`, inte ett öppet agentramverk. Den globala hard capen är åtta tool calls, men striktare per-tool-gränser ger i nuläget högst sex: ett metrics-anrop, två loggsökningar, två traces och en runbookhämtning. Varje collection-runda får den återstående budgeten och endast fortfarande tillåtna tools exponeras. Högst två collection-rundor, tre tool calls per runda, tre modellanrop och 45 sekunders hard timeout tillåts. Första collection kan få upp till 28 sekunder, en andra collection högst 8 sekunder och synthesis får återstående reserverad tid. Slutlig synthesis använder inga tools och följs endast av deterministisk verifiering.

**Varför:** Beteende, latency och kostnad ska vara möjliga att förstå och mäta.

**Konsekvens:** Systemet måste kunna avstå med `insufficient_evidence` i stället för att fortsätta leta utan gräns. Detta beslut beskriver det ursprungliga liveflödet. Den separata ADK-endpointens striktare tvåagentsflöde och tvåanropsgräns dokumenteras i DEC-019.

## DEC-007 – En leverantör, function calling och structured output

**Status:** Accepted for the current slice, updated 10 september 2026

Det aktuella liveflödet använder Googles officiella Java SDK, pinnad till
`google-genai` 1.67.0. Gemini Developer API är lokal standard, medan samma
klientgräns kan välja Vertex AI med ADC, project och location. Standardprofilen
är `gemini-3.1-flash-lite` med `MINIMAL` thinking och kontraktet
`gemini-live-v6`. Endast Google Gen AI används som modellleverantör i kärnan.

**Varför:** Meritvärdet ligger i arkitektur, evals och omdöme, inte i leverantörens namn. Gratis lokal utveckling minskar startkostnaden utan att låtsas att den publika demon blir kostnadsfri.

**Konsekvens:** Modell- och embeddinganrop går genom samma providerfabrik, men
inget generellt multi-provider-lager eller modellval byggs i gränssnittet.
Providerkonstruktionen är testad utan nätverksanrop; Vertex-reachability är inte
verifierad. `COLLECT` använder custom function tools, `SYNTHESIZE` görs separat
utan tools med ett strikt schema och `VERIFY` är deterministisk Java-kod. v6
behåller v5:s direkta evidenskrav och filtrerar tool-deklarationerna efter
återstående serverbudget. Två v6-försök med `gemini-3.5-flash-lite` nådde
timeout; två efterföljande RAG-smokes med standardprofilen slutfördes korrekt på
6 057 respektive 5 505 ms. Det är ett motiverat utvecklingsval, inte ett
stabilitets- eller accuracybevis; evalsen får avgöra om profilen behålls.

## DEC-008 – RAG endast för runbooks

**Status:** Accepted and implemented for retrieval v1, updated 26 augusti 2026

PostgreSQL/pgvector används för en fristående korpus med 10 ostrukturerade runbooks och 12 chunks. Metrics, logs och traces nås genom sina typade tools och läggs inte i vector store. Korpusen bäddas in med `gemini-embedding-2` i 768 dimensioner och söks med exakt cosine distance; ett approximate index behövs inte för denna lilla datamängd.

**Varför:** Retrieval löser ett verkligt textproblem utan att göra all telemetri otydlig eller svår att verifiera.

**Konsekvens:** Import är ett explicit och idempotent kommando; vanlig uppstart gör inga embedding-anrop. RAG-profilen vägrar retrieval om antal eller innehållshash inte matchar aktuell korpus och faller aldrig tyst tillbaka till keyword matching. Runbookresultat visar dokument-, chunk- och versionsmetadata samt rank, similarity, embeddingmodell, innehållshash, korpusversion och retrieval-backend. Tröskeln kalibreras endast på development. Retrieval v1 gav 5/5 development och 4/5 held-out Hit@4, medan tre no-match-fall gav 3/3. Den missade held-out-frågan och unsafe top-1 behålls som öppet kvalitetsproblem.

`capabilities-v4` rapporterar samma readinesskontroll som aktuell backendstatus:
korpusversion samt indexed/current/expected chunks. Embeddingprofilens identitet
inkluderar även providertransport, så Developer API-vektorer inte kan behandlas
som aktuella Vertex-vektorer. Readiness bevisar att indexet är redo, men aldrig
att en enskild AI-körning använde RAG; det senare kräver ett faktiskt
`retrieve_runbooks`-event.

## DEC-009 – Fyra separata verifieringsdimensioner

**Status:** Accepted, updated 26 augusti 2026

Verifieraren mäter separat:

1. citation validity – finns varje citerat ID i evidensen modellen såg,
2. evidence support/precision – stöder evidensen påståendet och det definierade facitstödet,
3. claim coverage – hur många unika förväntade `(claim_code, claim_value_code)` som svaret faktiskt innehåller,
4. diagnosis correctness – matchar `root_cause_code` det dolda facit i diagnosbara fall; i avsedda abstentionfall krävs `insufficient_evidence` och `root_cause_code = null`.

**Varför:** En korrekt rotorsak kan ha dåliga citat, ett giltigt citat kan vara irrelevant och ett kort svar kan annars få 100 procent precision genom att utelämna viktiga fakta.

**Konsekvens:** Publikt claim-coverage-resultat visar bara antal och score, aldrig vilka facitclaims som saknas. Låg coverage är ett kvalitetsmått, inte ett hårt kontraktsfel. `affected_service` mäts separat och ett enda sammanslaget “correct”-värde räcker inte.

## DEC-010 – Människosvar och post-run-kvitto, inte chain-of-thought

**Status:** Accepted, updated 10 september 2026

Portalens huvudlager prioriterar affärspåverkan, rotorsak, bevis och ett säkert nästa steg. `Så byggdes svaret` visar endast backendregistrerade agentnamn, tool events, evidence IDs, versionsdata, workflow-kvitto och Java-verifiering efter avslutad request. Privat chain-of-thought sparas eller visas inte. Recorded replay får spela upp en tydligt märkt färdig sekvens; liveflödet visar inga påhittade events medan requesten pågår.

**Varför:** Två målgrupper behöver olika detaljnivå, men ingen behöver modellens privata resonemang.

## DEC-011 – Små evalspår utanför runtime

**Status:** Accepted and simplified, 27 augusti 2026

Varje replay/live-resultat graderas av den deterministiska verifieraren. En
separat opt-in `RunbookRetrievalEvalIT` kör riktig Gemini embedding och pgvector
över en versionshanterad retrievalsvit.

**Varför:** Projektet ska visa evaldisciplin utan att webbappen blir ett eget
benchmarkramverk.

**Konsekvens:** Ingen evalrunner, baseline, datasetgenerator eller report engine
paketeras i runtime-JAR:en. Vanliga tester är providerfria. RAG-eval och live
smoke kräver uttryckligt opt-in. Den frysta retrievalrapporten är historisk när
korpus, embeddingprofil, tröskel eller scorer ändras.

## DEC-012 – JSON-loggar och OpenTelemetry först

**Status:** Accepted and partially implemented, updated 1 september 2026

Liveflödet använder nu manuella OpenTelemetry-spans för `investigation → collect → tool/retrieval → synthesize → verify`. När tracing är aktiverat kan de ligga under Spring MVC:s inkommande API-span. Micrometer är fortsatt applikationens metrics-API, medan Spring Boots OTel-bridge används för tracing. OTLP span-export finns som runtime-capability men både SDK och span-export är uttryckligen avstängda lokalt som standard; OTLP-export för metrics och logs är också avstängd. OTel-exportern använder JDK:s HTTP-sändare så att Google GenAI kan behålla sin sammanhållna OkHttp 4/Okio-stack utan dubbla `okhttp3`-klasser. Strukturerade JSON-loggar är ännu inte implementerade. Langfuse läggs bara till om ett konkret gap finns efter kärnflödet.

**Varför:** Observability ska förklara beteende utan att bli ett eget projekt.

**Konsekvens:** De manuella domänspannen använder en uttrycklig attribut-allowlist utan generic attribute-API. API-nycklar, råa prompts, providerresponser, full evidenstext, tool arguments, summaries, `GroundTruth` och privat chain-of-thought kan inte skickas in till denna instrumentering. Domänspannen registrerar inte exception messages eller stack traces; fel reduceras till en begränsad `error.type`. Ett in-process OpenTelemetry-test verifierar spanparenting, allowlisten och frånvaron av hemliga teststrängar. Den nuvarande claimen är därför en testad live-span-slice — inte strukturerade JSON-loggar, exporterad end-to-end-observability, replay tracing eller en färdig collector/dashboard.

Spring Boot rekommenderar Micrometer Observation/Tracing som applikations-API och stödjer OTel/OTLP genom Actuator. Den manuella instrumenteringen är därför begränsad till domänspans som ramverket inte kan förstå självt; vanliga HTTP- och databasspans lämnas till ramverks-/agentinstrumentering. GenAI-semantic-convention-fält används bara för säkra metadata som operation, modellnamn, toolnamn och tokenantal — aldrig för input/output-messages, tool arguments eller retrieval query text.

## DEC-013 – Cloud Run med server-side secrets

**Status:** Proposed, updated 10 september 2026; external deploy requires later approval

Slutcontainern ska deployas till Cloud Run. Hemligheter finns endast på serversidan. Manuell, verifierad deploy kommer före eventuell GitHub Actions/OIDC-automatisering.

**Varför:** Projektet ska bevisa verklig driftsättning utan att CI/CD blir det första problemet.

**Konsekvens:** Den kombinerade frontend-/backendcontainern verifieras lokalt först. Den aktuella Phase 3A-revisionen deployas inte i denna uppgift. En eventuell äldre publik Cloud Run-revision är separat evidens och får inte användas som bevis för att aktuell kod är live. Cloud-resurser, Vertex AI, ny revision och publik trafik kräver separata uttryckliga beslut.

## DEC-014 – Publikt portfolio-repo utan licens

**Status:** Accepted, 25 augusti 2026

Projektgrunden publiceras i [Sh1re-a/incident-detective-ai](https://github.com/Sh1re-a/incident-detective-ai). Ingen open-source-licens läggs till och ingen deploy görs som del av publiceringen.

**Varför:** Projektet är ett icke-kommersiellt portfolio- och utbildningsprojekt som ska kunna visas på GitHub och senare användas som ett sanningsenligt arbetsprov.

**Konsekvens:** Projektgrunden och dess Git-historik blir offentligt läsbara. Live-demo, mätresultat, en äldre deployment och den aktuella arbetsrevisionens deployment är separata evidenslägen. Ingen revision får kallas live utan att dess exakta Git-SHA och runtime har verifierats.

## DEC-015 – Recorded replay stoppar trasiga fixtures

**Status:** Accepted, 25 augusti 2026

Recorded replay använder versionshanterad, betrodd demodata. Hela fixturepaketet valideras därför när applikationen startar. Ett schemafel, ett saknat evidence ID eller ett citat till evidens som inte returnerats ska stoppa uppstarten i stället för att bli en normal replay-körning.

**Varför:** Replay-läget ska vara en stabil fallback och en reproducerbar referens, inte simulera felbeteenden som bara kan uppstå när en modell genererar ett nytt svar.

**Konsekvens:** Replay-API:t returnerar bara `completed` för ett startbart fixturepaket. Live-runnern har ett separat kontrakt och kan returnera `verification_failed` när ett nytt modellsvar bryter verifieringsreglerna.

## DEC-016 – Swagger måste matcha det verkliga API:t

**Status:** Accepted, 25 augusti 2026

Det lokala Spring Boot-API:t dokumenteras med springdoc OpenAPI och Swagger UI. OpenAPI-schemat använder samma `snake_case` som verkliga JSON-svar, beskriver evidence-varianterna med explicita wire-värden och omfattar replay, live-RAG, genererade incidenter och den uttryckligen bekräftade ADK-endpointen `/api/v1/agent/turns`.

**Varför:** Swagger ska hjälpa mig och en teknisk granskare att förstå och prova det API som faktiskt finns. Ett schema med andra fältnamn, dolt facit eller planerade funktioner skulle ge falsk trygghet.

**Konsekvens:** Kontraktstestet kontrollerar live/replay-svar, schemafält, evidence-discriminator och att interna fixturevägar, API-nyckel och GroundTruth-typer saknas. Swagger är tillgänglig lokalt; om den ska vara publik eller avstängd i Cloud Run beslutas separat före deployment.

## DEC-017 – Kostnad visas som uppskattat betalt listpris

**Status:** Accepted, 25 augusti 2026

Tokenanvändningen kommer från leverantörens verkliga responsmetadata. `estimated_cost_usd` beräknas modellberoende från Googles betalda standardlistpris som kontrollerades 31 augusti 2026. Svaret innehåller även `estimated_cost_basis` och säger att faktisk free-tier-debitering kan vara 0 USD.

**Varför:** Applikationen kan inte säkert avgöra kontots billingnivå från ett modellsvar. Ett omärkt listpris skulle därför se ut som en faktisk debitering.

**Konsekvens:** Okända modell-ID:n får `null` som kostnadsestimat i stället för ett påhittat pris. Prislistan måste omverifieras före publicerade kostnadsjämförelser.

## DEC-018 – Replay först och begränsade live-starter

**Status:** Accepted, 25 augusti 2026

Recorded replay är det kostnadsfria standardläget. Varje livekörning kräver ett aktivt val i gränssnittet och en explicit bekräftelse i requesten. Backend tillåter högst en pågående liveutredning och fem starter per rullande tio minuter per applikationsinstans. När gränsen nås returneras ett sanerat `429`-svar med `Retry-After`; replay påverkas inte och klienten gör inga automatiska live-retries.

**Varför:** En publik portfolio-demo ska kunna provas utan att en besökstopp, dubbla klick eller automatiska retries skapar okontrollerad modellkostnad. Replay gör samtidigt kärnberättelsen tillgänglig även när livekapaciteten är upptagen.

**Konsekvens:** Gränsen är lokal för varje process och är därför inte ett komplett publikt missbruksskydd. En framtida Cloud Run-deploy ska hålla `min-instances=0`, begränsa `max-instances` och kompletteras med budgetlarm innan den kallas kostnadssäkrad. Exakta molngränser verifieras vid deployment; de är inte genomförda nu.

## DEC-019 – Tvåagentsflöde i Google ADK for Java

**Status:** Accepted and implemented in the current working revision, 10 september 2026

ADK-endpointen använder ett `SequentialAgent` med exakt två barn i fast ordning. `nordly_evidence_agent` har ensam tillgång till ett sammansatt read-only tool och måste returnera en tool call utan diagnos eller berättande text. ADK:s strukturerade `FunctionResponse` lämnas direkt vidare i eventströmmen till `nordly_diagnosis_agent`, som saknar tools och endast får formulera en typad diagnoskandidat från den överlämnade evidensen. Båda barnen har `maxSteps = 1`, hela körningen har `maxLlmCalls = 2` och agent transfer till parent eller peer är avstängd.

**Varför:** Separationen visar ett verkligt multi-agentmönster utan att skapa fri agency. Evidensinsamling och formulering får olika behörigheter, samtidigt som ordning, kostnad och failure modes förblir begripliga.

**Konsekvens:** `nordly-adk-turn-v3` returnerar ett workflow-kvitto med förväntad och observerad agentordning, handofftyp, slutlig författare och completionstatus. En nullable `provider_route` finns bara när körningen registrerade minst ett modell- eller embeddinganrop. Deterministisk Java-kod är enda release gate och kontrollerar ordning, direkt evidensöverlämning, tool-gräns, slutlig författare, exakt två modellanrop, schema, citationer och faktastöd. Ett underkänt villkor ger `verification_failed` och diagnosen hålls inne. Ingen agent kan skriva, genomföra remediation eller visa privat chain-of-thought. En säkerhetsblockerad request stoppar före ADK, Gemini, tools och embeddings och returnerar noll i kontrollkvittot.

## DEC-020 – En versionsstyrd Nordly-företagskorpus med exkludering före embedding

**Status:** Accepted and implemented locally, 10 september 2026

Nordly Commerce AB använder en syntetisk företagskorpus med 16
dokumentposter och 30 chunks. Exakt 13 `APPROVED + public_demo`-dokument med
27 chunks får bäddas in. Ett godkänt men restricted löneregister, en deprecated
policy och en untrusted prompt-injection-fixture syns endast som metadata och
utesluts före embedding, rankning och modellkontext.

**Varför:** Demon ska visa både användbar företagskunskap och verklig
informationsstyrning. Ett filter efter modellen vore för sent och ett separat
dokumenthanteringssystem skulle vara onödig komplexitet i denna fas.

**Konsekvens:** JSON-manifestet är canonical runtime source. Dokumentbiblioteket
returnerar body och chunkhash endast för tillåtet material. RAG-kvitton binder
varje körning till deterministisk korpushash, indexsnapshot och hash per träff.
Den frysta v2-evalen innehåller svenska/engelska, held-out, multi-source,
no-match och input-gate-fall, men semantic retrieval-kvalitet för v2 står kvar
som `PENDING_PROVIDER_RUN` tills en separat betald körning godkänns.
