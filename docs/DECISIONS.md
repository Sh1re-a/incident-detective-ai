# Föreslagna tekniska beslut

- **Status:** föreslagen riktning inför Shirwacs granskning
- **Senast uppdaterad:** 24 augusti 2026

Förslagen nedan ska hålla Incident Detective litet, förklarbart och mätbart. De blir accepterade först efter Shirwacs granskning. Därefter ändras ett beslut bara när ny evidens eller ett verkligt blockerande problem motiverar det, och ändringen dokumenteras här i stället för att döljas i implementationen.

## DEC-001 – Syntetisk incident, verklig utredning

**Status:** Proposed

All incidentdata är syntetisk. Tool calling, modellbeteende, verifiering, evals, mätning och deployment ska däremot vara verkliga i slutprodukten. Live och replay har olika, synliga truth labels.

**Varför:** Projektet kan visa verklig engineering utan att röra kunddata eller låtsas vara ett produktionssystem.

**Konsekvens:** Realism måste skapas i fixtures och förklaras tydligt. Inga kund-, besparings- eller accuracyclaims får hittas på.

## DEC-002 – Monorepo och en deploybar container

**Status:** Proposed

Frontend, backend, scenariofixtures, verifierare och evalharness ligger i ett monorepo. Slutleveransen ska kunna köras som en container på Cloud Run.

**Varför:** Ett litet individuellt sprintprojekt behöver enkel lokal reproduktion och en tydlig deployväg.

**Konsekvens:** Ingen microservice-fleet, Kubernetes eller Terraform krävs för kärnan.

## DEC-003 – React/Vite och FastAPI/Pydantic

**Status:** Proposed

Story/Engineering View byggs med React, TypeScript och Vite. API, tool contracts och verifiering byggs med Python 3.12+, FastAPI och Pydantic v2.

**Varför:** Kombinationen ger ett snabbt gränssnitt, typade gränser och ett konkret Python/FastAPI-lärandemål.

**Konsekvens:** Delade begrepp måste kontraktstestas mellan TypeScript och Pydantic; de får inte utvecklas som två oberoende sanningar.

## DEC-004 – Fyra separata domänkontrakt

**Status:** Proposed

`Scenario`, `Evidence`, `Diagnosis` och `GroundTruth` hålls isär. `GroundTruth` är dolt för modellen och alla read-only tools.

**Varför:** Facitläckage skulle göra både demon och evalresultaten meningslösa.

**Konsekvens:** Scenario-API:t får aldrig serialisera evalfacit. Gränsen testas deterministiskt.

## DEC-005 – Typade read-only tools

**Status:** Proposed

Endast `get_metrics`, `search_logs`, `get_trace` och `retrieve_runbooks` exponeras för modellen. De returnerar strukturerad data med stabila evidence IDs och kan inte skriva eller genomföra remediation.

**Varför:** Modellen ska undersöka ett avgränsat system och lämna en rekommendation till en människa.

**Konsekvens:** Rollback, deploy, terminalåtkomst och externa driftintegrationer är CUT.

## DEC-006 – Explicit och begränsad state machine

**Status:** Proposed

Flödet är `COLLECT → SYNTHESIZE → VERIFY`, inte ett öppet agentramverk. Det har högst tre collection-rundor, åtta tool calls totalt, två per verktygstyp, tre parallella read-only-anrop, högst fyra modellanrop och 45 sekunders hard timeout. Slutlig synthesis får inte använda tools och följs endast av deterministisk verifiering.

**Varför:** Beteende, latency och kostnad ska vara möjliga att förstå och mäta.

**Konsekvens:** Systemet måste kunna avstå med `insufficient_evidence` i stället för att fortsätta leta utan gräns.

## DEC-007 – Responses API, strict output och en leverantör

**Status:** Proposed; exact SDK/model pending verification

AI-integrationen byggs med aktuell OpenAI Responses API, custom function tools och strict structured output. Endast en modellleverantör används under sprinten.

