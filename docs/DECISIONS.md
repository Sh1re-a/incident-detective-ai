# Tekniska beslut

- **Status:** levande beslutslogg; varje beslut har egen status
- **Senast uppdaterad:** 26 augusti 2026

Besluten ska hålla Incident Detective litet, förklarbart och mätbart. En föreslagen riktning blir accepterad när den är granskad eller implementerad. Därefter ändras den bara när ny evidens eller ett verkligt blockerande problem motiverar det, och ändringen dokumenteras här i stället för att döljas i implementationen.

## DEC-001 – Syntetisk incident, verklig utredning

**Status:** Accepted, 25 augusti 2026

All incidentdata är syntetisk. Tool calling, modellbeteende, verifiering, evals, mätning och deployment ska däremot vara verkliga i slutprodukten. Live och replay har olika, synliga truth labels.

**Varför:** Projektet kan visa verklig engineering utan att röra kunddata eller låtsas vara ett produktionssystem.

**Konsekvens:** Realism måste skapas i fixtures och förklaras tydligt. Inga kund-, besparings- eller accuracyclaims får hittas på.

## DEC-002 – Monorepo och en deploybar container

**Status:** Accepted, 25 augusti 2026

Frontend, backend, scenariofixtures, verifierare och evalharness ligger i ett monorepo. Slutleveransen ska kunna köras som en container på Cloud Run.

**Varför:** Ett litet individuellt sprintprojekt behöver enkel lokal reproduktion och en tydlig deployväg.

**Konsekvens:** Ingen microservice-fleet, Kubernetes eller Terraform krävs för kärnan.

## DEC-003 – React/Vite och Java/Spring Boot

**Status:** Accepted, 25 augusti 2026

Story/Engineering View byggs med React, TypeScript och Vite. API, tool contracts och verifiering byggs med Java 21, Spring Boot, Spring MVC, Jakarta Validation och Jackson.

**Varför:** Shirwac kan redan Java och vill använda projektet för att bli bättre på Spring Boot samtidigt som AI-systemet byggs. Den officiella Gemini-SDK:n fungerar i Java, så ett extra backend-runtime behövs inte i sprintens kärna.

**Konsekvens:** Delade begrepp måste kontraktstestas mellan TypeScript och Java; de får inte utvecklas som två oberoende sanningar. Spring Boot 4 använder Jackson 3 och den sealed `Evidence`-hierarkin round-trip-testas. Python ingår inte i kärnarkitekturen.

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

**Konsekvens:** Systemet måste kunna avstå med `insufficient_evidence` i stället för att fortsätta leta utan gräns.

## DEC-007 – En leverantör, function calling och structured output

**Status:** Accepted for the current slice, updated 26 augusti 2026

Det aktuella liveflödet använder Gemini Developer API genom den officiella Java SDK:n, pinnad till `google-genai` 1.67.0. Standardprofilen är `gemini-3.1-flash-lite` med `MINIMAL` thinking och kontraktet `gemini-live-v6`. Endast en modellleverantör används i sprintens kärna.

**Varför:** Meritvärdet ligger i arkitektur, evals och omdöme, inte i leverantörens namn. Gratis lokal utveckling minskar startkostnaden utan att låtsas att den publika demon blir kostnadsfri.

**Konsekvens:** Modellanrop isoleras bakom en liten intern gateway, men inget multi-provider-lager eller modellval byggs i gränssnittet. `COLLECT` använder custom function tools, `SYNTHESIZE` görs separat utan tools med ett strikt schema och `VERIFY` är deterministisk Java-kod. v6 behåller v5:s direkta evidenskrav och filtrerar tool-deklarationerna efter återstående serverbudget. Två v6-försök med `gemini-3.5-flash-lite` nådde timeout; två efterföljande RAG-smokes med standardprofilen slutfördes korrekt på 6 057 respektive 5 505 ms. Det är ett motiverat utvecklingsval, inte ett stabilitets- eller accuracybevis; evalsen får avgöra om profilen behålls.

## DEC-008 – RAG endast för runbooks

**Status:** Accepted and implemented for retrieval v1, updated 26 augusti 2026

PostgreSQL/pgvector används för en fristående korpus med 10 ostrukturerade runbooks och 12 chunks. Metrics, logs och traces nås genom sina typade tools och läggs inte i vector store. Korpusen bäddas in med `gemini-embedding-2` i 768 dimensioner och söks med exakt cosine distance; ett approximate index behövs inte för denna lilla datamängd.

**Varför:** Retrieval löser ett verkligt textproblem utan att göra all telemetri otydlig eller svår att verifiera.

**Konsekvens:** Import är ett explicit och idempotent kommando; vanlig uppstart gör inga embedding-anrop. RAG-profilen vägrar retrieval om antal eller innehållshash inte matchar aktuell korpus och faller aldrig tyst tillbaka till keyword matching. Runbookresultat visar dokument-, chunk- och versionsmetadata samt rank, similarity, embeddingmodell, innehållshash, korpusversion och retrieval-backend. Tröskeln kalibreras endast på development. Retrieval v1 gav 5/5 development och 4/5 held-out Hit@4, medan tre no-match-fall gav 3/3. Den missade held-out-frågan och unsafe top-1 behålls som öppet kvalitetsproblem.

## DEC-009 – Fyra separata verifieringsdimensioner

**Status:** Accepted, updated 26 augusti 2026

Verifieraren mäter separat:

1. citation validity – finns varje citerat ID i evidensen modellen såg,
2. evidence support/precision – stöder evidensen påståendet och det definierade facitstödet,
3. claim coverage – hur många unika förväntade `(claim_code, claim_value_code)` som svaret faktiskt innehåller,
4. diagnosis correctness – matchar `root_cause_code` det dolda facit i diagnosbara fall; i avsedda abstentionfall krävs `insufficient_evidence` och `root_cause_code = null`.

**Varför:** En korrekt rotorsak kan ha dåliga citat, ett giltigt citat kan vara irrelevant och ett kort svar kan annars få 100 procent precision genom att utelämna viktiga fakta.

**Konsekvens:** Publikt claim-coverage-resultat visar bara antal och score, aldrig vilka facitclaims som saknas. Låg coverage är ett kvalitetsmått, inte ett hårt kontraktsfel. `affected_service` mäts separat och ett enda sammanslaget “correct”-värde räcker inte.

## DEC-010 – Story View och Engineering View, inte chain-of-thought

**Status:** Accepted, 25 augusti 2026

Story View prioriterar affärspåverkan, tidslinje, rotorsak, bevis och nästa steg. Engineering View visar säkra tool events, evidence IDs, schemas, versions- och körmetadata. Privat chain-of-thought sparas eller visas inte. Recorded replay får spela upp en tydligt märkt färdig verktygssekvens; liveflödet visar inga påhittade tool events medan requesten pågår.

**Varför:** Två målgrupper behöver olika detaljnivå, men ingen behöver modellens privata resonemang.

## DEC-011 – Portabel evalharness

**Status:** Accepted; retrieval slice implemented, full harness pending, updated 26 augusti 2026

Evals ska kunna köras med lokala/CI-kommandon över versionshanterade fixtures och deterministiska scorers.

**Varför:** Utvärderingen ska överleva leverantörsförändringar och vara reproducerbar för en teknisk granskare.

**Konsekvens:** Ingen avvecklad provider-hostad Evals API-funktion får vara kritisk väg. Retrieval v1 har ett explicit lokalt kommando som ger JSON- och Markdownrapport med development/held-out, tröskel, rank, similarity, corpus/dataset-hash, embeddingprofil, nullable provider-usage, latency och git SHA. Vanlig CI kör deterministiska scorers utan betalda provideranrop; den explicita retrieval-evalen gör riktiga embeddings. Den fulla diagnos-evalen ska även identifiera modellprofil, prompt, diagnosschema och git SHA. När dataset, prompt, schema, korpus, chunkning, embeddingkonfiguration eller scorer ändras behandlas tidigare resultat som historiska tills relevant evalsvit har körts igen.

## DEC-012 – JSON-loggar och OpenTelemetry först

**Status:** Accepted design, not implemented, updated 26 augusti 2026

Systemet ska använda strukturerade JSON-loggar och OpenTelemetry för API-, tool- och verifieringsspår. Detta är ännu inte implementerat. Langfuse läggs bara till om ett konkret gap finns efter kärnflödet.

**Varför:** Observability ska förklara beteende utan att bli ett eget projekt.

**Konsekvens:** Telemetrin ska använda en uttrycklig allowlist. API-nycklar, råa prompts, providerresponser, full evidenstext, tool arguments, `GroundTruth` och privat chain-of-thought får inte loggas. Trace-strukturen ska minst kunna följas som API → collect → tool → synthesize → verify, och test ska visa att saneringen gäller. Observability får inte beskrivas som implementerad innan både spans och sanering har verifierats.

## DEC-013 – Cloud Run med server-side secrets

**Status:** Proposed; external deploy requires later approval

Slutcontainern ska deployas till Cloud Run. Hemligheter finns endast på serversidan. Manuell, verifierad deploy kommer före eventuell GitHub Actions/OIDC-automatisering.