**Varför:** Meritvärdet ligger i arkitektur, evals och omdöme, inte i ett stort providerlager.

**Konsekvens:** Aktuell officiell dokumentation, API-åtkomst, billing, SDK-version och modell-ID måste verifieras innan implementation. Assistants API, Threads/Runs och avvecklade evalflöden används inte.

## DEC-008 – RAG endast för runbooks

**Status:** Proposed

PostgreSQL/pgvector används för 10–15 ostrukturerade runbooks. Metrics, logs och traces nås genom sina typade tools och läggs inte i vector store.

**Varför:** Retrieval löser ett verkligt textproblem utan att göra all telemetri otydlig eller svår att verifiera.

**Konsekvens:** Runbookresultat måste ha dokument-, chunk- och versionsmetadata och kunna mätas med Hit@4.

## DEC-009 – Tre olika verifieringsfrågor

**Status:** Proposed

Verifieraren mäter separat:

1. citation validity – finns varje citerat ID i evidensen modellen såg,
2. evidence support/precision – stöder evidensen påståendet och det definierade facitstödet,
3. diagnosis correctness – matchar `root_cause_code` det dolda facit i diagnosbara fall; i avsedda abstentionfall krävs `insufficient_evidence` och `root_cause_code = null`.

**Varför:** En korrekt rotorsak kan ha dåliga citat och ett giltigt citat kan ändå vara irrelevant.

**Konsekvens:** `affected_service` mäts separat och ett enda sammanslaget “correct”-värde räcker inte.

## DEC-010 – Story View och Engineering View, inte chain-of-thought

**Status:** Proposed

Story View prioriterar affärspåverkan, tidslinje, rotorsak, bevis och nästa steg. Engineering View visar säkra tool events, evidence IDs, schemas, versions- och körmetadata samt evals. Privat chain-of-thought sparas eller visas inte.

**Varför:** Två målgrupper behöver olika detaljnivå, men ingen behöver modellens privata resonemang.

## DEC-011 – Portabel evalharness

**Status:** Proposed

Evals ska kunna köras med lokala/CI-kommandon över versionshanterade fixtures och deterministiska scorers.

**Varför:** Utvärderingen ska överleva leverantörsförändringar och vara reproducerbar för en teknisk granskare.

**Konsekvens:** Ingen avvecklad provider-hostad Evals API-funktion får vara kritisk väg.

## DEC-012 – JSON-loggar och OpenTelemetry först

**Status:** Proposed

Systemet använder strukturerade JSON-loggar och OpenTelemetry för API-, tool- och verifieringsspår. Langfuse läggs bara till om ett konkret gap finns efter kärnflödet.

**Varför:** Observability ska förklara beteende utan att bli ett eget projekt.

**Konsekvens:** Hemligheter, rå promptdata med känsligt innehåll och privat chain-of-thought loggas inte.

## DEC-013 – Cloud Run med server-side secrets

**Status:** Proposed; external deploy requires later approval

Slutcontainern ska deployas till Cloud Run. Hemligheter finns endast på serversidan. Manuell, verifierad deploy kommer före eventuell GitHub Actions/OIDC-automatisering.

**Varför:** Projektet ska bevisa verklig driftsättning utan att CI/CD blir sprintens första problem.

**Konsekvens:** Ingen deploy görs i grundfasen. Extern deploy och publik åtkomst kräver uttryckligt klartecken.

## DEC-014 – Privat repo och ingen licens före ägarskapsbesked

**Status:** Proposed

`Sh1re-a/incident-detective-ai` skapas privat. Ingen open-source-licens läggs till och repot görs inte publikt innan Julia/addendumfrågan är bekräftad.

**Varför:** Privat lagring är godkänd, men publik äganderätt och villkor är ännu inte verifierade.

**Konsekvens:** En publik release är planerad men får inte antas vara godkänd.