**Varför:** Projektet ska bevisa verklig driftsättning utan att CI/CD blir sprintens första problem.

**Konsekvens:** Ingen deploy görs i grundfasen. Extern deploy och publik åtkomst kräver uttryckligt klartecken.

## DEC-014 – Publikt portfolio-repo utan licens

**Status:** Accepted, 25 augusti 2026

Projektgrunden publiceras i [Sh1re-a/incident-detective-ai](https://github.com/Sh1re-a/incident-detective-ai). Ingen open-source-licens läggs till och ingen deploy görs som del av publiceringen.

**Varför:** Projektet är ett icke-kommersiellt portfolio- och utbildningsprojekt som ska kunna visas på GitHub och senare användas som ett sanningsenligt arbetsprov.

**Konsekvens:** Projektgrunden och dess Git-historik blir offentligt läsbara. Live-demo, mätresultat och deployment är fortfarande separata, ännu inte genomförda steg.

## DEC-015 – Recorded replay stoppar trasiga fixtures

**Status:** Accepted, 25 augusti 2026

Recorded replay använder versionshanterad, betrodd demodata. Hela fixturepaketet valideras därför när applikationen startar. Ett schemafel, ett saknat evidence ID eller ett citat till evidens som inte returnerats ska stoppa uppstarten i stället för att bli en normal replay-körning.

**Varför:** Replay-läget ska vara en stabil fallback och en reproducerbar referens, inte simulera felbeteenden som bara kan uppstå när en modell genererar ett nytt svar.

**Konsekvens:** Replay-API:t returnerar bara `completed` för ett startbart fixturepaket. Live-runnern har ett separat kontrakt och kan returnera `verification_failed` när ett nytt modellsvar bryter verifieringsreglerna.

## DEC-016 – Swagger måste matcha det verkliga API:t

**Status:** Accepted, 25 augusti 2026

Det lokala Spring Boot-API:t dokumenteras med springdoc OpenAPI och Swagger UI. OpenAPI-schemat använder samma `snake_case` som verkliga JSON-svar, beskriver evidence-varianterna med explicita wire-värden och visar nu både `recorded_replay` och den uttryckligen bekräftade `live_ai`-endpointen.

**Varför:** Swagger ska hjälpa mig och en teknisk granskare att förstå och prova det API som faktiskt finns. Ett schema med andra fältnamn, dolt facit eller planerade funktioner skulle ge falsk trygghet.

**Konsekvens:** Kontraktstestet kontrollerar live/replay-svar, schemafält, evidence-discriminator och att interna fixturevägar, API-nyckel och GroundTruth-typer saknas. Swagger är tillgänglig lokalt; om den ska vara publik eller avstängd i Cloud Run beslutas separat före deployment.

## DEC-017 – Kostnad visas som uppskattat betalt listpris

**Status:** Accepted, 25 augusti 2026

Tokenanvändningen kommer från leverantörens verkliga responsmetadata. `estimated_cost_usd` beräknas modellberoende från Googles betalda standardlistpris som kontrollerades 25 augusti 2026. Svaret innehåller även `estimated_cost_basis` och säger att faktisk free-tier-debitering kan vara 0 USD.

**Varför:** Applikationen kan inte säkert avgöra kontots billingnivå från ett modellsvar. Ett omärkt listpris skulle därför se ut som en faktisk debitering.

**Konsekvens:** Okända modell-ID:n får `null` som kostnadsestimat i stället för ett påhittat pris. Prislistan måste omverifieras före publicerade kostnadsjämförelser.

## DEC-018 – Replay först och begränsade live-starter

**Status:** Accepted, 25 augusti 2026

Recorded replay är det kostnadsfria standardläget. Varje livekörning kräver ett aktivt val i gränssnittet och en explicit bekräftelse i requesten. Backend tillåter högst en pågående liveutredning och fem starter per rullande tio minuter per applikationsinstans. När gränsen nås returneras ett sanerat `429`-svar med `Retry-After`; replay påverkas inte och klienten gör inga automatiska live-retries.

**Varför:** En publik portfolio-demo ska kunna provas utan att en besökstopp, dubbla klick eller automatiska retries skapar okontrollerad modellkostnad. Replay gör samtidigt kärnberättelsen tillgänglig även när livekapaciteten är upptagen.

**Konsekvens:** Gränsen är lokal för varje process och är därför inte ett komplett publikt missbruksskydd. En framtida Cloud Run-deploy ska hålla `min-instances=0`, begränsa `max-instances` och kompletteras med budgetlarm innan den kallas kostnadssäkrad. Exakta molngränser verifieras vid deployment; de är inte genomförda nu.
